package systems.lupine.sheaf.wear.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearAuthManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wear_auth", Context.MODE_PRIVATE)

    private fun isCredentialed() =
        !prefs.getString("access_token", null).isNullOrBlank() &&
        !prefs.getString("base_url", null).isNullOrBlank()

    private val _isAuthenticatedFlow = MutableStateFlow(isCredentialed())
    val isAuthenticatedFlow: StateFlow<Boolean> = _isAuthenticatedFlow.asStateFlow()

    val isAuthenticated: Boolean get() = _isAuthenticatedFlow.value

    // React to SharedPreferences writes from other instances (e.g. WearDataLayerService)
    private val prefsListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "access_token" || key == "base_url") {
                _isAuthenticatedFlow.value = isCredentialed()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        private set(v) = prefs.edit().putString("base_url", v).apply()

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        private set(v) {
            if (v != null) prefs.edit().putString("access_token", v).apply()
            else prefs.edit().remove("access_token").apply()
        }

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        private set(v) {
            if (v != null) prefs.edit().putString("refresh_token", v).apply()
            else prefs.edit().remove("refresh_token").apply()
        }

    fun saveCredentials(baseUrl: String, accessToken: String, refreshToken: String) {
        this.baseUrl = baseUrl
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        _isAuthenticatedFlow.value = true
    }

    fun clearCredentials() {
        prefs.edit()
            .remove("base_url")
            .remove("access_token")
            .remove("refresh_token")
            .apply()
        _isAuthenticatedFlow.value = false
    }
}
