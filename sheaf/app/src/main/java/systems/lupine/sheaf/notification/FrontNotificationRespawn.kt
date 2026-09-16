package systems.lupine.sheaf.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.repository.PreferencesRepository
import java.util.concurrent.TimeUnit

/**
 * Bringing the fronting notification back after it is swiped away.
 *
 * Android 14 made ongoing notifications dismissable, so `setOngoing(true)` no
 * longer means what it says and there is no API that restores the old
 * behaviour. A foreground service would not fix it either - those are
 * dismissable too - and it would cost two permissions on a manifest that is
 * deliberately four lines long.
 *
 * So the notification comes back instead, and on a delay. An instant re-post
 * is the behaviour of an app fighting its user; five minutes reads as "this is
 * meant to stay", leaves a swipe working as a way to clear the shade now, and
 * costs nothing if the user turns the whole thing off in between (the work is
 * cancelled, and the worker re-checks the setting before posting anyway).
 */
object FrontNotificationRespawn {

    private const val WORK_NAME = "front_notification_respawn"
    private val DELAY_MINUTES = 5L

    /** Fired by the system when the user swipes the notification away. */
    fun deleteIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DismissedReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // Replace: a second dismissal restarts the clock rather than
            // queueing a second re-post behind the first.
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RespawnWorker>()
                .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

/**
 * Receives the dismissal and schedules the re-post. Does no work itself: the
 * broadcast has to return promptly, and five minutes later is not now.
 */
@AndroidEntryPoint
class DismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FrontNotificationRespawn.schedule(context.applicationContext)
    }
}

/**
 * Re-posts the notification, reading who is fronting from the local cache
 * rather than the network: this runs on a timer with no user waiting, and a
 * notification that is five minutes stale is corrected by the next refresh
 * anyway. Being offline must not turn a re-post into a failed request.
 */
@HiltWorker
class RespawnWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PreferencesRepository,
    private val cache: LocalCache,
    private val helper: FrontNotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Re-checked rather than assumed: the user may have turned the
        // notification, or the respawn itself, off in the intervening minutes.
        if (!prefs.frontNotification.first()) return Result.success()
        if (!prefs.frontNotificationRespawn.first()) return Result.success()
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val fronts = cache.getFronts() ?: return Result.success()
        val members = cache.getMembers() ?: return Result.success()
        val frontingIds = fronts.flatMap { it.memberIds }.toSet()
        val names = members.filter { it.id in frontingIds }.map { it.displayNameOrName }

        helper.post(
            names,
            FrontNotificationStyle(
                useLogo = prefs.frontNotificationLogo.first(),
                showNames = prefs.frontNotificationNames.first(),
                respawn = true,
            ),
        )
        return Result.success()
    }
}
