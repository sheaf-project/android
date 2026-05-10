package systems.lupine.sheaf.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ReceivingChannelView
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ReceivingUiState(
    val isLoading: Boolean = false,
    val channels: List<ReceivingChannelView> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ReceivingViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceivingUiState(isLoading = true))
    val state: StateFlow<ReceivingUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listReceivingChannels() }
                .onSuccess { channels ->
                    _state.update { it.copy(isLoading = false, channels = channels) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load subscriptions"))
                    }
                }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            runCatching { api.unsubscribeReceiving(channelId) }
                .onSuccess {
                    // Optimistically drop the row; server has marked it disabled.
                    _state.update { s -> s.copy(channels = s.channels.filterNot { it.channelId == channelId }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't unsubscribe")) }
                }
        }
    }
}
