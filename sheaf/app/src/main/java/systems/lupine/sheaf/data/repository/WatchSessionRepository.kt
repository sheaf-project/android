package systems.lupine.sheaf.data.repository

import android.util.Log
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
     * watch session (e.g. a 401 came back from a watch-bound request,
     * or the watch sent a credentials-request message because its
     * cached tokens stopped working).
     */
    suspend fun ensureWatchSession(force: Boolean = false): Boolean = mintMutex.withLock {
        val cached = prefs.watchAccessToken.first()
        if (!force && cached != null) {
            Log.i(TAG, "ensureWatchSession: cached token present, skipping mint")
            return@withLock true
        }
        if (force) {
            Log.i(TAG, "ensureWatchSession: force=true, dropping cached tokens before mint")
            prefs.clearWatchTokens()
        }
        val phoneAccess = prefs.accessToken.first()
        if (phoneAccess.isNullOrBlank()) {
            Log.w(TAG, "ensureWatchSession: no phone access token, cannot mint")
            return@withLock false
        }

        return@withLock try {
            Log.i(TAG, "ensureWatchSession: minting via POST /v1/auth/sessions/secondary")
            val response = api.createSecondarySession(
                SecondarySessionRequest(clientName = "Sheaf wear OS")
            )
            prefs.saveWatchTokens(
                access = response.accessToken,
                refresh = response.refreshToken,
                sessionId = response.sessionId,
            )
            Log.i(TAG, "ensureWatchSession: mint succeeded, sessionId=${response.sessionId}")
            true
        } catch (e: Exception) {
            // Best-effort: if the server is unreachable or the endpoint
            // returns an error, leave watch credentials empty. The next
            // tile/screen that needs them will retry, and the watch will
            // continue to show "Open Sheaf on phone" in the meantime.
            Log.w(TAG, "ensureWatchSession: mint failed", e)
            false
        }
    }

    private companion object {
        const val TAG = "SheafPairing"
    }
}
