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

/** Display modes shared by QuickSwitch and MemberTracker widgets.
 *  Mirrors the wear tile's three-way choice (avatars / names / both). */
internal enum class WidgetDisplayMode {
    /** Avatar + name on each row. Default. */
    AVATARS_AND_NAMES,
    /** Avatar only — circle grid feel, good for dense layouts. */
    AVATARS_ONLY,
    /** Name only — no avatar column. */
    NAMES_ONLY;

    companion object {
        fun fromStored(raw: String?): WidgetDisplayMode = when (raw) {
            AVATARS_ONLY.name -> AVATARS_ONLY
            NAMES_ONLY.name -> NAMES_ONLY
            else -> AVATARS_AND_NAMES
        }
    }
}

private fun trackerModeKey(widgetId: Int) = "tracker_display_mode:$widgetId"
private fun quickSwitchModeKey(widgetId: Int) = "quick_switch_display_mode:$widgetId"

internal fun saveTrackerDisplayMode(context: Context, widgetId: Int, mode: WidgetDisplayMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(trackerModeKey(widgetId), mode.name)
        .apply()
}

internal fun loadTrackerDisplayMode(context: Context, widgetId: Int): WidgetDisplayMode =
    WidgetDisplayMode.fromStored(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(trackerModeKey(widgetId), null)
    )

internal fun saveQuickSwitchDisplayMode(context: Context, widgetId: Int, mode: WidgetDisplayMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(quickSwitchModeKey(widgetId), mode.name)
        .apply()
}

internal fun loadQuickSwitchDisplayMode(context: Context, widgetId: Int): WidgetDisplayMode =
    WidgetDisplayMode.fromStored(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(quickSwitchModeKey(widgetId), null)
    )

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
        .remove(trackerModeKey(widgetId))
        .remove(quickSwitchModeKey(widgetId))
        .apply()
}
