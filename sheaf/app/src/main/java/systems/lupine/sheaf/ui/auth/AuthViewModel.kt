package systems.lupine.sheaf.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.AltchaSolver
import systems.lupine.sheaf.data.api.AuthInterceptor
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AuthConfig
import systems.lupine.sheaf.data.model.TokenResponse
import systems.lupine.sheaf.data.model.UserLogin
import systems.lupine.sheaf.data.model.UserRegister
import systems.lupine.sheaf.data.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    // Solving a proof-of-work captcha. Distinct from Loading so the UI can
    // surface what's happening during the multi-second PBKDF2 solve.
    data object SolvingCaptcha : AuthUiState
    // Login succeeded but TOTP code is required to complete auth
    data class AwaitingTotp(val error: String? = null) : AuthUiState
    // Registration succeeded but email must be verified before proceeding
    data object AwaitingEmailVerification : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val authInterceptor: AuthInterceptor,
    private val altchaSolver: AltchaSolver,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = prefs.accessToken
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val cfClientId: StateFlow<String> = prefs.cfClientId
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val authConfig: StateFlow<AuthConfig?> = baseUrl
        .filter { it.isNotBlank() }
        .flatMapLatest { flow { emit(runCatching { api.getAuthConfig() }.getOrNull()) } }
        .onEach { config -> if (config != null) prefs.saveFileCdnBase(config.fileCdnBase) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Keep authConfig subscribed so `file_cdn_base` is persisted even when
        // no UI is observing it — the image interceptor reads it from prefs.
        viewModelScope.launch { authConfig.collect { } }
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Holds tokens during email-verification hold
    private var pendingAccessToken: String? = null
    private var pendingRefreshToken: String? = null
    // Holds credentials while waiting for the user to supply a TOTP code
    private var pendingEmail: String? = null
    private var pendingPassword: String? = null
    // Altcha payloads are valid for the server-side TTL (600s) and aren't
    // single-use, so we reuse the login-time solution when the user then
    // submits their TOTP code instead of solving the PoW a second time.
    private var pendingCaptcha: String? = null

    fun saveBaseUrl(url: String) {
        viewModelScope.launch { prefs.saveBaseUrl(url) }
    }

    fun saveCfTokens(clientId: String, clientSecret: String) {
        viewModelScope.launch { prefs.saveCfTokens(clientId, clientSecret) }
    }

    fun clearCfTokens() {
        viewModelScope.launch { prefs.clearCfTokens() }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            val captcha = if (authConfig.value?.captchaOnLogin == true) {
                _uiState.value = AuthUiState.SolvingCaptcha
                solveCaptcha() ?: run {
                    _uiState.value = AuthUiState.Error("Couldn't complete captcha — please try again")
                    return@launch
                }
            } else null
            pendingCaptcha = captcha
            _uiState.value = AuthUiState.Loading
            runCatching { api.login(UserLogin(email, password, captcha = captcha)) }
                .onSuccess { tokens -> handleLoginSuccess(tokens) }
                .onFailure { e ->
                    authInterceptor.pendingToken = null
                    // Server signals TOTP is required by rejecting the login with a specific detail
                    if (e is HttpException) {
                        val body = e.response()?.errorBody()?.string()
                        if (!body.isNullOrEmpty() && "TOTP code required" in body) {
                            pendingEmail = email
                            pendingPassword = password
                            _uiState.value = AuthUiState.AwaitingTotp()
                            return@launch
                        }
                    }
                    pendingCaptcha = null
                    val message = if (e is HttpException && e.code() == 401)
                        "Invalid email or password"
                    else
                        e.toUserMessage("Login failed")
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    fun submitTotp(code: String) {
        val email = pendingEmail ?: return
        val password = pendingPassword ?: return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.login(UserLogin(email, password, totpCode = code, captcha = pendingCaptcha)) }
                .onSuccess { tokens -> handleLoginSuccess(tokens) }
                .onFailure { e ->
                    authInterceptor.pendingToken = null
                    val message = when {
                        e is HttpException && (e.code() == 401 || e.code() == 422) ->
                            "Invalid code — please try again"
                        else -> e.toUserMessage("TOTP verification failed")
                    }
                    _uiState.value = AuthUiState.AwaitingTotp(error = message)
                }
        }
    }

    private suspend fun handleLoginSuccess(tokens: TokenResponse) {
        // Use pendingToken so AuthInterceptor can attach it for getMe() without writing
        // to DataStore, which would prematurely flip isLoggedIn.
        authInterceptor.pendingToken = tokens.accessToken
        val config = authConfig.value ?: runCatching { api.getAuthConfig() }.getOrNull()
        val user = runCatching { api.getMe() }.getOrNull()
        pendingAccessToken = tokens.accessToken
        pendingRefreshToken = tokens.refreshToken
        when {
            user?.emailVerified == false && config?.emailVerification != "none" ->
                _uiState.value = AuthUiState.AwaitingEmailVerification
            else ->
                finishAuth()
        }
    }

    fun register(email: String, password: String, inviteCode: String? = null) {
        viewModelScope.launch {
            val config = authConfig.value ?: runCatching { api.getAuthConfig() }.getOrNull()
            val captcha = if (config?.captchaProvider == "altcha") {
                _uiState.value = AuthUiState.SolvingCaptcha
                solveCaptcha() ?: run {
                    _uiState.value = AuthUiState.Error("Couldn't complete captcha — please try again")
                    return@launch
                }
            } else null
            _uiState.value = AuthUiState.Loading
            runCatching {
                api.register(UserRegister(email, password, inviteCode?.ifBlank { null }, captcha = captcha))
            }
                .onSuccess { tokens ->
                    if (config?.emailVerification != "none") {
                        // Hold tokens in memory — don't persist so isLoggedIn stays false
                        pendingAccessToken = tokens.accessToken
                        pendingRefreshToken = tokens.refreshToken
                        _uiState.value = AuthUiState.AwaitingEmailVerification
                    } else {
                        prefs.saveTokens(tokens.accessToken, tokens.refreshToken)
                        _uiState.value = AuthUiState.Idle
                    }
                }
                .onFailure { e ->
                    val message = when {
                        e is HttpException && e.code() == 409 -> "Email already registered"
                        e is HttpException && e.code() == 403 -> "Registration is not allowed"
                        e is HttpException && e.code() == 422 -> "Invalid email or password"
                        else -> e.toUserMessage("Registration failed")
                    }
                    _uiState.value = AuthUiState.Error(message)
                }
        }
    }

    private suspend fun solveCaptcha(): String? = runCatching {
        val challenge = api.getCaptchaChallenge()
        altchaSolver.solve(challenge)
    }.getOrNull()

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
                        e.toUserMessage("Verification failed")
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
        pendingEmail = null
        pendingPassword = null
        pendingCaptcha = null
        authInterceptor.pendingToken = null
    }

    fun clearError() { _uiState.value = AuthUiState.Idle }
}
