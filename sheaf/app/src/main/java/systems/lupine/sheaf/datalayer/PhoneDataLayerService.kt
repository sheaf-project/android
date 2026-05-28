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
import systems.lupine.sheaf.data.db.PendingFrontSwitch
import systems.lupine.sheaf.data.db.PendingOperationsDao
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.repository.WatchSessionRepository
import systems.lupine.sheaf.data.sync.SyncWorker
import javax.inject.Inject

@AndroidEntryPoint
class PhoneDataLayerService : WearableListenerService() {

    companion object {
        private const val TAG = "SheafPairing"

        const val PATH_CREDENTIALS = "/sheaf/credentials"
        const val PATH_CREDENTIALS_REQUEST = "/sheaf/credentials/request"

        /**
         * Watch-originated "queue this switch on the phone" message,
         * fired by WearSwitchQueue when the watch can't reach the
         * server directly but can reach us via BLE. Payload is the
         * line-encoded form the queue uses on disk:
         * `uuid|createdAt|replaceFronts|memberIds_csv`. We unpack it
         * into a [PendingFrontSwitch] and let the existing
         * [SyncWorker] replay path handle the rest.
         */
        const val PATH_QUEUE_SWITCH = "/sheaf/queue-switch"

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
    @Inject lateinit var pendingOps: PendingOperationsDao

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        Log.i(TAG, "onMessageReceived: path=${event.path} sourceNode=${event.sourceNodeId}")
        when (event.path) {
            PATH_CREDENTIALS_REQUEST -> {
                scope.launch {
                    // The watch only asks when its current creds aren't
                    // working, so any cached pair on the phone side is
                    // presumed stale. Force a fresh secondary-session
                    // mint rather than push whatever happens to be in
                    // DataStore (which could be a backup-restored
                    // revoked token).
                    pushWatchCredentials(applicationContext, prefs, watchSession, force = true)
                }
            }
            PATH_QUEUE_SWITCH -> handleQueueSwitch(event.data)
        }
    }

    private fun handleQueueSwitch(data: ByteArray?) {
        val payload = data?.toString(Charsets.UTF_8) ?: run {
            Log.w(TAG, "queue-switch: empty payload"); return
        }
        // Mirror of WearSwitchQueue.encode: uuid|createdAt|replaceFronts|memberIds_csv.
        val parts = payload.split('|', limit = 4)
        if (parts.size != 4) {
            Log.w(TAG, "queue-switch: malformed payload (parts=${parts.size})"); return
        }
        val uuid = parts[0]
        val createdAt = parts[1].toLongOrNull() ?: run {
            Log.w(TAG, "queue-switch: bad createdAt '${parts[1]}'"); return
        }
        val replaceFronts = parts[2] == "1"
        val memberIdsCsv = parts[3].split(',').filter { it.isNotBlank() }.joinToString(",")
        if (memberIdsCsv.isBlank()) {
            Log.w(TAG, "queue-switch: empty member set"); return
        }
        scope.launch {
            // Insert into the same pending-switches table the phone-side
            // offline flow uses, so SyncWorker can replay it with the
            // watch's original createdAt as startedAt. No de-dup on uuid:
            // the watch only sends after a successful Tasks reply, and we
            // hand exclusive ownership back via the same channel — but
            // worst-case a double would mean two identical past-dated
            // fronts the user can prune from history.
            pendingOps.insertSwitch(
                PendingFrontSwitch(
                    memberIds = memberIdsCsv,
                    replaceFronts = replaceFronts,
                    createdAt = createdAt,
                )
            )
            SyncWorker.schedule(applicationContext)
            Log.i(TAG, "queue-switch: queued switch $uuid for sync (createdAt=$createdAt)")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
