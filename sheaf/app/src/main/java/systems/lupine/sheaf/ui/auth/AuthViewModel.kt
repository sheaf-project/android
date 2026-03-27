package systems.lupine.sheaf.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.TOTPVerify
import systems.lupine.sheaf.data.model.UserLogin
import systems.lupine.sheaf.data.model.UserRegister
import systems.lupine.sheaf.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    // Login succeeded but TOTP code is required to complete auth
    data object AwaitingTotp : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = prefs.accessToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Holds the temporary access token issued before TOTP verification
    private var pendingAccessToken: String? = null
    private var pendingRefreshToken: String? = null

    fun saveBaseUrl(url: String) {
        viewModelScope.launch { prefs.saveBaseUrl(url) }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.login(UserLogin(email, password)) }
                .onSuccess { tokens ->
                    // Check if TOTP is required by calling /auth/me with this token.
                    // Save tokens temporarily, then check totp_enabled.
                    pendingAccessToken = tokens.accessToken
                    pendingRefreshToken = tokens.refreshToken
                    prefs.saveTokens(tokens.accessToken, tokens.refreshToken)

                    val user = runCatching { api.getMe() }.getOrNull()
                    if (user?.totpEnabled == true) {
                        // Don't mark as fully logged in yet — wait for TOTP
                        prefs.clearTokens()
                        _uiState.value = AuthUiState.AwaitingTotp
                    } else {
                        // No TOTP — fully logged in
                        _uiState.value = AuthUiState.Idle
                    }
                }
                .onFailure { e ->
                    val message = if (e is HttpException && e.code() == 401)
                        "Invalid email or password"
                    else
                        e.message ?: "Login failed"
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun submitTotp(code: String) {
        val access  = pendingAccessToken  ?: return
        val refresh = pendingRefreshToken ?: return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            // Temporarily save the token so AuthInterceptor can attach it
            prefs.saveTokens(access, refresh)
            runCatching { api.verifyTotp(TOTPVerify(code)) }
                .onSuccess {
                    // TOTP verified — tokens are already saved, mark as done
                    pendingAccessToken  = null
                    pendingRefreshToken = null
                    _uiState.value = AuthUiState.Idle
                }
                .onFailure { e ->
                    // Clear tokens again — user must retry
                    prefs.clearTokens()
                    val message = if (e is HttpException && e.code() == 422)
                        "Invalid code — please try again"
                    else
                        e.message ?: "TOTP verification failed"
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.register(UserRegister(email, password)) }
                .onSuccess { tokens ->
                    prefs.saveTokens(tokens.accessToken, tokens.refreshToken)
                    _uiState.value = AuthUiState.Idle
                }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            prefs.clearTokens()
            pendingAccessToken  = null
            pendingRefreshToken = null
        }
    }

    fun cancelTotp() {
        pendingAccessToken  = null
        pendingRefreshToken = null
        _uiState.value = AuthUiState.Idle
    }

    fun clearError() { _uiState.value = AuthUiState.Idle }
}
