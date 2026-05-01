package systems.lupine.sheaf.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import systems.lupine.sheaf.datalayer.PhoneDataLayerService
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
        val KEY_FRONT_NOTIFICATION = booleanPreferencesKey("front_notification")
        val KEY_CF_CLIENT_ID = stringPreferencesKey("cf_client_id")
        val KEY_CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")
        val KEY_FILE_CDN_BASE = stringPreferencesKey("file_cdn_base")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { it[KEY_BASE_URL] }
    val fileCdnBase: Flow<String?> = context.dataStore.data.map { it[KEY_FILE_CDN_BASE] }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "system" }
    val frontNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_FRONT_NOTIFICATION] ?: false }
    val cfClientId: Flow<String?> = context.dataStore.data.map { it[KEY_CF_CLIENT_ID] }
    val cfClientSecret: Flow<String?> = context.dataStore.data.map { it[KEY_CF_CLIENT_SECRET] }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_APP_LOCK] ?: false }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trimEnd('/') }
    }

    suspend fun saveFileCdnBase(url: String?) {
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(KEY_FILE_CDN_BASE)
            else it[KEY_FILE_CDN_BASE] = url.trimEnd('/')
        }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        val updated = context.dataStore.edit {
            it[KEY_ACCESS_TOKEN] = access
            it[KEY_REFRESH_TOKEN] = refresh
        }
        // Backend treats refresh tokens as one-shot (jti consumed on /refresh).
        // Push the new pair to the wear app immediately so it doesn't hold a
        // stale token that would trip reuse detection and kill the session.
        // Best-effort: fire-and-forget, no-op if the watch isn't paired.
        val baseUrl = updated[KEY_BASE_URL]
        if (baseUrl != null) {
            runCatching {
                PhoneDataLayerService.pushCredentials(context, baseUrl, access, refresh)
            }
        }
    }

    suspend fun saveTheme(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun saveFrontNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FRONT_NOTIFICATION] = enabled }
    }

    suspend fun saveAppLock(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK] = enabled }
    }

    suspend fun clearTokens() {
        context.dataStore.edit {
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
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
}
