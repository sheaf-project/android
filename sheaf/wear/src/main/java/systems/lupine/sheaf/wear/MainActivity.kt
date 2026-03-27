package systems.lupine.sheaf.wear

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import systems.lupine.sheaf.wear.data.WearApiClient
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearStore
import systems.lupine.sheaf.wear.datalayer.WearDataLayerService
import systems.lupine.sheaf.wear.presentation.WearNavigation
import systems.lupine.sheaf.wear.theme.SheafWearTheme

class MainActivity : ComponentActivity() {

    private lateinit var authManager: WearAuthManager
    private lateinit var store: WearStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = WearAuthManager(applicationContext)
        store = WearStore(WearApiClient(authManager), applicationContext)

        // Try to load cached credentials from the Data Layer first, then fall
        // back to requesting a fresh push from the phone. The cached DataItem
        // is available even if the phone isn't connected at startup yet.
        if (!authManager.isAuthenticated) {
            loadCredentialsFromDataLayer()
        }

        setContent {
            SheafWearTheme {
                WearNavigation(
                    authManager = authManager,
                    store = store,
                    onRequestSync = ::loadCredentialsFromDataLayer,
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
                        authManager.saveCredentials(baseUrl, accessToken, refreshToken)
                        found = true
                        break
                    }
                }
                dataItems.release()
                if (!found) requestCredentialsFromPhone()
            }
            .addOnFailureListener { requestCredentialsFromPhone() }
    }

    private fun requestCredentialsFromPhone() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.firstOrNull()?.id?.let { nodeId ->
                    Wearable.getMessageClient(this)
                        .sendMessage(nodeId, "/sheaf/credentials/request", null)
                }
            }
    }
}
