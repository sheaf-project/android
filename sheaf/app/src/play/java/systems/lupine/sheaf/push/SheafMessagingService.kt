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
import systems.lupine.sheaf.R
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            registrar.register(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = data["title"] ?: return
        val body = data["body"].orEmpty()
        val eventId = data["event_id"]

        // Stable per-event id keeps duplicate deliveries from stacking, but
        // distinct events get their own slot. Falls back to time-based id
        // when event_id is absent (shouldn't happen in production).
        val notificationId = eventId?.hashCode() ?: System.currentTimeMillis().toInt()

        // Backend currently only ships front-change events for mobile push.
        // Route reminders / system events here once the payload includes
        // an event_type field.
        val channelId = PushNotificationChannels.CHANNEL_FRONT_CHANGE

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+. Drop silently;
            // there's no useful action to take from a background service.
            Log.w(TAG, "Notification not posted: permission denied", e)
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
