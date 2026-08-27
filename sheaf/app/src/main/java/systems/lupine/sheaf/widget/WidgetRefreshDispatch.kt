package systems.lupine.sheaf.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "WidgetRefresh"

// A widget refresh hits the network, so it outlives onUpdate. Once the receiver
// finishes, the process becomes killable and the refresh can be cut off
// mid-request, leaving the widget stale until the next tick.
//
// goAsync() is how a receiver asks to stay alive, but there is only one
// PendingResult per dispatch and GlanceAppWidgetReceiver.onUpdate claims it for
// its own compose before any of this runs. So in practice these refreshes are
// NOT holding the broadcast open, and have not been since this was written.
// Losing a refresh to process death is a stale widget, not lost data, so this
// is a known limitation rather than a live bug; the durable fix is to enqueue
// the refresh as expedited work instead of doing it in the receiver.
//
// The broadcast still has a hard deadline (10s in the foreground, 60s in the
// background) before the system complains, so the work is bounded well inside
// that: a refresh that cannot finish in time is better dropped than turned into
// an ANR, and the next update tick will try again.
private const val REFRESH_TIMEOUT_MS = 8_000L

/** Run [action] for each widget id, keeping the broadcast alive until it completes. */
fun BroadcastReceiver.refreshWidgets(
    context: Context,
    appWidgetIds: IntArray,
    action: ActionCallback,
) {
    // goAsync() hands out the receiver's PendingResult exactly once and nulls
    // its own reference, so a second caller in the same dispatch gets null.
    // GlanceAppWidgetReceiver.onUpdate already calls it for its own compose,
    // and every caller here runs after super.onUpdate(), so null is the normal
    // case rather than an edge one. Treating it as non-null threw out of the
    // finally below and took the process with it.
    val pending: BroadcastReceiver.PendingResult? = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                val glanceManager = GlanceAppWidgetManager(context)
                appWidgetIds.forEach { appWidgetId ->
                    val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                    action.onAction(context, glanceId, actionParametersOf())
                }
            } ?: Log.w(TAG, "refresh timed out for ${appWidgetIds.size} widget(s)")
        } catch (t: Throwable) {
            Log.w(TAG, "widget refresh failed: ${t::class.simpleName}")
        } finally {
            // Nothing to release when Glance holds the broadcast; the refresh
            // still runs, it just isn't protected from the process being
            // killed. A cut-short refresh leaves the widget stale until the
            // next tick, which is what happens today anyway.
            runCatching { pending?.finish() }
                .onFailure { Log.w(TAG, "could not finish broadcast: ${it::class.simpleName}") }
        }
    }
}
