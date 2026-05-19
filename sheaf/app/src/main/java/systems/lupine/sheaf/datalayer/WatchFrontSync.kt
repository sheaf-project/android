package systems.lupine.sheaf.datalayer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Pushes a lightweight "front status changed, re-sync" nudge to a paired
 * Wear OS watch.
 *
 * Watchface tiles and complications can't observe the phone's front state
 * directly and only re-sync when the wear app is foregrounded. Without a
 * nudge a switch made on the phone (or arriving via push) leaves the
 * watchface stale until the user next opens the wear app. The watch's
 * data-layer listener turns this DataItem into a full re-sync.
 *
 * The nudge carries no front data — just a changing timestamp so the
 * DataItem content differs on every call and the watch's `onDataChanged`
 * actually fires. If no watch is paired the put is a harmless no-op.
 */
object WatchFrontSync {

    private const val TAG = "SheafWatchSync"

    /** Mirror of `WearDataLayerService.PATH_REFRESH` on the watch side. */
    private const val PATH_REFRESH = "/sheaf/refresh"

    fun notifyFrontChanged(context: Context) {
        val request = PutDataMapRequest.create(PATH_REFRESH).apply {
            dataMap.putLong("changed_at", System.currentTimeMillis())
        }
        Wearable.getDataClient(context)
            .putDataItem(request.asPutDataRequest().setUrgent())
            .addOnSuccessListener { Log.d(TAG, "front-changed nudge sent uri=${it.uri}") }
            .addOnFailureListener { Log.w(TAG, "front-changed nudge failed", it) }
    }
}
