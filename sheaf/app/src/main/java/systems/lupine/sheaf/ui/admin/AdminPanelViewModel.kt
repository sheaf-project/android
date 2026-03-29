package systems.lupine.sheaf.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AdminAuthStatus
import systems.lupine.sheaf.data.model.AdminStats
import systems.lupine.sheaf.data.model.AdminStepUpVerify
import systems.lupine.sheaf.data.model.AdminUserRead
import systems.lupine.sheaf.data.model.AdminUserUpdate
import systems.lupine.sheaf.data.model.PendingUserRead
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class AdminPanelUiState(
    val isLoading: Boolean = false,
    val authStatus: AdminAuthStatus? = null,
    val stats: AdminStats? = null,
    val users: List<AdminUserRead> = emptyList(),
    val approvals: List<PendingUserRead> = emptyList(),
    val error: String? = null,
    val isSteppingUp: Boolean = false,
    val stepUpError: String? = null,
    val search: String = "",
    val maintenanceMessage: String? = null,
)

@HiltViewModel
class AdminPanelViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminPanelUiState())
    val state: StateFlow<AdminPanelUiState> = _state.asStateFlow()

    init { loadAuthStatus() }

    fun loadAuthStatus() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getAdminAuthStatus() }
                .onSuccess { status ->
                    _state.update { it.copy(isLoading = false, authStatus = status) }
                    if (status.verified) loadAll()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to check admin status") }
                }
        }
    }

    fun stepUp(password: String, totpCode: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSteppingUp = true, stepUpError = null) }
            runCatching {
                api.adminStepUp(AdminStepUpVerify(
                    password = password.ifBlank { null },
                    totpCode = totpCode.ifBlank { null },
                ))
            }
                .onSuccess {
                    _state.update { it.copy(isSteppingUp = false, authStatus = it.authStatus?.copy(verified = true)) }
                    loadAll()
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 401) "Invalid credentials"
                              else e.message ?: "Step-up authentication failed"
                    _state.update { it.copy(isSteppingUp = false, stepUpError = msg) }
                }
        }
    }

    private fun loadAll() {
        viewModelScope.launch {
            runCatching {
                val stats = api.getAdminStats()
                val users = api.getAdminUsers()
                val approvals = api.getApprovals()
                Triple(stats, users, approvals)
            }
                .onSuccess { (stats, users, approvals) ->
                    _state.update { it.copy(stats = stats, users = users, approvals = approvals) }
                }

                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to load admin data") }
                }
        }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
        viewModelScope.launch {
            runCatching { api.getAdminUsers(search = query.ifBlank { null }) }
                .onSuccess { users -> _state.update { it.copy(users = users) } }
        }
    }

    fun updateUser(id: String, update: AdminUserUpdate) {
        viewModelScope.launch {
            runCatching { api.updateAdminUser(id, update) }
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(users = s.users.map { if (it.id == id) updated else it })
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to update user") } }
        }
    }

    fun approveUser(id: String) {
        viewModelScope.launch {
            runCatching { api.approveUser(id) }
                .onSuccess { _state.update { it.copy(approvals = it.approvals.filter { u -> u.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to approve user") } }
        }
    }

    fun rejectUser(id: String) {
        viewModelScope.launch {
            runCatching { api.rejectUser(id) }
                .onSuccess { _state.update { it.copy(approvals = it.approvals.filter { u -> u.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed to reject user") } }
        }
    }

    fun runRetention() {
        viewModelScope.launch {
            runCatching { api.runRetention() }
                .onSuccess { _state.update { it.copy(maintenanceMessage = "Retention job started") } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed") } }
        }
    }

    fun runCleanup() {
        viewModelScope.launch {
            runCatching { api.runCleanup() }
                .onSuccess { _state.update { it.copy(maintenanceMessage = "Cleanup job started") } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed") } }
        }
    }

    fun runStorageAudit() {
        viewModelScope.launch {
            runCatching { api.getStorageStats() }
                .onSuccess { _state.update { it.copy(maintenanceMessage = "Storage stats refreshed") } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Failed") } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearMaintenanceMessage() { _state.update { it.copy(maintenanceMessage = null) } }
    fun clearStepUpError() { _state.update { it.copy(stepUpError = null) } }
}
