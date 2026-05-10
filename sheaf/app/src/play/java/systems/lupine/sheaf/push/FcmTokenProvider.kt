package systems.lupine.sheaf.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenProvider @Inject constructor() : PushTokenProvider {
    override val platform: PushPlatform = PushPlatform.FCM

    override suspend fun getToken(): String? = try {
        FirebaseMessaging.getInstance().token.await()
    } catch (e: IllegalStateException) {
        // FirebaseApp wasn't initialised — typically because google-services.json
        // isn't present in the build. Treat as "no push on this device" rather
        // than crashing.
        Log.w(TAG, "Firebase not initialised; FCM token unavailable", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "FCM token retrieval failed", e)
        null
    }

    private companion object {
        const val TAG = "FcmTokenProvider"
    }
}
