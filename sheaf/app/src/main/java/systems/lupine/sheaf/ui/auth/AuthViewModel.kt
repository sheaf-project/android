package systems.lupine.sheaf.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.TOTPVerify
import systems.lupine.sheaf.data.model.UserLogin
import systems.lupine.sheaf.data.model.UserRegister
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.datalayer.PhoneDataLayerService
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
    // Account requires email verification before use
    data object AwaitingEmailVerification : AuthUiState
    // Account is pending admin approval
    data object AccountPending : AuthUiState
    // Account was rejected by an admin
    data object AccountRejected : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    application: Application,
) : AndroidViewModel(application) {

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

    private fun pushToWatch() {
        viewModelScope.launch {
            val baseUrl = prefs.baseUrl.first() ?: return@launch
            val access  = prefs.accessToken.first() ?: return@launch
            val refresh = prefs.refreshToken.first() ?: return@launch
            PhoneDataLayerService.pushCredentials(getApplication(), baseUrl, access, refresh)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.login(UserLogin(email, password)) }
                .onSuccess { tokens ->
                    pendingAccessToken = tokens.accessToken
                    pendingRefreshToken = tokens.refreshToken
                    prefs.saveTokens(tokens.accessToken, tokens.refreshToken)

                    val user = runCatching { api.getMe() }.getOrNull()
                    when {
                        user?.totpEnabled == true -> {
                            prefs.clearTokens()
                            _uiState.value = AuthUiState.AwaitingTotp
                        }
                        user?.emailVerified == false -> {
                            _uiState.value = AuthUiState.AwaitingEmailVerification
                        }
                        user?.accountStatus == "pending" -> {
                            _uiState.value = AuthUiState.AccountPending
                        }
                        user?.accountStatus == "rejected" -> {
                            prefs.clearTokens()
                            _uiState.value = AuthUiState.AccountRejected
                        }
                        else -> {
                            pushToWatch()
                            _uiState.value = AuthUiState.Idle
                        }
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
                    pushToWatch()
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

    fun register(email: String, password: String, inviteCode: String? = null) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            runCatching { api.register(UserRegister(email, password, inviteCode?.ifBlank { null })) }
                .onSuccess { tokens ->
                    prefs.saveTokens(tokens.accessToken, tokens.refreshToken)
                    val user = runCatching { api.getMe() }.getOrNull()
                    when {
                        user?.emailVerified == false -> {
                            _uiState.value = AuthUiState.AwaitingEmailVerification
                        }
                        user?.accountStatus == "pending" -> {
                            _uiState.value = AuthUiState.AccountPending
                        }
                        else -> {
                            pushToWatch()
                            _uiState.value = AuthUiState.Idle
                        }
                    }
                }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Registration failed") }
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            runCatching { api.resendVerification() }
        }
    }

    fun checkAccountStatus() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val user = runCatching { api.getMe() }.getOrNull()
            when {
                user == null -> _uiState.value = AuthUiState.Error("Could not check status")
                user.emailVerified == false -> _uiState.value = AuthUiState.AwaitingEmailVerification
                user.accountStatus == "pending" -> _uiState.value = AuthUiState.AccountPending
                user.accountStatus == "rejected" -> {
                    prefs.clearTokens()
                    _uiState.value = AuthUiState.AccountRejected
                }
                else -> _uiState.value = AuthUiState.Idle
            }
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
