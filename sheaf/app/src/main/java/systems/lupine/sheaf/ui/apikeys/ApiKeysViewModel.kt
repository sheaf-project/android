package systems.lupine.sheaf.ui.apikeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ApiKeyCreate
import systems.lupine.sheaf.data.model.ApiKeyCreated
import systems.lupine.sheaf.data.model.ApiKeyRead
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ApiKeysUiState(
    val isLoading: Boolean = false,
    val keys: List<ApiKeyRead> = emptyList(),
    val error: String? = null,
    val isCreating: Boolean = false,
    val createdKey: ApiKeyCreated? = null,
)

val ALL_SCOPES = listOf(
    "members:read",
    "members:write",
    "fronts:read",
    "fronts:write",
    "groups:read",
    "groups:write",
    "fields:read",
    "fields:write",
    "system:read",
    "system:write",
)

@HiltViewModel
class ApiKeysViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ApiKeysUiState())
    val state: StateFlow<ApiKeysUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listApiKeys() }
                .onSuccess { keys -> _state.update { it.copy(isLoading = false, keys = keys) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load API keys")) } }
        }
    }

    fun createKey(name: String, scopes: List<String>, expiresAt: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            runCatching { api.createApiKey(ApiKeyCreate(name, scopes, expiresAt)) }
                .onSuccess { created -> _state.update { it.copy(isCreating = false, createdKey = created) } }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 422) "Invalid key configuration"
                              else e.toUserMessage("Failed to create API key")
                    _state.update { it.copy(isCreating = false, error = msg) }
                }
        }
    }

    fun revokeKey(id: String) {
        viewModelScope.launch {
            runCatching { api.revokeApiKey(id) }
                .onSuccess { _state.update { it.copy(keys = it.keys.filter { k -> k.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to revoke key")) } }
        }
    }

    fun clearCreatedKey() {
        _state.update { it.copy(createdKey = null) }
        load()
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
