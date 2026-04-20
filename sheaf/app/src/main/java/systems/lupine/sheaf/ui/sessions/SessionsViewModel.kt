package systems.lupine.sheaf.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SessionRead
import systems.lupine.sheaf.data.model.SessionUpdate
import systems.lupine.sheaf.data.model.TokenRefresh
import systems.lupine.sheaf.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class SessionsUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionRead> = emptyList(),
    val error: String? = null,
    val isRevoking: Boolean = false,
)

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
            runCatching { api.listSessions() }
                .onSuccess { sessions -> _state.update { it.copy(isLoading = false, sessions = sessions) } }
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

    fun revokeOtherSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isRevoking = true, error = null) }
            val refreshToken = prefs.refreshToken.firstOrNull()
            if (refreshToken == null) {
                _state.update { it.copy(isRevoking = false, error = "Not signed in") }
                return@launch
            }
            runCatching { api.revokeOtherSessions(TokenRefresh(refreshToken)) }
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(isRevoking = false, error = e.toUserMessage("Failed to revoke sessions")) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
