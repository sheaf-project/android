package systems.lupine.sheaf.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.AuthInterceptor
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AuthConfig
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
    // Registration succeeded but email must be verified before proceeding
    data object AwaitingEmailVerification : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val authInterceptor: AuthInterceptor,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = prefs.accessToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val authConfig: StateFlow<AuthConfig?> = baseUrl
        .filter { it.isNotBlank() }
        .flatMapLatest { flow { emit(runCatching { api.getAuthConfig() }.getOrNull()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
                    pendingAccessToken = tokens.accessToken
                    pendingRefreshToken = tokens.refreshToken
                    // Use the in-memory override so AuthInterceptor can attach the token
                    // for getMe() without writing to DataStore (which would flip isLoggedIn).
                    authInterceptor.pendingToken = tokens.accessToken

                    val user = runCatching { api.getMe() }.getOrNull()
                    when {
                        user?.totpEnabled == true -> {
                            _uiState.value = AuthUiState.AwaitingTotp
                        }
                        user?.emailVerified == false && authConfig.value?.emailVerification != "none" -> {
                            _uiState.value = AuthUiState.AwaitingEmailVerification
                        }
                        else -> {
                            finishAuth()
                        }
                    }
                }
                .onFailure { e ->
                    authInterceptor.pendingToken = null
                    val message = if (e is HttpException && e.code() == 401)
                        "Invalid email or password"
                    else
                        e.message ?: "Login failed"
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun submitTotp(code: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            // pendingToken is already set from login() — no prefs write needed
            runCatching { api.verifyTotp(TOTPVerify(code)) }
                .onSuccess { finishAuth() }
                .onFailure { e ->
                    val message = if (e is HttpException && e.code() == 422)
                        "Invalid code — please try again"
                    else
                        e.message ?: "TOTP verification failed"
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun register(email: String, password: String, inviteCode: String? = null) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.register(UserRegister(email, password, inviteCode?.ifBlank { null })) }
                .onSuccess { tokens ->
                    if (authConfig.value?.emailVerification != "none") {
                        // Hold tokens in memory — don't persist so isLoggedIn stays false
                        pendingAccessToken = tokens.accessToken
                        pendingRefreshToken = tokens.refreshToken
                        _uiState.value = AuthUiState.AwaitingEmailVerification
                    } else {
                        prefs.saveTokens(tokens.accessToken, tokens.refreshToken)
                        _uiState.value = AuthUiState.Idle
                    }
                }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") }
        }
    }

    fun verifyEmail(token: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            // verifyEmail is a public endpoint — no auth needed
            runCatching { api.verifyEmail(token) }
                .onSuccess { finishAuth() }
                .onFailure { e ->
                    val message = if (e is HttpException && e.code() == 400)
                        "Invalid or expired token"
                    else
                        e.message ?: "Verification failed"
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            // pendingToken already set — AuthInterceptor will attach it without prefs write
            runCatching { api.resendVerification() }
            _uiState.value = AuthUiState.AwaitingEmailVerification
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { api.logout() }
            prefs.clearTokens()
            clearPending()
        }
    }

    fun cancelTotp() {
        clearPending()
        _uiState.value = AuthUiState.Idle
    }

    fun cancelEmailVerification() {
        clearPending()
        _uiState.value = AuthUiState.Idle
    }

    private suspend fun finishAuth() {
        val access = pendingAccessToken ?: return
        val refresh = pendingRefreshToken ?: return
        prefs.saveTokens(access, refresh)
        clearPending()
        _uiState.value = AuthUiState.Idle
    }

    private fun clearPending() {
        pendingAccessToken = null
        pendingRefreshToken = null
        authInterceptor.pendingToken = null
    }

    fun clearError() { _uiState.value = AuthUiState.Idle }
}
