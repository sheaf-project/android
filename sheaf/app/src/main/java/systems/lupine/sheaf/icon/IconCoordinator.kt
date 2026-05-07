package systems.lupine.sheaf.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Swaps the launcher activity-alias between the light and dark icon variants
 * based on the user's selected theme.
 *
 * The manifest declares two activity-aliases, [LIGHT_ALIAS] and [DARK_ALIAS],
 * each with the same intent-filter (LAUNCHER) but different icons. We toggle
 * which one is enabled via [PackageManager.setComponentEnabledSetting]. Only
 * one is enabled at a time so the launcher doesn't show a duplicate, and we
 * enable the target before disabling the previous one to minimise the window
 * where the launcher sees no LAUNCHER component for the package.
 *
 * Wired up in [systems.lupine.sheaf.SheafApplication] via [start], plus
 * [refresh] on configuration change so a system dark-mode flip while the
 * user has theme = "system" updates the icon without waiting for the next
 * cold start.
 */
@Singleton
class IconCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        scope.launch {
            prefs.themeMode.collectLatest { mode -> applyForMode(mode) }
        }
    }

    /** Re-evaluate after a configuration change (system dark-mode flip). */
    fun refresh() {
        scope.launch {
            applyForMode(prefs.themeModeBlocking())
        }
    }

    private fun applyForMode(mode: String) {
        val useDark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> isSystemDark()
        }
        applyComponentState(dark = useDark)
    }

    private fun isSystemDark(): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyComponentState(dark: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        val light = ComponentName(pkg, "$pkg.$LIGHT_ALIAS")
        val darkComp = ComponentName(pkg, "$pkg.$DARK_ALIAS")
        val (enable, disable) = if (dark) darkComp to light else light to darkComp

        // No-op if the launcher is already in the target state. Avoids a
        // pointless setComponentEnabledSetting on every theme-flow emission
        // and the brief launcher refresh that comes with it.
        if (pm.getComponentEnabledSetting(enable) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            pm.getComponentEnabledSetting(disable) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return
        }

        pm.setComponentEnabledSetting(
            enable,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            disable,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private companion object {
        const val LIGHT_ALIAS = "LauncherLight"
        const val DARK_ALIAS = "LauncherDark"
    }
}
