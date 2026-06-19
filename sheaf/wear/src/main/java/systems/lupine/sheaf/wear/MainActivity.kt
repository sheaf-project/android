package systems.lupine.sheaf.wear

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.launch
import com.google.android.gms.wearable.Wearable
import systems.lupine.sheaf.wear.complications.EXTRA_INITIAL_ROUTE
import systems.lupine.sheaf.wear.data.WearApiClient
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearSettingsStore
import systems.lupine.sheaf.wear.data.WearStore
import systems.lupine.sheaf.wear.datalayer.WearDataLayerService
import systems.lupine.sheaf.wear.presentation.WearNavigation
import systems.lupine.sheaf.wear.theme.SheafWearTheme

private const val TAG = "SheafPairing"

class MainActivity : ComponentActivity() {

    private lateinit var authManager: WearAuthManager
    private lateinit var store: WearStore
    private lateinit var settings: WearSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = WearAuthManager(applicationContext)
        store = WearStore(WearApiClient(authManager), applicationContext)
        settings = WearSettingsStore(applicationContext)

        // Try to load cached credentials from the Data Layer first, then fall
        // back to requesting a fresh push from the phone. The cached DataItem
        // is available even if the phone isn't connected at startup yet.
        if (!authManager.isAuthenticated) {
            loadCredentialsFromDataLayer()
        }

        // Recover a lost session without making the user re-pair. If a
        // working companion session goes away while the app is open (a
        // refresh failed offline, the session was revoked, or a stale cached
        // credential was applied on launch and then rejected by the server),
        // the watch would otherwise strand on the signed-out screen even
        // though the phone could mint a fresh session on demand. Watch for
        // the signed-in -> signed-out transition and ask the phone to re-mint
        // and push fresh credentials. We track the previous value so the
        // initial signed-out state (handled by loadCredentialsFromDataLayer,
        // which already asks the phone on a cache miss) and a normal
        // signed-out -> signed-in startup don't trigger a redundant re-mint.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wasAuthenticated = authManager.isAuthenticated
                authManager.isAuthenticatedFlow.collect { authed ->
                    if (!authed && wasAuthenticated) {
                        Log.i(TAG, "companion session lost while app open, requesting reauth from phone")
                        requestCredentialsFromPhone()
                    }
                    wasAuthenticated = authed
                }
            }
        }

        // Complications can deep-link to a specific destination by passing
        // EXTRA_INITIAL_ROUTE. WearNavigation always starts at the menu and
        // navigates on top, so swipe-back from the deep-linked screen lands
        // on the menu like any other entry.
        val initialRoute = intent?.getStringExtra(EXTRA_INITIAL_ROUTE)

        setContent {
            SheafWearTheme {
                WearNavigation(
                    authManager = authManager,
                    store = store,
                    settings = settings,
                    onRequestSync = ::requestCredentialsFromPhone,
                    initialRoute = initialRoute,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (authManager.isAuthenticated) {
            store.loadAll()
        }
    }

    /**
     * Query the Wearable DataClient for a cached credentials item. DataItems
     * are persistent — they survive watch reboots and are available even when
     * the phone is not currently connected. Falls back to a live phone request
     * if no cached item is found.
     */
    private fun loadCredentialsFromDataLayer() {
        Log.i(TAG, "loadCredentialsFromDataLayer: querying cached DataItem")
        val uri = Uri.Builder()
            .scheme("wear")
            .path(WearDataLayerService.PATH_CREDENTIALS)
            .build()
        Wearable.getDataClient(this).getDataItems(uri)
            .addOnSuccessListener { dataItems ->
                var found = false
                for (item in dataItems) {
                    if (item.uri.path == WearDataLayerService.PATH_CREDENTIALS) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val baseUrl      = dataMap.getString("base_url") ?: continue
                        val accessToken  = dataMap.getString("access_token") ?: continue
                        val refreshToken = dataMap.getString("refresh_token") ?: continue
                        Log.i(TAG, "loadCredentialsFromDataLayer: found cached creds, saving")
                        authManager.saveCredentials(baseUrl, accessToken, refreshToken)
                        found = true
                        break
                    }
                }
                dataItems.release()
                if (!found) {
                    Log.i(TAG, "loadCredentialsFromDataLayer: no cached item, asking phone")
                    requestCredentialsFromPhone()
                }
            }
            .addOnFailureListener {
                Log.w(TAG, "loadCredentialsFromDataLayer: getDataItems failed", it)
                requestCredentialsFromPhone()
            }
    }

    private fun requestCredentialsFromPhone() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val nodeId = nodes.firstOrNull()?.id
                if (nodeId == null) {
                    Log.w(TAG, "requestCredentialsFromPhone: no connected nodes")
                    return@addOnSuccessListener
                }
                Log.i(TAG, "requestCredentialsFromPhone: sending /sheaf/credentials/request to $nodeId")
                Wearable.getMessageClient(this)
                    .sendMessage(nodeId, "/sheaf/credentials/request", null)
                    .addOnSuccessListener {
                        Log.i(TAG, "requestCredentialsFromPhone: send succeeded")
                    }
                    .addOnFailureListener {
                        Log.w(TAG, "requestCredentialsFromPhone: send failed", it)
                    }
            }
            .addOnFailureListener {
                Log.w(TAG, "requestCredentialsFromPhone: connectedNodes failed", it)
            }
    }
}
