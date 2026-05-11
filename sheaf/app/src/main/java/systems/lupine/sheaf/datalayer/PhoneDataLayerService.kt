package systems.lupine.sheaf.datalayer

import android.content.Context
import android.util.Log
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
        private const val TAG = "SheafPairing"

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
                .addOnSuccessListener {
                    Log.i(TAG, "putDataItem(credentials) succeeded uri=${it.uri}")
                }
                .addOnFailureListener {
                    Log.w(TAG, "putDataItem(credentials) failed", it)
                }
        }

        /**
         * Sends the watch's companion-session credentials. Provisioning
         * (minting via /v1/auth/sessions/secondary) happens here on demand
         * if no credentials have been minted yet — typical trigger paths
         * are post-login and watch-side credential requests.
         *
         * When [force] is true, [WatchSessionRepository.ensureWatchSession]
         * re-mints even if a cached watch token exists. Use this from the
         * watch-initiated request path: the watch only sends that message
         * when its own creds aren't working, so a cached "looks valid"
         * pair on the phone is presumed stale.
         */
        suspend fun pushWatchCredentials(
            context: Context,
            prefs: PreferencesRepository,
            watchSession: WatchSessionRepository,
            force: Boolean = false,
        ) {
            Log.i(TAG, "pushWatchCredentials: force=$force")
            val baseUrl = prefs.baseUrl.first()?.takeIf { it.isNotBlank() }
            if (baseUrl == null) {
                Log.w(TAG, "pushWatchCredentials: aborting, no base URL set")
                return
            }
            val ok = watchSession.ensureWatchSession(force = force)
            if (!ok) {
                Log.w(TAG, "pushWatchCredentials: ensureWatchSession returned false")
                return
            }
            val access = prefs.watchAccessToken.first()
            val refresh = prefs.watchRefreshToken.first()
            if (access == null || refresh == null) {
                Log.w(TAG, "pushWatchCredentials: missing watch tokens after ensure ok")
                return
            }
            putCredentialsItem(context, baseUrl, access, refresh)
        }
    }

    @Inject lateinit var prefs: PreferencesRepository
    @Inject lateinit var watchSession: WatchSessionRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        Log.i(TAG, "onMessageReceived: path=${event.path} sourceNode=${event.sourceNodeId}")
        if (event.path == PATH_CREDENTIALS_REQUEST) {
            scope.launch {
                // The watch only asks when its current creds aren't working,
                // so any cached pair on the phone side is presumed stale.
                // Force a fresh secondary-session mint rather than push
                // whatever happens to be in DataStore (which could be a
                // backup-restored revoked token).
                pushWatchCredentials(applicationContext, prefs, watchSession, force = true)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
