package systems.lupine.sheaf.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AuthConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportUiState(
    val isLoading: Boolean = true,
    val config: AuthConfig? = null,
)

/**
 * Backs the Support screen. Pulls the operator's public config (support
 * contact, status page, policy links) from the same `/v1/auth/config`
 * endpoint the login screen uses; everything there is optional.
 */
@HiltViewModel
class SupportViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val config = runCatching { api.getAuthConfig() }.getOrNull()
            _state.value = SupportUiState(isLoading = false, config = config)
        }
    }
}
