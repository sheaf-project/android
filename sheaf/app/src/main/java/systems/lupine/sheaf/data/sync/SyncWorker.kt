package systems.lupine.sheaf.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.PendingFrontRemoval
import systems.lupine.sheaf.data.db.PendingFrontSwitch
import systems.lupine.sheaf.data.db.PendingOperationsDao
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

        // Replay switches and removals interleaved in the order the user made
        // them (by createdAt), not all-removals-then-all-switches. A queue that
        // mixed a switch and a later removal would otherwise replay them out of
        // order and rebuild the wrong timeline. Each row carries its original
        // createdAt as the front's startedAt / endedAt so offline actions land
        // as real past-dated history rather than collapsing into "synced now".
        //
        // Each op is deleted on success, so a partial replay (network drops
        // mid-queue) is safe to retry from where it stopped. NOTE: createFront
        // is posted before its row is deleted, so an ambiguous response or a
        // mid-flight cancellation can still duplicate a front; a true
        // exactly-once guarantee needs a server-side Idempotency-Key on
        // createFront, tracked as a backend follow-up.
        val ops: List<QueuedOp> = buildList {
            dao.getAllRemovals().forEach { add(QueuedOp.Removal(it)) }
            dao.getAllSwitches().forEach { add(QueuedOp.Switch(it)) }
        }.sortedBy { it.createdAt }

        for (op in ops) {
            val retry = when (op) {
                is QueuedOp.Removal -> replayRemoval(dao, op.row)
                is QueuedOp.Switch -> replaySwitch(dao, op.row)
            }
            if (retry) return Result.retry()
        }
        return Result.success()
    }

    /** Returns true if the caller should [Result.retry] (transient failure). */
    private suspend fun replayRemoval(dao: PendingOperationsDao, removal: PendingFrontRemoval): Boolean {
        val removedAtIso = Instant.ofEpochMilli(removal.createdAt).toString()
        return runCatching {
            val fronts = api.getCurrentFronts()
            fronts.filter { removal.memberId in it.memberIds }.forEach { front ->
                val remaining = front.memberIds - removal.memberId
                if (remaining.isEmpty()) {
                    api.updateFront(front.id, FrontUpdate(endedAt = removedAtIso))
                } else {
                    api.updateFront(front.id, FrontUpdate(memberIds = remaining))
                }
            }
        }.fold(
            onSuccess = { dao.deleteRemoval(removal); false },
            onFailure = { e ->
                if (isPermanent(e)) {
                    // Front already gone / ended on another device: drop it so
                    // it can't wedge the queue behind an endless retry.
                    Log.w(WORK_NAME, "dropping un-replayable removal (${reason(e)})")
                    dao.deleteRemoval(removal); false
                } else true
            },
        )
    }

    /** Returns true if the caller should [Result.retry] (transient failure). */
    private suspend fun replaySwitch(dao: PendingOperationsDao, switch: PendingFrontSwitch): Boolean {
        val memberIds = switch.memberIds.split(",").filter { it.isNotBlank() }
        if (memberIds.isEmpty()) {
            // A switch with no members can never be created; drop it rather
            // than retrying a guaranteed 4xx forever.
            Log.w(WORK_NAME, "dropping queued switch with empty member set")
            dao.deleteSwitch(switch)
            return false
        }
        return runCatching {
            api.createFront(
                FrontCreate(
                    memberIds = memberIds,
                    startedAt = Instant.ofEpochMilli(switch.createdAt).toString(),
                    replaceFronts = switch.replaceFronts,
                    customStatus = switch.customStatus,
                )
            )
        }.fold(
            onSuccess = { dao.deleteSwitch(switch); false },
            onFailure = { e ->
                if (isPermanent(e)) {
                    // Un-replayable: a member was deleted server-side (404) or
                    // the payload is rejected (422). Drop so the rest isn't stuck.
                    Log.w(WORK_NAME, "dropping un-replayable switch (${reason(e)})")
                    dao.deleteSwitch(switch); false
                } else true
            },
        )
    }

    private sealed class QueuedOp(val createdAt: Long) {
        class Removal(val row: PendingFrontRemoval) : QueuedOp(row.createdAt)
        class Switch(val row: PendingFrontSwitch) : QueuedOp(row.createdAt)
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
