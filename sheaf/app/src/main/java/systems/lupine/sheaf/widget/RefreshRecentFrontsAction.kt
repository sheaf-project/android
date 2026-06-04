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
        writeWidgetPalette(context, glanceId)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[RecentFrontsWidget.KEY_LOADING] = true
                this[RecentFrontsWidget.KEY_ERROR] = false
            }
        }
        RecentFrontsWidget().update(context, glanceId)

        try {
            val entryPoint = EntryPointAccessors
                .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val api = entryPoint.sheafApiService()
            val prefsRepo = entryPoint.preferencesRepository()
            val http = entryPoint.okHttpClient()

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
                    memberIds = f.memberIds,
                )
            }

            // Pre-render avatar PNGs for every member that appears in
            // the visible entries so the widget can decode them
            // synchronously from filesDir during render. The
            // member-tracker / quick-switch widgets do the same and we
            // happily share the same cache directory — re-rendering
            // the same id twice on the same tick is cheap and avoids
            // a per-widget bookkeeping pass to dedupe across them.
            val members_in_view = entries
                .flatMap { it.memberIds }
                .distinct()
                .mapNotNull { members[it] }
            renderWidgetAvatars(context, prefsRepo, http, members_in_view)

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
