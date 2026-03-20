package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SystemRead
import systems.lupine.sheaf.data.model.TOTPSetupResponse
import systems.lupine.sheaf.data.model.TOTPVerify
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TotpStep { LOADING, SECRET, VERIFY, RECOVERY_CODES, DONE }

data class SettingsUiState(
    val user: UserRead? = null,
    val system: SystemRead? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val exportJson: String? = null,
    // TOTP
    val totpStep: TotpStep = TotpStep.LOADING,
    val totpSetupResponse: TOTPSetupResponse? = null,
    val totpError: String? = null,
    val totpIsVerifying: Boolean = false,
    val totpCopiedSecret: Boolean = false,
    val totpCopiedCodes: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(isLoading = true))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val themeMode: StateFlow<String> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val user = api.getMe()
                val system = api.getOwnSystem()
                user to system
            }.onSuccess { (user, system) ->
                _state.update { it.copy(user = user, system = system, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun saveBaseUrl(url: String) {
        viewModelScope.launch { prefs.saveBaseUrl(url) }
    }

    fun saveTheme(mode: String) {
        viewModelScope.launch { prefs.saveTheme(mode) }
    }

    fun exportData() {
        viewModelScope.launch {
            runCatching { api.exportAll() }
                .onSuccess { data ->
                    _state.update { it.copy(exportJson = data.toString()) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    // ── TOTP ──────────────────────────────────────────────────────────────────

    fun startTotpSetup() {
        _state.update { it.copy(totpStep = TotpStep.LOADING, totpError = null, totpSetupResponse = null) }
        viewModelScope.launch {
            runCatching { api.setupTotp() }
                .onSuccess { resp ->
                    _state.update { it.copy(totpSetupResponse = resp, totpStep = TotpStep.SECRET) }
                }
                .onFailure { e ->
                    _state.update { it.copy(totpError = e.message, totpStep = TotpStep.SECRET) }
                }
        }
    }

    fun advanceTotpToVerify() {
        _state.update { it.copy(totpStep = TotpStep.VERIFY, totpError = null) }
    }

    fun verifyTotp(code: String) {
        _state.update { it.copy(totpIsVerifying = true, totpError = null) }
        viewModelScope.launch {
            runCatching { api.verifyTotp(TOTPVerify(code)) }
                .onSuccess {
                    _state.update { it.copy(totpIsVerifying = false, totpStep = TotpStep.RECOVERY_CODES) }
                }
                .onFailure {
                    _state.update { it.copy(totpIsVerifying = false, totpError = "Incorrect code — please try again") }
                }
        }
    }

    fun advanceTotpToDone() {
        _state.update { it.copy(totpStep = TotpStep.DONE) }
        // Reload user to update totpEnabled status
        viewModelScope.launch {
            runCatching { api.getMe() }.onSuccess { user ->
                _state.update { it.copy(user = user) }
            }
        }
    }

    fun resetTotpSetup() {
        _state.update { it.copy(
            totpStep = TotpStep.LOADING,
            totpSetupResponse = null,
            totpError = null,
            totpIsVerifying = false,
            totpCopiedSecret = false,
            totpCopiedCodes = false,
        ) }
    }

    fun clearExport() { _state.update { it.copy(exportJson = null) } }
    fun clearError()  { _state.update { it.copy(error = null) } }
}
