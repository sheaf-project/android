package systems.lupine.sheaf.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class RefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Show loading state
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[FrontingWidget.KEY_LOADING] = true
                this[FrontingWidget.KEY_ERROR] = false
            }
        }
        FrontingWidget().update(context, glanceId)

        try {
            val api = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .sheafApiService()

            val fronts = api.getCurrentFronts()
            val members = api.listMembers()
            val frontingIds = fronts.flatMap { it.memberIds }.toSet()
            val frontingMembers = members.filter { it.id in frontingIds }
            val names  = frontingMembers.map { it.displayNameOrName }
            val colors = frontingMembers.map { it.color ?: "" }

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[FrontingWidget.KEY_MEMBER_NAMES]  = names.joinToString("|")
                    this[FrontingWidget.KEY_MEMBER_COLORS] = colors.joinToString("|")
                    this[FrontingWidget.KEY_LOADING] = false
                    this[FrontingWidget.KEY_ERROR] = false
                }
            }
        } catch (e: Exception) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[FrontingWidget.KEY_LOADING] = false
                    this[FrontingWidget.KEY_ERROR] = true
                }
            }
        }

        FrontingWidget().update(context, glanceId)
    }
}
