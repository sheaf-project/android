package systems.lupine.sheaf.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ServerVersion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the instance says it is, fetched once and shared.
 *
 * Exists so a feature that has found itself unsupported can say which version
 * would support it and which one is running, rather than a bare "not
 * supported" that leaves somebody with nowhere to go. It is never the thing
 * that decides whether a feature is used - that is always detection, because a
 * version string cannot be trusted to describe a build off main.
 *
 * Cached for the session and keyed on the base URL, so switching instances
 * cannot leave the previous server's version on screen. A failure is not
 * cached: the endpoint is unauthenticated and cheap, and being offline once
 * should not mean the version stays unknown until the app restarts.
 */
@Singleton
class ServerInfoRepository @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
) {
    private val _version = MutableStateFlow<ServerVersion?>(null)
    val version: StateFlow<ServerVersion?> = _version.asStateFlow()

    private val mutex = Mutex()
    private var fetchedFor: String? = null

    /**
     * Fetch the version if it is not already known for the current server.
     * Safe to call often; does nothing after the first success per instance.
     */
    suspend fun ensureLoaded() {
        val baseUrl = prefs.baseUrl.first().orEmpty()
        mutex.withLock {
            if (fetchedFor == baseUrl && _version.value != null) return
            if (fetchedFor != baseUrl) _version.value = null
            val fetched = runCatching { api.getServerVersion() }.getOrNull()
            if (fetched != null) {
                _version.value = fetched
                fetchedFor = baseUrl
            }
        }
    }

    /** Drop what we know, so the next [ensureLoaded] asks again. */
    fun invalidate() {
        _version.value = null
        fetchedFor = null
    }
}
