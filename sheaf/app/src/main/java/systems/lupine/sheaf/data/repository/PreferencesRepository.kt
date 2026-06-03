package systems.lupine.sheaf.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("sheaf_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_PALETTE = stringPreferencesKey("theme_palette")
        // Theme stickiness toggle. When true, this device follows the
        // account's shared Android theme (client_settings/android on the
        // server) — saving on one Android device propagates to the
        // others on next load. When false, this device pins to its
        // local DataStore values and the backend isn't touched.
        //
        // Defaults to true: existing users already mirrored their last
        // theme + palette choice up to the backend under a one-way
        // write, so flipping sync ON by default means the backend value
        // matches what they had locally — no surprise re-themes on
        // upgrade. Users who want per-device control flip it off in
        // Settings → Appearance.
        val KEY_THEME_SYNCED = booleanPreferencesKey("theme_synced")
        val KEY_FRONT_NOTIFICATION = booleanPreferencesKey("front_notification")
        val KEY_CF_CLIENT_ID = stringPreferencesKey("cf_client_id")
        val KEY_CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")
        val KEY_FILE_CDN_BASE = stringPreferencesKey("file_cdn_base")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        // Mirrors the backend's sheaf_trusted_device cookie. We store it
        // verbatim plus its expiry-millis so the CookieJar can drop it once
        // expired without a server round-trip.
        val KEY_TRUSTED_DEVICE_COOKIE = stringPreferencesKey("trusted_device_cookie")
        val KEY_TRUSTED_DEVICE_EXPIRES_AT = longPreferencesKey("trusted_device_expires_at")
        // Wear companion-session credentials, distinct from the phone's
        // primary tokens so the watch can rotate its own one-shot refresh
        // JWT without colliding with the phone's rotation. Provisioned
        // via POST /v1/auth/sessions/secondary after login.
        val KEY_WATCH_ACCESS_TOKEN = stringPreferencesKey("watch_access_token")
        val KEY_WATCH_REFRESH_TOKEN = stringPreferencesKey("watch_refresh_token")
        val KEY_WATCH_SESSION_ID = stringPreferencesKey("watch_session_id")
        // Stable opaque per-install id. Lets the server distinguish a
        // push-token rotation on the same install (update-in-place) from
        // a fresh install with a new token (insert). Cleared on logout
        // so account-switching on one device looks like a fresh install
        // to the server.
        val KEY_PUSH_INSTALL_ID = stringPreferencesKey("push_install_id")
        // Front-history pagination preference. "infinite" = cursor-based
        // load-more (default), "paged" = offset-based numbered pages.
        // Mirrors web's view toggle so the choice carries between clients
        // for users who use both.
        val KEY_HISTORY_VIEW = stringPreferencesKey("history_view")
        val KEY_HISTORY_PAGE_SIZE = intPreferencesKey("history_page_size")
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { it[KEY_BASE_URL] }
    val fileCdnBase: Flow<String?> = context.dataStore.data.map { it[KEY_FILE_CDN_BASE] }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val watchAccessToken: Flow<String?> = context.dataStore.data.map { it[KEY_WATCH_ACCESS_TOKEN] }
    val watchRefreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_WATCH_REFRESH_TOKEN] }
    val watchSessionId: Flow<String?> = context.dataStore.data.map { it[KEY_WATCH_SESSION_ID] }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "system" }
    val themePalette: Flow<String> =
        context.dataStore.data.map { it[KEY_PALETTE] ?: "purple" }
    val themeSynced: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_THEME_SYNCED] ?: true }
    val frontNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_FRONT_NOTIFICATION] ?: false }
    val cfClientId: Flow<String?> = context.dataStore.data.map { it[KEY_CF_CLIENT_ID] }
    val cfClientSecret: Flow<String?> = context.dataStore.data.map { it[KEY_CF_CLIENT_SECRET] }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_LOCK] ?: false }
    val historyView: Flow<String> = context.dataStore.data.map { it[KEY_HISTORY_VIEW] ?: "infinite" }
    val historyPageSize: Flow<Int> = context.dataStore.data.map { it[KEY_HISTORY_PAGE_SIZE] ?: 50 }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = normalizeBaseUrl(url) }
    }

    suspend fun saveFileCdnBase(url: String?) {
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(KEY_FILE_CDN_BASE)
            else it[KEY_FILE_CDN_BASE] = url.trimEnd('/')
        }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit {
            it[KEY_ACCESS_TOKEN] = access
            it[KEY_REFRESH_TOKEN] = refresh
        }
        // The watch no longer rides on the phone's primary refresh token —
        // it gets its own companion session via /v1/auth/sessions/secondary
        // so each device can rotate independently. Wear credentials are
        // pushed via PhoneDataLayerService once provisioned.
    }

    suspend fun saveWatchTokens(access: String, refresh: String, sessionId: String) {
        context.dataStore.edit {
            it[KEY_WATCH_ACCESS_TOKEN] = access
            it[KEY_WATCH_REFRESH_TOKEN] = refresh
            it[KEY_WATCH_SESSION_ID] = sessionId
        }
    }

    suspend fun clearWatchTokens() {
        context.dataStore.edit {
            it.remove(KEY_WATCH_ACCESS_TOKEN)
            it.remove(KEY_WATCH_REFRESH_TOKEN)
            it.remove(KEY_WATCH_SESSION_ID)
        }
    }

    /**
     * Returns the existing install id, or generates one (UUID) and persists
     * it on first call. Stable across token rotations and app restarts;
     * cleared on logout via [clearTokens].
     */
    suspend fun getOrCreatePushInstallId(): String {
        val existing = context.dataStore.data.first()[KEY_PUSH_INSTALL_ID]
        if (existing != null) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_PUSH_INSTALL_ID] = fresh }
        return fresh
    }

    suspend fun saveTheme(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun savePalette(paletteId: String) {
        context.dataStore.edit { it[KEY_PALETTE] = paletteId }
    }

    suspend fun saveThemeSynced(synced: Boolean) {
        context.dataStore.edit { it[KEY_THEME_SYNCED] = synced }
    }

    suspend fun saveFrontNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FRONT_NOTIFICATION] = enabled }
    }

    suspend fun saveAppLock(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK] = enabled }
    }

    suspend fun saveHistoryView(view: String) {
        context.dataStore.edit { it[KEY_HISTORY_VIEW] = view }
    }

    suspend fun saveHistoryPageSize(size: Int) {
        context.dataStore.edit { it[KEY_HISTORY_PAGE_SIZE] = size }
    }

    suspend fun clearTokens() {
        context.dataStore.edit {
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
            // Watch session is a server-side child of the phone's session;
            // when the phone's tokens go, the watch's are about to follow
            // via cascade revocation anyway. Drop them locally so a new
            // login mints a fresh pair.
            it.remove(KEY_WATCH_ACCESS_TOKEN)
            it.remove(KEY_WATCH_REFRESH_TOKEN)
            it.remove(KEY_WATCH_SESSION_ID)
            // Per the mobile push design: install id is logout-scoped so
            // account-switching looks like a fresh install to the server.
            it.remove(KEY_PUSH_INSTALL_ID)
        }
    }

    suspend fun saveCfTokens(clientId: String, clientSecret: String) {
        context.dataStore.edit {
            it[KEY_CF_CLIENT_ID] = clientId
            it[KEY_CF_CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun clearCfTokens() {
        context.dataStore.edit {
            it.remove(KEY_CF_CLIENT_ID)
            it.remove(KEY_CF_CLIENT_SECRET)
        }
    }

    suspend fun saveTrustedDeviceCookie(value: String, expiresAtMs: Long) {
        context.dataStore.edit {
            it[KEY_TRUSTED_DEVICE_COOKIE] = value
            it[KEY_TRUSTED_DEVICE_EXPIRES_AT] = expiresAtMs
        }
    }

    suspend fun clearTrustedDeviceCookie() {
        context.dataStore.edit {
            it.remove(KEY_TRUSTED_DEVICE_COOKIE)
            it.remove(KEY_TRUSTED_DEVICE_EXPIRES_AT)
        }
    }

    /**
     * Synchronous read for use from non-suspending contexts (the OkHttp
     * [okhttp3.CookieJar] callbacks). Returns the stored cookie value if it
     * hasn't expired, otherwise null.
     */
    fun trustedDeviceCookieBlocking(): String? = kotlinx.coroutines.runBlocking {
        val prefs = context.dataStore.data.first()
        val value = prefs[KEY_TRUSTED_DEVICE_COOKIE] ?: return@runBlocking null
        val expiresAt = prefs[KEY_TRUSTED_DEVICE_EXPIRES_AT] ?: 0L
        if (expiresAt > System.currentTimeMillis()) value else null
    }

}

// Normalize a user-typed server URL into something the OkHttp interceptor can
// parse. Trims whitespace and a trailing slash. If the user didn't write a
// scheme we default to https:// so a bare `app.sheaf.sh` or
// `app.sheaf.sh:8080` works; opting into plaintext requires explicitly
// typing `http://`.
internal fun normalizeBaseUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return trimmed
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
        else -> "https://$trimmed"
    }
}
