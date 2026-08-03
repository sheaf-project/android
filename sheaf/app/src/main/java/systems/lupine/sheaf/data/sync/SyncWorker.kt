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
import systems.lupine.sheaf.data.model.FrontReplace

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
        val ops = mergeQueuedOps(dao.getAllRemovals(), dao.getAllSwitches())

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
                    // Same replace-don't-edit rule as the online path, so a
                    // removal queued offline lands identically when it drains.
                    // startedAt carries the original removal time, keeping the
                    // history boundary where the user actually made the change.
                    api.replaceFront(
                        front.id,
                        FrontReplace(memberIds = remaining, startedAt = removedAtIso),
                    )
                }
            }
        }.fold(
            onSuccess = { dao.deleteRemoval(removal); false },
            onFailure = { e ->
                if (isPermanentHttpFailure(e)) {
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
                if (isPermanentHttpFailure(e)) {
                    // Un-replayable: a member was deleted server-side (404) or
                    // the payload is rejected (422). Drop so the rest isn't stuck.
                    Log.w(WORK_NAME, "dropping un-replayable switch (${reason(e)})")
                    dao.deleteSwitch(switch); false
                } else true
            },
        )
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
            // KEEP, not REPLACE: a drain that is already running must not be
            // cancelled by a fresh enqueue. createFront is posted before its row
            // is deleted (see doWork), so a REPLACE cancellation mid-drain is
            // exactly the window that can duplicate a front. With KEEP, an
            // enqueue while a run is in flight is dropped rather than cancelling
            // it; any rows added after that run snapshotted the queue are picked
            // up by the next schedule() (each offline action, plus reconnect,
            // calls this). Trading a little latency on the tail for not
            // double-creating fronts. True exactly-once still needs the backend
            // Idempotency-Key noted in doWork.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/**
 * One queued offline mutation, tagged with when the user actually made it.
 * Top-level (rather than nested in the worker) so the ordering and the
 * failure classification below can be unit-tested without a WorkManager.
 */
internal sealed class QueuedOp(val createdAt: Long) {
    class Removal(val row: PendingFrontRemoval) : QueuedOp(row.createdAt)
    class Switch(val row: PendingFrontSwitch) : QueuedOp(row.createdAt)
}

/**
 * Interleave removals and switches by the time the user made them, rather than
 * replaying all removals and then all switches, which rebuilds the wrong
 * timeline whenever the queue holds both.
 */
internal fun mergeQueuedOps(
    removals: List<PendingFrontRemoval>,
    switches: List<PendingFrontSwitch>,
): List<QueuedOp> = buildList {
    removals.forEach { add(QueuedOp.Removal(it)) }
    switches.forEach { add(QueuedOp.Switch(it)) }
}.sortedBy { it.createdAt }

/**
 * A failure is "permanent" when the server says the request itself is bad and
 * replaying it can't help: a 4xx other than auth (401/403, handled by the token
 * authenticator and recoverable on re-login), request timeout (408) and rate
 * limiting (429), which are all worth retrying. Network / IO errors and 5xx
 * aren't HttpExceptions, or are >= 500, so they retry.
 *
 * Getting this wrong is expensive in both directions: call a transient failure
 * permanent and the user's offline switch is silently dropped; call a permanent
 * one transient and it wedges the whole queue behind an endless retry.
 *
 * The wear app mirrors this in WearStore.isPermanentSwitchError.
 */
internal fun isPermanentHttpFailure(t: Throwable): Boolean {
    val code = (t as? HttpException)?.code() ?: return false
    return code in 400..499 && code !in setOf(401, 403, 408, 429)
}
