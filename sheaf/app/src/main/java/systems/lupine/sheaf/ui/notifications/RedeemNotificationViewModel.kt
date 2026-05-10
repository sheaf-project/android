package systems.lupine.sheaf.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.RedeemRequest
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
                    _state.value = RedeemUiState.Success(
                        channelName = resp.channelName,
                        systemLabel = resp.systemLabel,
                    )
                }
                .onFailure { e ->
                    val needsLogin = e is HttpException && e.code() == 401
                    val message = when {
                        needsLogin -> "Sign in to receive notifications on this device"
                        e is HttpException && e.code() == 404 ->
                            "This subscription link has expired or already been redeemed"
                        e is HttpException && e.code() == 410 ->
                            "This subscription link has already been redeemed"
                        e is HttpException && e.code() == 400 ->
                            "This link is for a subscription type the app can't handle"
                        else -> e.toUserMessage("Couldn't redeem subscription")
                    }
                    _state.value = RedeemUiState.Error(message, needsLogin = needsLogin)
                }
        }
    }
}
