package systems.lupine.sheaf.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class RefreshMemberTrackerAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Resolve picked member ids for this specific widget instance. Glance
        // hands us a GlanceId; convert it back to the AppWidget id so we can
        // read the per-instance SharedPreferences slot.
        writeWidgetPalette(context, glanceId)
        val widgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        }.getOrDefault(-1)
        val pickedIds = if (widgetId > 0) loadTrackerMembers(context, widgetId) else emptyList()

        if (pickedIds.isEmpty()) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[MemberTrackerWidget.KEY_UNCONFIGURED] = true
                    this[MemberTrackerWidget.KEY_LOADING] = false
                    this[MemberTrackerWidget.KEY_ERROR] = false
                }
            }
            MemberTrackerWidget().update(context, glanceId)
            return
        }

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[MemberTrackerWidget.KEY_UNCONFIGURED] = false
                this[MemberTrackerWidget.KEY_LOADING] = true
                this[MemberTrackerWidget.KEY_ERROR] = false
            }
        }
        MemberTrackerWidget().update(context, glanceId)

        try {
            val entryPoint = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val api = entryPoint.sheafApiService()
            val prefsRepo = entryPoint.preferencesRepository()
            val http = entryPoint.okHttpClient()

            val members = api.listMembers().associateBy { it.id }
            val tracked = pickedIds.mapNotNull { members[it] }
            val fronts = api.getCurrentFronts()
            val frontingIds = fronts.flatMap { it.memberIds }.toSet()

            renderWidgetAvatars(context, prefsRepo, http, tracked)

            val displayMode = if (widgetId > 0) loadTrackerDisplayMode(context, widgetId)
                              else WidgetDisplayMode.AVATARS_AND_NAMES
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[MemberTrackerWidget.KEY_TRACKED_IDS]    = tracked.joinToString("|") { it.id }
                    this[MemberTrackerWidget.KEY_TRACKED_NAMES]  = tracked.joinToString("|") { it.displayNameOrName }
                    this[MemberTrackerWidget.KEY_TRACKED_COLORS] = tracked.joinToString("|") { it.color ?: "" }
                    this[MemberTrackerWidget.KEY_FRONTING_IDS]   = tracked
                        .filter { it.id in frontingIds }
                        .joinToString("|") { it.id }
                    this[MemberTrackerWidget.KEY_DISPLAY_MODE]   = displayMode.name
                    this[MemberTrackerWidget.KEY_LOADING] = false
                    this[MemberTrackerWidget.KEY_ERROR] = false
                }
            }
        } catch (e: Exception) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[MemberTrackerWidget.KEY_LOADING] = false
                    this[MemberTrackerWidget.KEY_ERROR] = true
                }
            }
        }

        MemberTrackerWidget().update(context, glanceId)
    }
}
