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

    /**
     * Where this instance serves files from, when it serves them from
     * somewhere other than the API host. Null means the API host.
     *
     * Not a secret, so it sits here rather than in the encrypted credential
     * store: it is public configuration, readable from the same unauthenticated
     * endpoint the login screen already reads. Stored at all because the tile
     * avatars are rendered on whatever refresh happens to run, which may be
     * long after the last time the config was fetched.
     */
    var fileCdnBase: String?
        get() = prefs.getString(KEY_FILE_CDN_BASE, null)?.ifBlank { null }
        set(value) {
            val cleaned = value?.trim()?.ifBlank { null }
            // An instance that drops its CDN must stop being asked for files
            // there, so clearing is a real operation rather than a no-op.
            if (cleaned == null) {
                prefs.edit().remove(KEY_FILE_CDN_BASE).apply()
            } else {
                prefs.edit().putString(KEY_FILE_CDN_BASE, cleaned).apply()
            }
        }

    private companion object {
        const val KEY_END_EXISTING_FRONTS = "end_existing_fronts"
        const val KEY_FILE_CDN_BASE = "file_cdn_base"
        // Mirrors the most common phone-side default — switching front almost
        // always means replacing whoever was there. Power users with cofronter
        // workflows can flip this once and have it stick.
        const val DEFAULT_END_EXISTING_FRONTS = true
    }
}
