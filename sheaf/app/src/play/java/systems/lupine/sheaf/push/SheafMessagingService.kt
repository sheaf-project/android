package systems.lupine.sheaf.push

import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.R
import systems.lupine.sheaf.datalayer.WatchFrontSync
import javax.inject.Inject

/**
 * Receives FCM token rotations and incoming push payloads. Token
 * rotations get forwarded to the backend via [PushDeviceRegistrar].
 * Payloads are pure-data with the server-rendered title/body inline
 * (see backend's send_to_token); we just turn them into Android
 * notifications on the appropriate channel.
 *
 * Android 13+ POST_NOTIFICATIONS permission is checked by
 * NotificationManagerCompat at notify-time; without it the call is a
 * silent no-op. The runtime prompt is surfaced from the UI when the
 * user takes an action that depends on push.
 */
@AndroidEntryPoint
class SheafMessagingService : FirebaseMessagingService() {

    @Inject lateinit var registrar: PushDeviceRegistrar
    @Inject lateinit var channels: PushNotificationChannels
    @Inject lateinit var channelSync: PushChannelSync

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            registrar.register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "onMessageReceived: data=${message.data} " +
                    "notification.title=${message.notification?.title} " +
                    "notification.body=${message.notification?.body} " +
                    "notificationsEnabled=${NotificationManagerCompat.from(this).areNotificationsEnabled()}",
            )
        }
        val data = message.data
        // Backend ships pure-data payloads with title/body inline. Fall
        // back to message.notification fields so Firebase Console "Send
        // test message" (which uses the legacy notification payload
        // shape) and any future notification-style traffic still render.
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val eventId = data["event_id"]

        // Stable per-event id keeps duplicate deliveries from stacking, but
        // distinct events get their own slot. Falls back to time-based id
        // when event_id is absent (shouldn't happen in production).
        val notificationId = eventId?.hashCode() ?: System.currentTimeMillis().toInt()

        // Route by the server's notification-channel id when present so
        // each Sheaf channel maps to its own Android NotificationChannel.
        // Fall back to event_type buckets and ultimately to the broad
        // front-change channel for older payloads that don't carry the
        // routing fields yet.
        val serverChannelId = data["channel_id"]?.takeIf { it.isNotBlank() }
        val eventType = data["event_type"]?.takeIf { it.isNotBlank() }
        val channelId = when {
            serverChannelId != null && channels.hasChannelFor(serverChannelId) ->
                channels.channelIdFor(serverChannelId)
            eventType == "reminders" -> PushNotificationChannels.CHANNEL_REMINDERS
            eventType == "system" -> PushNotificationChannels.CHANNEL_SYSTEM
            else -> PushNotificationChannels.CHANNEL_FRONT_CHANGE
        }
        // If we got a channel_id but don't have a matching Android channel
        // yet (redemption happened on another device, or sync hasn't run
        // since), kick off a lazy sync. The current notification still
        // posts on the fallback so the user sees it; subsequent ones
        // route correctly.
        if (serverChannelId != null && !channels.hasChannelFor(serverChannelId)) {
            scope.launch { channelSync.sync() }
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+. Drop silently;
            // there's no useful action to take from a background service.
            Log.w(TAG, "Notification not posted: permission denied", e)
        }

        // Nudge a paired watch to re-sync so its tiles and complications
        // don't sit stale until the wear app is next opened. Most pushes
        // imply front state moved; skip only the buckets that clearly
        // don't (reminders, system). An occasional redundant re-sync on
        // an unknown event type is cheaper than a missed front change.
        if (eventType != "reminders" && eventType != "system") {
            WatchFrontSync.notifyFrontChanged(this)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SheafMessagingService"
    }
}
