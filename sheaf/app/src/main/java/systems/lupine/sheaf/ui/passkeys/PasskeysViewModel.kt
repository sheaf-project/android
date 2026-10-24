package systems.lupine.sheaf.ui.passkeys

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.PasskeyRead
import systems.lupine.sheaf.data.model.PasskeyRegisterBegin
import systems.lupine.sheaf.data.model.PasskeyRegisterComplete
import systems.lupine.sheaf.data.model.PasskeyUpdate
import systems.lupine.sheaf.data.model.RawJson
import systems.lupine.sheaf.passkey.PasskeyAuthenticator
import systems.lupine.sheaf.passkey.PasskeyException
import systems.lupine.sheaf.util.errorDetail
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class PasskeysUiState(
    val isLoading: Boolean = true,
    // False when the server can't offer passkeys at all (off, plain http, pre-1.7).
    val available: Boolean = true,
    val passkeys: List<PasskeyRead> = emptyList(),
    val totpEnabled: Boolean = false,
    val error: String? = null,
    val isAdding: Boolean = false,
    // Shown inside the add dialog so a wrong password doesn't close it.
    val addError: String? = null,
    // Bumped when an add finishes, so the screen knows to close the dialog.
    val addCompleted: Int = 0,
)

@HiltViewModel
class PasskeysViewModel @Inject constructor(
    private val api: SheafApiService,
    private val passkeys: PasskeyAuthenticator,
) : ViewModel() {

    private val _state = MutableStateFlow(PasskeysUiState())
    val state: StateFlow<PasskeysUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val available = runCatching { api.getAuthConfig().passkeysAvailable }.getOrDefault(true)
            if (!available) {
                _state.update { it.copy(isLoading = false, available = false) }
                return@launch
            }
            runCatching { api.listPasskeys() to api.getMe().totpEnabled }
                .onSuccess { (list, totp) ->
                    _state.update { it.copy(isLoading = false, passkeys = list, totpEnabled = totp) }
                }
                .onFailure { e ->
                    if (e is HttpException && e.code() == 404) {
                        _state.update { it.copy(isLoading = false, available = false) }
                    } else {
                        _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load passkeys")) }
                    }
                }
        }
    }

    /** [activity] hosts the system passkey sheet; it is not kept. */
    fun addPasskey(activity: Context, password: String, totpCode: String?, nickname: String) {
        viewModelScope.launch {
            _state.update { it.copy(isAdding = true, addError = null) }
            runCatching {
                val options = api.beginPasskeyRegistration(
                    PasskeyRegisterBegin(password, totpCode?.ifBlank { null })
                ).options
                val credential = passkeys.create(activity, options.json) ?: return@runCatching null
                api.completePasskeyRegistration(
                    PasskeyRegisterComplete(RawJson(credential), nickname.trim().ifBlank { null })
                )
            }
                .onSuccess { created ->
                    if (created == null) {
                        _state.update { it.copy(isAdding = false) }
                    } else {
                        _state.update {
                            it.copy(
                                isAdding = false,
                                passkeys = it.passkeys + created,
                                addCompleted = it.addCompleted + 1,
                            )
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(isAdding = false, addError = addMessage(e)) } }
        }
    }

    private fun addMessage(e: Throwable): String {
        if (e is PasskeyException) return e.message!!
        // A 401 here is the step-up prompt answering, so it keeps prompt wording.
        // Everything else (expired challenge, duplicate key, lockout) carries a
        // server detail that already says what to do next.
        if (e is HttpException && e.code() == 401) {
            return when (e.errorDetail()) {
                "Invalid password" -> "Incorrect password"
                "TOTP code required" -> "Enter your two-factor code"
                else -> "Invalid two-factor code"
            }
        }
        return e.toUserMessage("Couldn't add the passkey", preferDetail = true)
    }

    fun clearAddError() { _state.update { it.copy(addError = null) } }

    fun rename(id: String, nickname: String) {
        viewModelScope.launch {
            runCatching { api.renamePasskey(id, PasskeyUpdate(nickname.trim().ifBlank { null })) }
                .onSuccess { updated ->
                    _state.update { s -> s.copy(passkeys = s.passkeys.map { if (it.id == id) updated else it }) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to rename passkey")) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { api.deletePasskey(id) }
                .onSuccess { _state.update { s -> s.copy(passkeys = s.passkeys.filter { it.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to remove passkey")) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
