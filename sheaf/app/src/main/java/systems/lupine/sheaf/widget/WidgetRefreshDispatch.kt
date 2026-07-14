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

// A widget refresh hits the network, so it outlives onUpdate. Without goAsync()
// the receiver is finished the moment onUpdate returns and the process becomes
// killable, so the refresh can be cut off mid-request and the widget just stays
// stale. goAsync() keeps the process alive until finish() is called.
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
    val pending = goAsync()
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
            pending.finish()
        }
    }
}
