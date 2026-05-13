package systems.lupine.sheaf.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.RedeemRequest
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.push.PushChannelSync
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

sealed interface RedeemUiState {
    data object Loading : RedeemUiState
    data class Success(
        val channelName: String,
        val systemLabel: String?,
    ) : RedeemUiState
    data class Error(val message: String, val needsLogin: Boolean = false) : RedeemUiState
}

@HiltViewModel
class RedeemNotificationViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val pushChannelSync: PushChannelSync,
) : ViewModel() {

    private val _state = MutableStateFlow<RedeemUiState>(RedeemUiState.Loading)
    val state: StateFlow<RedeemUiState> = _state.asStateFlow()

    fun redeem(activationCode: String) {
        viewModelScope.launch {
            _state.value = RedeemUiState.Loading
            runCatching {
                api.redeemActivationCode(RedeemRequest(activationCode = activationCode))
            }
                .onSuccess { resp ->
                    // New subscription -> new Android NotificationChannel
                    // entry so the user can tune importance for this Sheaf
                    // channel independently before the first push arrives.
                    runCatching { pushChannelSync.sync() }
                    _state.value = RedeemUiState.Success(
                        channelName = resp.channelName,
                        systemLabel = resp.systemLabel,
                    )
                }
                .onFailure { e ->
                    // 401 here doesn't necessarily mean the user is signed
                    // out — the redeem endpoint can reject otherwise-valid
                    // Bearer auth if the server's session-binding path
                    // doesn't fire. needsLogin only flips when DataStore
                    // genuinely has no access token, so the UI doesn't
                    // shove a signed-in user toward the login screen.
                    val code = (e as? HttpException)?.code()
                    val hasToken = !prefs.accessToken.firstOrNull().isNullOrBlank()
                    val needsLogin = code == 401 && !hasToken
                    val message = when {
                        code == 401 && hasToken ->
                            "Couldn't redeem subscription. The server rejected the request even though you're signed in — please report this if it keeps happening."
                        needsLogin -> "Sign in to receive notifications on this device"
                        code == 404 ->
                            "This subscription link has expired or already been redeemed"
                        code == 410 ->
                            "This subscription link has already been redeemed"
                        code == 400 ->
                            "This link is for a subscription type the app can't handle"
                        else -> e.toUserMessage("Couldn't redeem subscription")
                    }
                    _state.value = RedeemUiState.Error(message, needsLogin = needsLogin)
                }
        }
    }
}
