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
import systems.lupine.sheaf.data.model.PushDeviceListEntry
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class YourDevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<PushDeviceListEntry> = emptyList(),
    val currentInstallId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class YourDevicesViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(YourDevicesUiState(isLoading = true))
    val state: StateFlow<YourDevicesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val installId = runCatching { prefs.getOrCreatePushInstallId() }.getOrNull()
            runCatching { api.listPushDevices() }
                .onSuccess { devices ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            devices = devices,
                            currentInstallId = installId,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.toUserMessage("Couldn't load your devices"),
                        )
                    }
                }
        }
    }
}
