package systems.lupine.sheaf.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-platform notification channels that classify incoming push
 * messages. Distinct from the existing `sheaf_fronting` channel
 * (FrontNotificationHelper), which backs the local persistent
 * fronters notification — these three are for server-pushed events.
 *
 * Channel ids mirror the server's NotificationChannel.event_type
 * vocabulary so the messaging service can route by event_type without
 * a translation table.
 */
@Singleton
class PushNotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun register() {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_FRONT_CHANGE,
                    "Front change",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Front-change events from systems you watch"
                },
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Reminders triggered by your system or watched systems"
                },
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Account and security notices"
                },
            )
        )
    }

    companion object {
        const val CHANNEL_FRONT_CHANGE = "sheaf_front_change"
        const val CHANNEL_REMINDERS = "sheaf_reminders"
        const val CHANNEL_SYSTEM = "sheaf_system"
    }
}
