package systems.lupine.sheaf.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
            }.onFailure {
                return Result.retry()
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
            runCatching {
                val memberIds = switch.memberIds.split(",")
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
            }.onFailure {
                return Result.retry()
            }
        }

        return Result.success()
    }

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
