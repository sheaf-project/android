package systems.lupine.sheaf.datalayer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Pushes a "front status changed" update to a paired Wear OS watch, carrying
 * the current fronter snapshot in the payload.
 *
 * Watchface tiles and complications can't observe the phone's front state
 * directly. The watch's data-layer listener applies the snapshot we send here
 * straight to its tile/complication cache and refreshes them, so they update
 * even if the watch can't reach the backend at that moment. The watch still
 * runs a best-effort network re-sync afterwards for data the lightweight
 * payload doesn't carry (full roster, history, avatars).
 *
 * `changed_at` ensures the DataItem content differs on every call so the
 * watch's `onDataChanged` actually fires. If no watch is paired the put is a
 * harmless no-op.
 */
object WatchFrontSync {

    private const val TAG = "SheafWatchSync"

    /** Mirror of `WearDataLayerService.PATH_REFRESH` on the watch side. */
    private const val PATH_REFRESH = "/sheaf/refresh"

    /**
     * One fronter as the watch expects it in the snapshot. Field shape
     * (id / name / since) matches the watch's FronterRow so the watch writes
     * the decoded JSON straight to its `fronters` cache key. [since] is the
     * effective per-member fronting-since: chain-aware member_since when the
     * system coalesces contiguous fronts, else the front's started_at.
     */
    @JsonClass(generateAdapter = true)
    data class FronterPayload(
        val id: String,
        val name: String,
        val since: String = "",
    )

    private val adapter = Moshi.Builder().build().adapter<List<FronterPayload>>(
        Types.newParameterizedType(List::class.java, FronterPayload::class.java),
    )

    /** Push the current fronter snapshot so the watch can refresh offline. */
    fun notifyFrontChanged(context: Context, fronters: List<FronterPayload>) {
        val request = PutDataMapRequest.create(PATH_REFRESH).apply {
            dataMap.putLong("changed_at", System.currentTimeMillis())
            dataMap.putString("fronters_json", adapter.toJson(fronters))
        }
        put(context, request)
    }

    /**
     * Timestamp-only nudge for callers without the snapshot in hand (e.g. the
     * FCM push handler). Deliberately omits `fronters_json` so the watch does
     * a full network re-sync rather than applying a payload; sending an empty
     * list here would wrongly overwrite the cache with "no one fronting".
     * Prefer the snapshot overload wherever the fronting state is available.
     */
    fun notifyFrontChanged(context: Context) {
        val request = PutDataMapRequest.create(PATH_REFRESH).apply {
            dataMap.putLong("changed_at", System.currentTimeMillis())
        }
        put(context, request)
    }

    private fun put(context: Context, request: PutDataMapRequest) {
        Wearable.getDataClient(context)
            .putDataItem(request.asPutDataRequest().setUrgent())
            .addOnSuccessListener { Log.d(TAG, "front-changed push sent uri=${it.uri}") }
            .addOnFailureListener { Log.w(TAG, "front-changed push failed", it) }
    }
}
