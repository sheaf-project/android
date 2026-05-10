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
import systems.lupine.sheaf.data.model.NotificationChannelCreate
import systems.lupine.sheaf.data.model.NotificationChannelRead
import systems.lupine.sheaf.data.model.WatchTokenCreate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ChannelsUiState(
    val isLoading: Boolean = false,
    val channels: List<NotificationChannelRead> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ChannelsYouOwnViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState(isLoading = true))
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listOwnedChannels() }
                .onSuccess { rows ->
                    _state.update { it.copy(isLoading = false, channels = rows) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load channels"))
                    }
                }
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            runCatching { api.deleteChannel(channelId) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete channel")) }
                }
        }
    }

    fun toggleEnabled(channel: NotificationChannelRead) {
        viewModelScope.launch {
            val call = if (channel.destinationState.equals("disabled", ignoreCase = true)) {
                suspend { api.enableChannel(channel.id) }
            } else {
                suspend { api.disableChannel(channel.id) }
            }
            runCatching { call() }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't update channel")) }
                }
        }
    }
}

data class ChannelDetailUiState(
    val isLoading: Boolean = false,
    val channel: NotificationChannelRead? = null,
    val reissuedActivationUrl: String? = null,
    val reissuedExpiresAt: String? = null,
    val isReissuing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelDetailUiState(isLoading = true))
    val state: StateFlow<ChannelDetailUiState> = _state.asStateFlow()

    fun load(channelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // Channel list is small; just filter from the same endpoint
            // rather than wiring a single-channel GET. Saves one route on
            // the client side and stays consistent with whatever the
            // list view is showing.
            runCatching { api.listOwnedChannels() }
                .onSuccess { rows ->
                    val match = rows.firstOrNull { it.id == channelId }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            channel = match,
                            error = if (match == null) "Channel not found" else null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load channel"))
                    }
                }
        }
    }

    fun reissueActivation(channelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isReissuing = true, error = null) }
            runCatching { api.reissueChannelActivation(channelId) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isReissuing = false,
                            reissuedActivationUrl = resp.activationUrl,
                            reissuedExpiresAt = resp.activationExpiresAt,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isReissuing = false,
                            error = e.toUserMessage("Couldn't reissue activation"),
                        )
                    }
                }
        }
    }

    fun toggleEnabled() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            val call = if (channel.destinationState.equals("disabled", ignoreCase = true)) {
                suspend { api.enableChannel(channel.id) }
            } else {
                suspend { api.disableChannel(channel.id) }
            }
            runCatching { call() }
                .onSuccess { load(channel.id) }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't update channel")) }
                }
        }
    }

    fun delete(channelId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { api.deleteChannel(channelId) }
                .onSuccess { onDeleted() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete channel")) }
                }
        }
    }

    fun dismissReissuedUrl() {
        _state.update { it.copy(reissuedActivationUrl = null, reissuedExpiresAt = null) }
    }
}

data class CreateChannelUiState(
    val isSubmitting: Boolean = false,
    val createdChannelName: String? = null,
    val activationUrl: String? = null,
    val activationExpiresAt: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CreateChannelViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateChannelUiState())
    val state: StateFlow<CreateChannelUiState> = _state.asStateFlow()

    fun create(
        name: String,
        recipientLabel: String?,
        destinationType: String,
        triggerOnStart: Boolean,
        triggerOnStop: Boolean,
        triggerOnCofrontChange: Boolean,
    ) {
        viewModelScope.launch {
            _state.update { CreateChannelUiState(isSubmitting = true) }
            runCatching {
                val system = api.getOwnSystem()
                val token = api.createWatchToken(
                    system.id,
                    WatchTokenCreate(label = recipientLabel?.takeIf { it.isNotBlank() }),
                )
                api.createChannel(
                    token.id,
                    NotificationChannelCreate(
                        name = name,
                        destinationType = destinationType,
                        triggerOnStart = triggerOnStart,
                        triggerOnStop = triggerOnStop,
                        triggerOnCofrontChange = triggerOnCofrontChange,
                    ),
                )
            }
                .onSuccess { resp ->
                    _state.update {
                        CreateChannelUiState(
                            createdChannelName = resp.channel.name,
                            activationUrl = resp.activationUrl,
                            activationExpiresAt = resp.activationExpiresAt,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        CreateChannelUiState(error = e.toUserMessage("Couldn't create channel"))
                    }
                }
        }
    }

    fun reset() { _state.value = CreateChannelUiState() }
}
