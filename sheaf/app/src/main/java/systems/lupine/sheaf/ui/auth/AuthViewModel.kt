package systems.lupine.sheaf.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.AltchaSolver
import systems.lupine.sheaf.data.api.AuthInterceptor
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.sameConfiguredOrigin
import kotlinx.coroutines.flow.firstOrNull
import systems.lupine.sheaf.data.model.AuthConfig
import systems.lupine.sheaf.data.model.TokenResponse
import systems.lupine.sheaf.data.model.UserLogin
import systems.lupine.sheaf.data.model.UserRegister
import systems.lupine.sheaf.data.repository.AccountDataWiper
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.ui.components.resolveDisplayZoneId
import java.time.ZoneId
import systems.lupine.sheaf.data.repository.WatchSessionRepository
import systems.lupine.sheaf.datalayer.PhoneDataLayerService
import systems.lupine.sheaf.push.PushDeviceRegistrar
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    // Registration succeeded but email must be verified before proceeding.
    // Carries its own error/notice so a failed resend can be reported without
    // flipping to Error, which would drop the user back to the login form.
    data class AwaitingEmailVerification(
        val error: String? = null,
        val resent: Boolean = false,
    ) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val authInterceptor: AuthInterceptor,
    private val altchaSolver: AltchaSolver,
    private val watchSession: WatchSessionRepository,
    private val pushRegistrar: PushDeviceRegistrar,
    private val accountDataWiper: AccountDataWiper,
    @ApplicationContext private val appContext: Context,
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

    // The instance's file CDN base, persisted from the auth config. Surfaced so
    // the app root can provide it to LocalFileCdnBase for hosted/external image
    // classification.
    val fileCdnBase: StateFlow<String?> = prefs.fileCdnBase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The resolved display timezone (account default shadowed by any device
    // override). Provided app-wide via LocalDisplayTimeZone so every timestamp
    // renders in the same zone.
    val effectiveDisplayZone: StateFlow<ZoneId> =
        combine(prefs.accountTimezone, prefs.timezoneOverride) { account, override ->
            resolveDisplayZoneId(account, override)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneId.systemDefault())

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

        // Refresh the cached account timezone on each login so the app-wide
        // display zone reflects a change made on another device. Cached to
        // prefs so the zone is available at startup / offline; settings writes
        // update it directly for an instant local reflection.
        viewModelScope.launch {
            isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    runCatching { api.getOwnSystem() }
                        .onSuccess { prefs.saveAccountTimezone(it.timezone) }
                }
            }
        }
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Set when registration completes so the post-login navigation can route
    // the user through onboarding rather than straight to home. Cleared once
    // the user finishes (or skips) onboarding.
    private val _pendingOnboarding = MutableStateFlow(false)
    val pendingOnboarding: StateFlow<Boolean> = _pendingOnboarding.asStateFlow()

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
        viewModelScope.launch {
            val previous = prefs.baseUrl.firstOrNull()
            prefs.saveBaseUrl(url)
            // Switching to a different instance must drop any session bound to
            // the old one: otherwise the previous instance's tokens, cache and
            // offline queue survive against the new host (and its bearer would
            // ride to the new server).
            if (!sameConfiguredOrigin(previous, url)) {
                authInterceptor.pendingToken = null
                prefs.clearTokens()
                accountDataWiper.wipe()
            }
        }
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

    fun submitTotp(code: String, rememberDevice: Boolean = false) {
        val email = pendingEmail ?: return
        val password = pendingPassword ?: return
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching {
                api.login(
                    UserLogin(
                        email = email,
                        password = password,
                        totpCode = code,
                        captcha = pendingCaptcha,
                        rememberDevice = rememberDevice,
                    )
                )
            }
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
                _uiState.value = AuthUiState.AwaitingEmailVerification()
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
                    _pendingOnboarding.value = true
                    if (config?.emailVerification != "none") {
                        // Hold tokens in memory, don't persist, so isLoggedIn stays false.
                        // pendingToken still has to be set: resend-verification is an
                        // authenticated endpoint, and without this the freshly registered
                        // user's "Resend Email" went out with no bearer and 401'd.
                        authInterceptor.pendingToken = tokens.accessToken
                        pendingAccessToken = tokens.accessToken
                        pendingRefreshToken = tokens.refreshToken
                        _uiState.value = AuthUiState.AwaitingEmailVerification()
                    } else {
                        // This branch commits a session directly instead of via
                        // finishAuth(), so wipe here too: without it a prior
                        // account's cache and offline queue survive into the new
                        // account (finishAuth is the only other path that wipes).
                        accountDataWiper.wipe()
                        prefs.saveTokens(tokens.accessToken, tokens.refreshToken)
                        runCatching {
                            PhoneDataLayerService.pushWatchCredentials(
                                appContext, prefs, watchSession
                            )
                        }
                        runCatching { pushRegistrar.registerCurrentToken() }
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
            // pendingToken is set by both the login and the register path, so
            // AuthInterceptor attaches it without a prefs write.
            runCatching { api.resendVerification() }
                .onSuccess { _uiState.value = AuthUiState.AwaitingEmailVerification(resent = true) }
                .onFailure { e ->
                    _uiState.value = AuthUiState.AwaitingEmailVerification(
                        error = e.toUserMessage("Couldn't resend the verification email"),
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Drop the push device row server-side before the session goes
            // away. Best-effort: the DELETE endpoint requires auth, so this
            // has to run before prefs.clearTokens(). If it fails (network,
            // etc.) the row gets reaped lazily on the next delivery via the
            // 410 / Unregistered path.
            runCatching { pushRegistrar.unregisterCurrent() }
            runCatching { api.logout() }
            prefs.clearTokens()
            // Wipe cached account data + the offline queue so the next account
            // to sign in on this device can't see them or replay them.
            accountDataWiper.wipe()
            // Delete the watch's credential DataItem so a paired watch drops the
            // session too and can't reload it after a restart.
            runCatching { PhoneDataLayerService.clearWatchCredentials(appContext) }
            // Trusted-device cookie deliberately persists across logout, same
            // as browser behaviour. It's a property of the device, not the
            // session. Revoke from the trusted-devices settings screen if you
            // need to force TOTP next login.
            clearPending()
            _pendingOnboarding.value = false
        }
    }

    fun cancelTotp() {
        clearPending()
        _uiState.value = AuthUiState.Idle
    }

    fun cancelEmailVerification() {
        clearPending()
        _pendingOnboarding.value = false
        _uiState.value = AuthUiState.Idle
    }

    fun completeOnboarding() {
        _pendingOnboarding.value = false
    }

    fun forceShowOnboarding() {
        _pendingOnboarding.value = true
    }

    private suspend fun finishAuth() {
        val access = pendingAccessToken ?: return
        val refresh = pendingRefreshToken ?: return
        // Purge any prior account's cache + offline queue before committing the
        // new session, so this login can't inherit another account's data or
        // replay its queued mutations. (Token refresh does not route here.)
        accountDataWiper.wipe()
        prefs.saveTokens(access, refresh)
        // Provision the wear app's companion session and push to the
        // watch. Best-effort: a wear-side error here doesn't block the
        // user from logging in on the phone.
        runCatching {
            PhoneDataLayerService.pushWatchCredentials(appContext, prefs, watchSession)
        }
        // Register the current FCM token against this account so the
        // server has somewhere to deliver pushes to. Best-effort.
        runCatching { pushRegistrar.registerCurrentToken() }
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
