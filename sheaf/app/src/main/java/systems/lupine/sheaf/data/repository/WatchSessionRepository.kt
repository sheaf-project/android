package systems.lupine.sheaf.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SecondarySessionRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the wear app's companion session: a child of the phone's
 * primary session, minted via `POST /v1/auth/sessions/secondary`. The
 * watch keeps its own one-shot refresh JWT and rotates it independently
 * of the phone's, so a refresh on either device can't trip the other's
 * reuse-detection path.
 *
 * Idempotent: returns immediately if watch credentials already exist.
 * Concurrent callers (e.g. login completion + a watch credential
 * request firing back-to-back) coalesce on a single in-flight Mutex
 * so we never mint two parallel watch sessions.
 *
 * Server-side revocation cascade: when the parent session is revoked
 * (logout, /sessions DELETE, change-password) the child is revoked
 * too. We don't need to do anything special here — `clearTokens`
 * already wipes the locally-stored watch creds, and the cascade
 * handles the server.
 */
@Singleton
class WatchSessionRepository @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
) {
    private val mintMutex = Mutex()

    /**
     * Ensures watch credentials exist locally. If absent, mints a new
     * companion session and stores the result. Returns true if watch
     * credentials are available after this call. [force] re-mints even
     * if credentials are already present; use after losing the existing
     * watch session (e.g. a 401 came back from a watch-bound request).
     */
    suspend fun ensureWatchSession(force: Boolean = false): Boolean = mintMutex.withLock {
        if (!force && prefs.watchAccessToken.first() != null) return@withLock true
        val phoneAccess = prefs.accessToken.first() ?: return@withLock false
        if (phoneAccess.isBlank()) return@withLock false

        return@withLock try {
            val response = api.createSecondarySession(
                SecondarySessionRequest(clientName = "Sheaf wear OS")
            )
            prefs.saveWatchTokens(
                access = response.accessToken,
                refresh = response.refreshToken,
                sessionId = response.sessionId,
            )
            true
        } catch (_: Exception) {
            // Best-effort: if the server is unreachable or the endpoint
            // returns an error, leave watch credentials empty. The next
            // tile/screen that needs them will retry, and the watch will
            // continue to show "Open Sheaf on phone" in the meantime.
            false
        }
    }
}
