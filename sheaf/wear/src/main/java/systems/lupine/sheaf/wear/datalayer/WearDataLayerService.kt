package systems.lupine.sheaf.wear.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import systems.lupine.sheaf.wear.data.WearApiClient
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearStore

class WearDataLayerService : WearableListenerService() {

    companion object {
        private const val TAG = "SheafPairing"
        const val PATH_CREDENTIALS = "/sheaf/credentials"

        /**
         * The phone writes a DataItem here after any front-status change.
         * It carries no front data — just a changing timestamp so the
         * item content differs each push and onDataChanged actually fires
         * — and serves purely as a "re-sync now" nudge so watchface tiles
         * and complications don't sit stale until the wear app is next
         * opened.
         */
        const val PATH_REFRESH = "/sheaf/refresh"
    }

    override fun onDataChanged(events: DataEventBuffer) {
        Log.i(TAG, "onDataChanged: ${events.count} event(s)")
        val authManager = WearAuthManager(applicationContext)
        for (event in events) {
            when (event.dataItem.uri.path) {
                PATH_CREDENTIALS -> handleCredentials(event, authManager)
                PATH_REFRESH -> handleRefreshNudge(event, authManager)
                else -> Log.d(TAG, "ignoring DataItem path=${event.dataItem.uri.path}")
            }
        }
    }

    private fun handleCredentials(event: DataEvent, authManager: WearAuthManager) {
        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
        val baseUrl      = dataMap.getString("base_url")
        val accessToken  = dataMap.getString("access_token")
        val refreshToken = dataMap.getString("refresh_token")
        if (baseUrl == null || accessToken == null || refreshToken == null) {
            Log.w(TAG, "credentials DataItem missing fields, skipping")
            return
        }
        Log.i(TAG, "credentials DataItem received, saving (baseUrl host=${
            runCatching { android.net.Uri.parse(baseUrl).host }.getOrNull()
        })")
        authManager.saveCredentials(baseUrl, accessToken, refreshToken)
    }

    /**
     * Handle a phone front-change nudge by re-syncing immediately. The
     * sync writes the tile-data snapshot and fires tile + complication
     * update requests (see [WearStore.refreshNow]). Run inline with
     * [runBlocking]: the callback is already on a background thread, and
     * blocking it keeps the service process alive through the refresh
     * rather than racing teardown with a fire-and-forget coroutine.
     */
    private fun handleRefreshNudge(event: DataEvent, authManager: WearAuthManager) {
        if (event.type != DataEvent.TYPE_CHANGED) return
        if (!authManager.isAuthenticated) {
            Log.d(TAG, "refresh nudge ignored: not authenticated")
            return
        }
        Log.i(TAG, "refresh nudge received, re-syncing")
        runBlocking {
            runCatching {
                WearStore(WearApiClient(authManager), applicationContext).refreshNow()
            }.onFailure { Log.w(TAG, "refresh nudge sync failed", it) }
        }
    }
}
