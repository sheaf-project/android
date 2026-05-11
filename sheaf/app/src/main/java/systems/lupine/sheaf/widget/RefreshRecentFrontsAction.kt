package systems.lupine.sheaf.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class RefreshRecentFrontsAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[RecentFrontsWidget.KEY_LOADING] = true
                this[RecentFrontsWidget.KEY_ERROR] = false
            }
        }
        RecentFrontsWidget().update(context, glanceId)

        try {
            val api = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
                .sheafApiService()

            val members = api.listMembers().associateBy { it.id }
            // listFronts returns newest-first, which is the order we want.
            val fronts = api.listFronts(limit = RecentFrontsWidget.MAX_VISIBLE * 2)

            val entries = fronts.take(RecentFrontsWidget.MAX_VISIBLE).map { f ->
                val names = f.memberIds
                    .mapNotNull { members[it]?.displayNameOrName }
                    .joinToString(RecentFrontsWidget.NAME_SEP)
                RecentEntry(
                    startedAt = f.startedAt,
                    endedAt = f.endedAt,
                    names = names,
                )
            }

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[RecentFrontsWidget.KEY_ENTRIES] = encodeEntries(entries)
                    this[RecentFrontsWidget.KEY_LOADING] = false
                    this[RecentFrontsWidget.KEY_ERROR] = false
                }
            }
        } catch (e: Exception) {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[RecentFrontsWidget.KEY_LOADING] = false
                    this[RecentFrontsWidget.KEY_ERROR] = true
                }
            }
        }

        RecentFrontsWidget().update(context, glanceId)
    }
}
