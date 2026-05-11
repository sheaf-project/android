package systems.lupine.sheaf.widget

import android.content.Context

// Per-appWidgetId configuration storage. Multi-add of the same widget type
// each gets an independent slot keyed by the framework-issued widget id.
//
// Two surfaces share this store:
//  - Member tracker widget: persisted selection of which members to watch
//  - Quick switch widget: which members are tappable from the launcher
//
// Selections are stored as comma-separated id lists. Missing key means
// "not yet configured" which the widget renders as a configure-prompt.

private const val PREFS_NAME = "widget_config"

private fun trackerKey(widgetId: Int) = "tracker_members:$widgetId"
private fun quickSwitchKey(widgetId: Int) = "quick_switch_members:$widgetId"

internal fun saveTrackerMembers(context: Context, widgetId: Int, ids: List<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(trackerKey(widgetId), ids.joinToString(","))
        .apply()
}

internal fun loadTrackerMembers(context: Context, widgetId: Int): List<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(trackerKey(widgetId), null)
        ?: return emptyList()
    return raw.split(',').filter { it.isNotBlank() }
}

internal fun saveQuickSwitchMembers(context: Context, widgetId: Int, ids: List<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(quickSwitchKey(widgetId), ids.joinToString(","))
        .apply()
}

internal fun loadQuickSwitchMembers(context: Context, widgetId: Int): List<String> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(quickSwitchKey(widgetId), null)
        ?: return emptyList()
    return raw.split(',').filter { it.isNotBlank() }
}

internal fun clearWidgetConfig(context: Context, widgetId: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(trackerKey(widgetId))
        .remove(quickSwitchKey(widgetId))
        .apply()
}
