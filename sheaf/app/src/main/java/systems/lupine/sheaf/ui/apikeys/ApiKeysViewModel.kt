package systems.lupine.sheaf.ui.apikeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ApiKeyCreate
import systems.lupine.sheaf.data.model.ApiKeyCreated
import systems.lupine.sheaf.data.model.ApiKeyRead
import systems.lupine.sheaf.data.model.UserRead
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ApiKeysUiState(
    val isLoading: Boolean = false,
    val keys: List<ApiKeyRead> = emptyList(),
    val user: UserRead? = null,
    val error: String? = null,
    val isCreating: Boolean = false,
    val createdKey: ApiKeyCreated? = null,
)

// One row in the scope matrix UI. `hasDelete=true` lets the row offer the
// Read+Write+Delete level; `readOnly`/`writeOnly` constrains the row to a
// single direction (e.g. export is read-only on the backend, import is
// write-only). `key` matches the resource prefix the backend's _VALID_SCOPES
// uses (sheaf/api/v1/auth.py).
data class ApiScopeResource(
    val key: String,
    val label: String,
    val hasDelete: Boolean = false,
    val readOnly: Boolean = false,
    val writeOnly: Boolean = false,
)

// Mirrors web's SCOPE_GROUPS layout, expressed for the matrix UI we render.
val SCOPE_GROUPS: List<Pair<String, List<ApiScopeResource>>> = listOf(
    "Data" to listOf(
        ApiScopeResource("members",  "Members",       hasDelete = true),
        ApiScopeResource("fronts",   "Fronts",        hasDelete = true),
        ApiScopeResource("groups",   "Groups",        hasDelete = true),
        ApiScopeResource("tags",     "Tags",          hasDelete = true),
        ApiScopeResource("fields",   "Custom fields", hasDelete = true),
        ApiScopeResource("journals", "Journals",      hasDelete = true),
    ),
    "Configuration" to listOf(
        ApiScopeResource("system",   "System"),
        ApiScopeResource("settings", "Client settings", hasDelete = true),
    ),
    "Notifications" to listOf(
        ApiScopeResource("notifications", "Notifications", hasDelete = true),
    ),
    "Import & Export" to listOf(
        ApiScopeResource("import", "Data import", writeOnly = true),
        ApiScopeResource("export", "Data export", readOnly = true),
    ),
)

val ALL_SCOPE_RESOURCES: List<ApiScopeResource> = SCOPE_GROUPS.flatMap { (_, rs) -> rs }

enum class ApiScopeLevel { NONE, READ, WRITE, DELETE }

// Project the per-resource level map down to the scope strings the backend
// expects. Write implies read (server-side), so we only emit the higher
// level. Delete level emits both write and delete.
fun scopesFromLevels(
    levels: Map<String, ApiScopeLevel>,
    isAdmin: Boolean,
    adminLevel: ApiScopeLevel,
): List<String> {
    val out = mutableListOf<String>()
    for (r in ALL_SCOPE_RESOURCES) {
        val lvl = levels[r.key] ?: ApiScopeLevel.NONE
        if (lvl == ApiScopeLevel.NONE) continue
        when {
            r.writeOnly -> out += "${r.key}:write"
            r.readOnly  -> out += "${r.key}:read"
            lvl == ApiScopeLevel.READ   -> out += "${r.key}:read"
            lvl == ApiScopeLevel.WRITE  -> out += "${r.key}:write"
            lvl == ApiScopeLevel.DELETE && r.hasDelete -> {
                out += "${r.key}:write"
                out += "${r.key}:delete"
            }
            lvl == ApiScopeLevel.DELETE -> out += "${r.key}:write" // fallback if hasDelete=false
        }
    }
    if (isAdmin && adminLevel != ApiScopeLevel.NONE) {
        out += if (adminLevel == ApiScopeLevel.WRITE) "admin:write" else "admin:read"
    }
    return out
}

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
            val user = runCatching { api.getMe() }.getOrNull()
            runCatching { api.listApiKeys() }
                .onSuccess { keys ->
                    _state.update { it.copy(isLoading = false, keys = keys, user = user ?: it.user) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load API keys"), user = user ?: it.user) } }
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
