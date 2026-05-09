package systems.lupine.sheaf.datalayer

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.repository.WatchSessionRepository
import javax.inject.Inject

@AndroidEntryPoint
class PhoneDataLayerService : WearableListenerService() {

    companion object {
        const val PATH_CREDENTIALS = "/sheaf/credentials"
        const val PATH_CREDENTIALS_REQUEST = "/sheaf/credentials/request"

        /**
         * Push raw credentials to the watch. The watch always gets the
         * companion-session creds (its own session), never the phone's
         * primary tokens — see [pushWatchCredentials].
         */
        private fun putCredentialsItem(
            context: Context,
            baseUrl: String,
            accessToken: String,
            refreshToken: String,
        ) {
            val request = PutDataMapRequest.create(PATH_CREDENTIALS).apply {
                dataMap.putString("base_url", baseUrl)
                dataMap.putString("access_token", accessToken)
                dataMap.putString("refresh_token", refreshToken)
                dataMap.putLong("updated_at", System.currentTimeMillis())
            }
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
        }

        /**
         * Sends the watch's companion-session credentials. Provisioning
         * (minting via /v1/auth/sessions/secondary) happens here on demand
         * if no credentials have been minted yet — typical trigger paths
         * are post-login and watch-side credential requests.
         */
        suspend fun pushWatchCredentials(
            context: Context,
            prefs: PreferencesRepository,
            watchSession: WatchSessionRepository,
        ) {
            val baseUrl = prefs.baseUrl.first()?.takeIf { it.isNotBlank() } ?: return
            if (!watchSession.ensureWatchSession()) return
            val access = prefs.watchAccessToken.first() ?: return
            val refresh = prefs.watchRefreshToken.first() ?: return
            putCredentialsItem(context, baseUrl, access, refresh)
        }
    }

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var watchSession: WatchSessionRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == PATH_CREDENTIALS_REQUEST) {
            scope.launch {
                pushWatchCredentials(applicationContext, prefs, watchSession)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
