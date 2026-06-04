package systems.lupine.sheaf.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import systems.lupine.sheaf.ui.theme.SheafPalette

/**
 * Shared per-widget palette state key. Every refresh action writes the
 * currently-selected SheafPalette id here so the composable can recolor
 * its `GlanceTheme(colors=...)` to match the in-app palette. Defaults
 * silently to [SheafPalette.default] when missing — protects upgrade
 * from pre-themed-widget builds where the key isn't yet set.
 */
internal val KEY_WIDGET_PALETTE = stringPreferencesKey("widget_palette_id")

/**
 * Wraps Glance content in a [GlanceTheme] coloured from the user's
 * selected [SheafPalette]. Falls through to GlanceTheme's default
 * (system dynamic colors on Android 12+) when the palette is Material
 * You so the wallpaper-derived scheme keeps working.
 *
 * Light vs dark selection follows the device's system theme as Glance
 * does by default — the in-app "always dark" preference doesn't
 * cleanly translate to widget chrome (Glance picks via system
 * uiMode), and night-mode-on-widgets is a fairly conventional
 * convention for home-screen widgets anyway.
 */
@Composable
internal fun SheafGlanceTheme(content: @Composable () -> Unit) {
    val prefs = currentState<Preferences>()
    val paletteId = prefs[KEY_WIDGET_PALETTE] ?: SheafPalette.default.id
    if (paletteId == SheafPalette.MATERIAL_YOU_ID) {
        GlanceTheme(content = content)
        return
    }
    val palette = SheafPalette.fromId(paletteId)
    GlanceTheme(
        colors = ColorProviders(light = palette.light, dark = palette.dark),
        content = content,
    )
}

/**
 * Process-scoped coroutine scope for firing widget refreshes from
 * config activities. We can't use the activity's lifecycleScope —
 * config activities finish() immediately after kicking off the
 * refresh, which cancels lifecycleScope and kills the in-flight
 * fetch, leaving the widget pinned on its "Refreshing..." state.
 * This scope lives for the process lifetime; the work itself is
 * a few hundred ms of network + I/O, well under any reasonable
 * abandonment threshold.
 */
internal val WidgetRefreshScope: CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Convenience for refresh actions — reads the user's currently-selected
 * palette via [PreferencesRepository] (off the widget Hilt entry point)
 * and persists the id into the widget's Glance preferences blob so
 * [SheafGlanceTheme] picks it up on the next composition.
 *
 * Falls through silently to the default palette if the DI lookup or
 * datastore read trips — widgets that haven't seen this key still
 * render fine with the default.
 */
internal suspend fun writeWidgetPalette(
    context: Context,
    glanceId: GlanceId,
) {
    val paletteId = runCatching {
        EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .preferencesRepository()
            .themePalette
            .first()
    }.getOrDefault(SheafPalette.default.id)
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
        prefs.toMutablePreferences().apply {
            this[KEY_WIDGET_PALETTE] = paletteId
        }
    }
}
