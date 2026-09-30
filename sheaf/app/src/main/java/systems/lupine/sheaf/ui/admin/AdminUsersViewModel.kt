package systems.lupine.sheaf.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AdminChangeEmailRequest
import systems.lupine.sheaf.data.model.AdminReasonBody
import systems.lupine.sheaf.data.model.AdminResetPasswordRequest
import systems.lupine.sheaf.data.model.AdminSuspendRequest
import systems.lupine.sheaf.data.model.AdminUserRead
import systems.lupine.sheaf.data.model.AdminUserUpdate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val users: List<AdminUserRead> = emptyList(),
    val search: String = "",
    val error: String? = null,
    val message: String? = null,
)

/**
 * The account list and everything done to a single account from it.
 *
 * Split out of [AdminPanelViewModel] along with the screen. Step-up is still
 * the panel's job: this is only reachable through it, and every call here
 * fails closed with the server's 401 if that ever stops being true.
 */
@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersUiState())
    val state: StateFlow<AdminUsersUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch { fetch(null) }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
        // Filtering happens server-side, so the old code spent one round trip
        // per keystroke: "alex" was four searches, three of them already stale
        // by the time they landed. Wait for a pause in typing instead.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            fetch(query.ifBlank { null })
        }
    }

    private suspend fun fetch(search: String?) {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { api.getAdminUsers(search = search) }
            .onSuccess { users -> _state.update { it.copy(isLoading = false, users = users) } }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load users")) }
            }
    }

    fun updateUser(id: String, update: AdminUserUpdate) {
        viewModelScope.launch {
            runCatching { api.updateAdminUser(id, update) }
                .onSuccess { updated ->
                    _state.update { s -> s.copy(users = s.users.map { if (it.id == id) updated else it }) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to update user")) } }
        }
    }

    // ── Recovery ────────────────────────────────────────────────────────────

    fun resetPassword(userId: String, reason: String, newPassword: String?) {
        viewModelScope.launch {
            runCatching {
                api.adminResetPassword(
                    userId,
                    AdminResetPasswordRequest(reason = reason, newPassword = newPassword?.ifBlank { null }),
                )
            }
                .onSuccess { _state.update { it.copy(message = "Password reset successfully") } }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 403) "Insufficient permissions"
                              else e.toUserMessage("Failed to reset password")
                    _state.update { it.copy(error = msg) }
                }
        }
    }

    fun changeEmail(userId: String, reason: String, newEmail: String) {
        viewModelScope.launch {
            runCatching { api.adminChangeEmail(userId, AdminChangeEmailRequest(reason = reason, newEmail = newEmail)) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            message = "Email changed to $newEmail",
                            users = s.users.map { if (it.id == userId) it.copy(email = newEmail) else it },
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 409) "Email already in use"
                              else e.toUserMessage("Failed to change email")
                    _state.update { it.copy(error = msg) }
                }
        }
    }

    fun disableTotp(userId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.adminDisableTotp(userId, AdminReasonBody(reason)) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            message = "TOTP disabled",
                            users = s.users.map { if (it.id == userId) it.copy(totpEnabled = false) else it },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to disable TOTP")) } }
        }
    }

    fun verifyEmail(userId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.adminVerifyEmail(userId, AdminReasonBody(reason)) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            message = "Email marked as verified",
                            users = s.users.map { if (it.id == userId) it.copy(emailVerified = true) else it },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to verify email")) } }
        }
    }

    fun cancelDeletion(userId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.adminCancelDeletion(userId, AdminReasonBody(reason)) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            message = "Account deletion cancelled",
                            users = s.users.map { if (it.id == userId) it.copy(accountStatus = "active") else it },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to cancel deletion")) } }
        }
    }

    // ── Moderation ──────────────────────────────────────────────────────────

    fun suspendUser(userId: String, reason: String, durationDays: Int?) {
        moderate(
            userId = userId,
            call = { api.adminSuspendUser(userId, AdminSuspendRequest(reason = reason, durationDays = durationDays)) },
            newStatus = "suspended",
            message = "Account suspended",
            failure = "Failed to suspend account",
        )
    }

    fun unsuspendUser(userId: String, reason: String) {
        moderate(
            userId = userId,
            call = { api.adminUnsuspendUser(userId, AdminReasonBody(reason)) },
            newStatus = "active",
            message = "Suspension lifted",
            failure = "Failed to lift suspension",
        )
    }

    fun banUser(userId: String, reason: String) {
        moderate(
            userId = userId,
            call = { api.adminBanUser(userId, AdminReasonBody(reason)) },
            newStatus = "banned",
            message = "Account banned",
            failure = "Failed to ban account",
        )
    }

    fun unbanUser(userId: String, reason: String) {
        moderate(
            userId = userId,
            call = { api.adminUnbanUser(userId, AdminReasonBody(reason)) },
            newStatus = "active",
            message = "Ban lifted",
            failure = "Failed to lift ban",
        )
    }

    // Shared shape for the four moderation actions: run the call, optimistically
    // reflect the new account_status in the loaded row, surface a result toast.
    private fun moderate(
        userId: String,
        call: suspend () -> Unit,
        newStatus: String,
        message: String,
        failure: String,
    ) {
        viewModelScope.launch {
            runCatching { call() }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            message = message,
                            users = s.users.map { if (it.id == userId) it.copy(accountStatus = newStatus) else it },
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 403) "Insufficient permissions"
                              else e.toUserMessage(failure)
                    _state.update { it.copy(error = msg) }
                }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
