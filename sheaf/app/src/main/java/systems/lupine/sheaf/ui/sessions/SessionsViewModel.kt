package systems.lupine.sheaf.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SessionRead
import systems.lupine.sheaf.data.model.SessionUpdate
import systems.lupine.sheaf.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.extractSessionId
import systems.lupine.sheaf.util.sessionHandle
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class SessionsUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionRead> = emptyList(),
    val error: String? = null,
    val isRevoking: Boolean = false,
)

/**
 * The sessions "revoke all others" should end: everything the server did not
 * mark as the caller's own.
 *
 * Filtered here rather than leaning on the server's refusal to revoke the
 * current session, so the request is never sent in the first place. Revoking
 * the current one would sign the user out of the phone they are holding.
 */
internal fun sessionsToRevoke(sessions: List<SessionRead>): List<SessionRead> =
    sessions.filterNot { it.isCurrent }

@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // The server's /v1/auth/sessions identifies the current session by
            // cookie, which our bearer-token client doesn't carry, so it always
            // returns is_current=false. Fall back to the JWT's `sid` claim,
            // hashed into the handle the list addresses sessions by: the raw id
            // is the cookie credential and is never returned, so comparing the
            // two directly matched nothing and left every row marked "other".
            val handle = extractSessionId(prefs.accessToken.firstOrNull())?.let { sessionHandle(it) }
            runCatching { api.listSessions() }
                .onSuccess { sessions ->
                    val tagged = if (handle != null) {
                        sessions.map { it.copy(isCurrent = it.id == handle) }
                    } else sessions
                    _state.update { it.copy(isLoading = false, sessions = tagged) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load sessions")) } }
        }
    }

    fun renameSession(id: String, nickname: String) {
        viewModelScope.launch {
            runCatching { api.renameSession(id, SessionUpdate(nickname)) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(sessions = s.sessions.map { if (it.id == id) it.copy(nickname = nickname) else it })
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to rename session")) } }
        }
    }

    fun revokeSession(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isRevoking = true, error = null) }
            runCatching { api.revokeSession(id) }
                .onSuccess { _state.update { it.copy(isRevoking = false, sessions = it.sessions.filter { s -> s.id != id }) } }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 400) "Cannot revoke the current session"
                              else e.toUserMessage("Failed to revoke session")
                    _state.update { it.copy(isRevoking = false, error = msg) }
                }
        }
    }

    /**
     * Revoke every session except this one, one at a time.
     *
     * Not `POST /auth/sessions/revoke-others`, which this used to call and
     * which can never work from here: it identifies the caller's session from
     * the `sheaf_session` cookie, and this client authenticates with a bearer
     * token and deliberately keeps only the trusted-device cookie. Every
     * attempt came back 400 "No current session", so the button has never
     * revoked anything.
     *
     * Revoking each handle individually is the same outcome by a route that
     * works with a bearer token. Which one is ours is already known: the server
     * cannot tell either, for the same cookie reason, so [load] re-tags
     * `isCurrent` from the access token's own `sid` claim.
     */
    fun revokeOtherSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isRevoking = true, error = null) }
            val others = sessionsToRevoke(_state.value.sessions)
            if (others.isEmpty()) {
                _state.update { it.copy(isRevoking = false) }
                return@launch
            }
            // Not atomic, so a failure part way leaves the rest revoked. That
            // is the right way round for this action: the point is to shut
            // other sessions out, and a partial result is still progress. The
            // reload shows exactly which survived.
            val failures = others.count { session ->
                runCatching { api.revokeSession(session.id) }.isFailure
            }
            // After the reload, not before: load() clears `error`, so setting
            // it first would report a partial failure to nobody.
            load()
            if (failures > 0) {
                _state.update {
                    it.copy(
                        error = "Couldn't revoke $failures of ${others.size} sessions. " +
                            "The rest were signed out.",
                    )
                }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
