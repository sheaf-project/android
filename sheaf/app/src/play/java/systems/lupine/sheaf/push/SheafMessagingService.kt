package systems.lupine.sheaf.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase A scaffolding. Receives FCM token rotations and incoming push
 * payloads. The token-registration call (POST /v1/devices/push) and the
 * notification-formatting path are wired in Phase B once the backend
 * endpoints are live and the redemption flow is built.
 */
@AndroidEntryPoint
class SheafMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token rotated; registration deferred to Phase B")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.i(TAG, "FCM payload received; rendering deferred to Phase B (data=${message.data})")
    }

    private companion object {
        const val TAG = "SheafMessagingService"
    }
}
