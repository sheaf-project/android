package systems.lupine.sheaf.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class RefreshQuickSwitchAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val widgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        }.getOrDefault(-1)
        val pickedIds = if (widgetId > 0) loadQuickSwitchMembers(context, widgetId) else emptyList()

        if (pickedIds.isEmpty()) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[QuickSwitchWidget.KEY_UNCONFIGURED] = true
                    this[QuickSwitchWidget.KEY_LOADING] = false
                    this[QuickSwitchWidget.KEY_ERROR] = false
                }
            }
            QuickSwitchWidget().update(context, glanceId)
            return
        }

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[QuickSwitchWidget.KEY_UNCONFIGURED] = false
                this[QuickSwitchWidget.KEY_LOADING] = true
                this[QuickSwitchWidget.KEY_ERROR] = false
            }
        }
        QuickSwitchWidget().update(context, glanceId)

        try {
            val entryPoint = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val api = entryPoint.sheafApiService()
            val prefsRepo = entryPoint.preferencesRepository()
            val http = entryPoint.okHttpClient()

            val members = api.listMembers().associateBy { it.id }
            val picked = pickedIds.mapNotNull { members[it] }

            renderWidgetAvatars(context, prefsRepo, http, picked)

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[QuickSwitchWidget.KEY_PICKED_IDS]    = picked.joinToString("|") { it.id }
                    this[QuickSwitchWidget.KEY_PICKED_NAMES]  = picked.joinToString("|") { it.displayNameOrName }
                    this[QuickSwitchWidget.KEY_PICKED_COLORS] = picked.joinToString("|") { it.color ?: "" }
                    this[QuickSwitchWidget.KEY_LOADING] = false
                    this[QuickSwitchWidget.KEY_ERROR] = false
                }
            }
        } catch (e: Exception) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[QuickSwitchWidget.KEY_LOADING] = false
                    this[QuickSwitchWidget.KEY_ERROR] = true
                }
            }
        }

        QuickSwitchWidget().update(context, glanceId)
    }
}
