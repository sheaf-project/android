package systems.lupine.sheaf.wear.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import systems.lupine.sheaf.wear.complications.parseFrontersJson
import systems.lupine.sheaf.wear.complications.requestAllComplicationUpdates
import systems.lupine.sheaf.wear.data.WearApiClient
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearStore
import systems.lupine.sheaf.wear.data.requestAllTileUpdates
import systems.lupine.sheaf.wear.data.writeFronterTileData

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
                PATH_CREDENTIALS ->
                    if (event.type == DataEvent.TYPE_DELETED) {
                        // Phone signed out and deleted the credential item
                        // (tombstone): drop the watch's session too.
                        Log.i(TAG, "credentials DataItem deleted, clearing session")
                        authManager.clearCredentials()
                    } else {
                        handleCredentials(event, authManager)
                    }
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
        // Tiles and complications cache their own auth view and only redraw
        // when asked. This push is often a reauth landing on a watch that was
        // stuck on the "open Sheaf on phone" message, so nudge them now;
        // otherwise they'd stay signed-out-looking until their next scheduled
        // refresh even though the session is live again.
        runCatching {
            requestAllTileUpdates(applicationContext)
            requestAllComplicationUpdates(applicationContext)
        }.onFailure { Log.w(TAG, "tile/complication refresh after credentials failed", it) }
    }

    /**
     * Handle a phone front-change nudge.
     *
     * The payload carries the current fronter snapshot the phone already
     * computed. We apply it straight to the tile/complication cache and fire
     * their update requests first, so watchface complications refresh even
     * when the watch can't reach the backend at that moment (stale token,
     * off-network, dozing). The previous design shipped only a timestamp and
     * made the watch re-fetch, so a failed fetch left complications frozen.
     * Applying pushed render-data needs no auth; it's data the trusted phone
     * derived.
     *
     * A best-effort full network re-sync still runs afterwards to enrich the
     * member roster, history and avatars that the lightweight payload doesn't
     * carry. Run inline with [runBlocking]: the callback is already on a
     * background thread, and blocking it keeps the service process alive
     * through the refresh rather than racing teardown.
     */
    private fun handleRefreshNudge(event: DataEvent, authManager: WearAuthManager) {
        if (event.type != DataEvent.TYPE_CHANGED) return

        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
        val frontersJson = dataMap.getString("fronters_json")
        if (frontersJson != null) {
            runCatching {
                val fronters = parseFrontersJson(frontersJson)
                writeFronterTileData(applicationContext, fronters)
                requestAllTileUpdates(applicationContext)
                requestAllComplicationUpdates(applicationContext)
                Log.i(TAG, "applied pushed fronter snapshot (${fronters.size} fronting)")
            }.onFailure { Log.w(TAG, "applying pushed fronter snapshot failed", it) }
        }

        if (!authManager.isAuthenticated) {
            Log.d(TAG, "refresh nudge: not authenticated, skipping network re-sync")
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
