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

        // Apply removals first (in order), then switches.
        // If any step fails due to a non-network error, discard to avoid getting stuck.
        val removals = dao.getAllRemovals()
        for (removal in removals) {
            runCatching {
                val fronts = api.getCurrentFronts()
                fronts.filter { removal.memberId in it.memberIds }.forEach { front ->
                    val remaining = front.memberIds - removal.memberId
                    if (remaining.isEmpty()) {
                        api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
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

        // Only apply the latest queued switch (stale intermediate switches are discarded).
        val switches = dao.getAllSwitches()
        if (switches.isNotEmpty()) {
            val latest = switches.last()
            runCatching {
                val memberIds = latest.memberIds.split(",")
                val currentFronts = api.getCurrentFronts()
                currentFronts.forEach { front ->
                    api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                }
                api.createFront(FrontCreate(memberIds = memberIds, startedAt = Instant.now().toString()))
            }.onSuccess {
                dao.deleteAllSwitches()
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
