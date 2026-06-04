package systems.lupine.sheaf.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class RefreshWithAvatarsAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        writeWidgetPalette(context, glanceId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[FrontingWithAvatarsWidget.KEY_LOADING] = true
                this[FrontingWithAvatarsWidget.KEY_ERROR] = false
            }
        }
        FrontingWithAvatarsWidget().update(context, glanceId)

        try {
            val entryPoint = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val api = entryPoint.sheafApiService()
            val prefsRepo = entryPoint.preferencesRepository()
            val http = entryPoint.okHttpClient()

            val fronts = api.getCurrentFronts()
            val members = api.listMembers()
            val frontingIds = fronts.flatMap { it.memberIds }.toSet()
            val frontingMembers = members.filter { it.id in frontingIds }

            // Render avatars to disk before flipping out of loading state so
            // the first frame the widget paints already has bitmaps available.
            renderWidgetAvatars(context, prefsRepo, http, frontingMembers)

            val ids    = frontingMembers.map { it.id }
            val names  = frontingMembers.map { it.displayNameOrName }
            val colors = frontingMembers.map { it.color ?: "" }

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[FrontingWithAvatarsWidget.KEY_MEMBER_IDS]    = ids.joinToString("|")
                    this[FrontingWithAvatarsWidget.KEY_MEMBER_NAMES]  = names.joinToString("|")
                    this[FrontingWithAvatarsWidget.KEY_MEMBER_COLORS] = colors.joinToString("|")
                    this[FrontingWithAvatarsWidget.KEY_LOADING] = false
                    this[FrontingWithAvatarsWidget.KEY_ERROR] = false
                }
            }
        } catch (e: Exception) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[FrontingWithAvatarsWidget.KEY_LOADING] = false
                    this[FrontingWithAvatarsWidget.KEY_ERROR] = true
                }
            }
        }

        FrontingWithAvatarsWidget().update(context, glanceId)
    }
}
