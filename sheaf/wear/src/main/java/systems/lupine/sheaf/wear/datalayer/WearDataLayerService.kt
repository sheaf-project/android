package systems.lupine.sheaf.wear.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import systems.lupine.sheaf.wear.data.WearAuthManager

class WearDataLayerService : WearableListenerService() {

    companion object {
        private const val TAG = "SheafPairing"
        const val PATH_CREDENTIALS = "/sheaf/credentials"
    }

    override fun onDataChanged(events: DataEventBuffer) {
        Log.i(TAG, "onDataChanged: ${events.count} event(s)")
        val authManager = WearAuthManager(applicationContext)
        for (event in events) {
            val path = event.dataItem.uri.path
            if (path == PATH_CREDENTIALS) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val baseUrl      = dataMap.getString("base_url")
                val accessToken  = dataMap.getString("access_token")
                val refreshToken = dataMap.getString("refresh_token")
                if (baseUrl == null || accessToken == null || refreshToken == null) {
                    Log.w(TAG, "credentials DataItem missing fields, skipping")
                    continue
                }
                Log.i(TAG, "credentials DataItem received, saving (baseUrl host=${
                    runCatching { android.net.Uri.parse(baseUrl).host }.getOrNull()
                })")
                authManager.saveCredentials(baseUrl, accessToken, refreshToken)
            } else {
                Log.d(TAG, "ignoring DataItem path=$path")
            }
        }
    }
}
