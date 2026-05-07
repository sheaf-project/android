package systems.lupine.sheaf.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent wear-side preferences. SharedPreferences for symmetry with
 * [WearAuthManager]; DataStore would be overkill for a couple of booleans
 * and would pull in another dep on the watch.
 */
class WearSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wear_settings", Context.MODE_PRIVATE)

    private val _endExistingFronts = MutableStateFlow(
        prefs.getBoolean(KEY_END_EXISTING_FRONTS, DEFAULT_END_EXISTING_FRONTS)
    )

    /** Default for the "end existing fronts" toggle on the switch screen. */
    val endExistingFronts: StateFlow<Boolean> = _endExistingFronts.asStateFlow()

    fun setEndExistingFronts(value: Boolean) {
        prefs.edit().putBoolean(KEY_END_EXISTING_FRONTS, value).apply()
        _endExistingFronts.value = value
    }

    private companion object {
        const val KEY_END_EXISTING_FRONTS = "end_existing_fronts"
        // Mirrors the most common phone-side default — switching front almost
        // always means replacing whoever was there. Power users with cofronter
        // workflows can flip this once and have it stick.
        const val DEFAULT_END_EXISTING_FRONTS = true
    }
}
