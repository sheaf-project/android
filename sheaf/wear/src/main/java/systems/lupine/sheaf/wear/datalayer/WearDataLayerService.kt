package systems.lupine.sheaf.wear.datalayer

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import systems.lupine.sheaf.wear.data.WearAuthManager

class WearDataLayerService : WearableListenerService() {

    companion object {
        const val PATH_CREDENTIALS = "/sheaf/credentials"
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val authManager = WearAuthManager(applicationContext)
        for (event in events) {
            if (event.dataItem.uri.path == PATH_CREDENTIALS) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val baseUrl      = dataMap.getString("base_url") ?: continue
                val accessToken  = dataMap.getString("access_token") ?: continue
                val refreshToken = dataMap.getString("refresh_token") ?: continue
                authManager.saveCredentials(baseUrl, accessToken, refreshToken)
            }
        }
    }
}
