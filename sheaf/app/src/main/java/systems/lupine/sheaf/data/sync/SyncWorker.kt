package systems.lupine.sheaf.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.SheafDatabase
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontUpdate
import java.time.Instant

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: SheafApiService,
    private val db: SheafDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = db.pendingOperationsDao()

        // Replay removals oldest-first, each with its original createdAt as
        // endedAt so the timeline reflects when the user actually removed
        // the member rather than when the connection came back. (Partial
        // removals — front members shrinks but the front continues — can't
        // carry a timestamp; the API just takes the new memberIds list.)
        val removals = dao.getAllRemovals()
        for (removal in removals) {
            val removedAtIso = Instant.ofEpochMilli(removal.createdAt).toString()
            runCatching {
                val fronts = api.getCurrentFronts()
                fronts.filter { removal.memberId in it.memberIds }.forEach { front ->
                    val remaining = front.memberIds - removal.memberId
                    if (remaining.isEmpty()) {
                        api.updateFront(front.id, FrontUpdate(endedAt = removedAtIso))
                    } else {
                        api.updateFront(front.id, FrontUpdate(memberIds = remaining))
                    }
                }
            }.onSuccess {
                dao.deleteRemoval(removal)
            }.onFailure { e ->
                if (isPermanent(e)) {
                    // The target front is gone or the request is otherwise
                    // un-replayable (e.g. the front was already ended on
                    // another device). Drop the row so it can't wedge the
                    // queue behind an endless retry, and carry on.
                    Log.w(WORK_NAME, "dropping un-replayable removal (${reason(e)})")
                    dao.deleteRemoval(removal)
                } else {
                    return Result.retry()
                }
            }
        }

        // Replay every queued switch oldest-first with its original
        // createdAt as startedAt, so a string of offline switches lands as
        // a real history of past-dated fronts (the API accepts past
        // startedAt) rather than collapsing into a single "synced just
        // now" entry. Each row is deleted on success so a partial replay
        // (network drops mid-loop) is safe to retry.
        val switches = dao.getAllSwitches()
        for (switch in switches) {
            val memberIds = switch.memberIds.split(",").filter { it.isNotBlank() }
            if (memberIds.isEmpty()) {
                // A switch with no members can never be created; drop it
                // rather than retrying a guaranteed 4xx forever.
                Log.w(WORK_NAME, "dropping queued switch with empty member set")
                dao.deleteSwitch(switch)
                continue
            }
            runCatching {
                api.createFront(
                    FrontCreate(
                        memberIds = memberIds,
                        startedAt = Instant.ofEpochMilli(switch.createdAt).toString(),
                        replaceFronts = switch.replaceFronts,
                        customStatus = switch.customStatus,
                    )
                )
            }.onSuccess {
                dao.deleteSwitch(switch)
            }.onFailure { e ->
                if (isPermanent(e)) {
                    // Un-replayable: e.g. a member in this switch was deleted
                    // server-side (404), or the payload is rejected (422).
                    // Drop it so the rest of the queue isn't stuck behind it.
                    Log.w(WORK_NAME, "dropping un-replayable switch (${reason(e)})")
                    dao.deleteSwitch(switch)
                } else {
                    return Result.retry()
                }
            }
        }

        return Result.success()
    }

    // A failure is "permanent" when the server says the request itself is bad
    // and replaying it can't help: a 4xx other than auth (401/403, handled by
    // the token authenticator and recoverable on re-login), request timeout
    // (408) and rate limiting (429), which are worth retrying. Network / IO
    // errors and 5xx aren't HttpExceptions or are >=500, so they retry.
    private fun isPermanent(t: Throwable): Boolean {
        val code = (t as? HttpException)?.code() ?: return false
        return code in 400..499 && code !in setOf(401, 403, 408, 429)
    }

    private fun reason(t: Throwable): String =
        (t as? HttpException)?.let { "HTTP ${it.code()}" } ?: (t::class.simpleName ?: "error")

    companion object {
        const val WORK_NAME = "sync_pending_fronts"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
