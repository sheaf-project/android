package systems.lupine.sheaf.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AdminAuthStatus
import systems.lupine.sheaf.data.model.AdminStats
import systems.lupine.sheaf.data.model.AdminStepUpVerify
import systems.lupine.sheaf.data.model.AnnouncementCreate
import systems.lupine.sheaf.data.model.BulkApproveRequest
import systems.lupine.sheaf.data.model.AnnouncementRead
import systems.lupine.sheaf.data.model.AnnouncementUpdate
import systems.lupine.sheaf.data.model.InviteCodeCreate
import systems.lupine.sheaf.data.model.InviteCodeRead
import systems.lupine.sheaf.data.model.PendingUserRead
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class AdminPanelUiState(
    val isLoading: Boolean = false,
    val authStatus: AdminAuthStatus? = null,
    val stats: AdminStats? = null,
    val approvals: List<PendingUserRead> = emptyList(),
    val invites: List<InviteCodeRead> = emptyList(),
    val announcements: List<AnnouncementRead> = emptyList(),
    val error: String? = null,
    val isSteppingUp: Boolean = false,
    val stepUpError: String? = null,
    val maintenanceMessage: String? = null,
    val isCreatingInvite: Boolean = false,
    val createInviteError: String? = null,
    val isSavingAnnouncement: Boolean = false,
    val announcementSaved: Boolean = false,
    val announcementError: String? = null,
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
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to check admin status")) }
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
                              else e.toUserMessage("Step-up authentication failed")
                    _state.update { it.copy(isSteppingUp = false, stepUpError = msg) }
                }
        }
    }

    private data class AdminData(
        val stats: AdminStats,
        val approvals: List<PendingUserRead>,
        val invites: List<InviteCodeRead>,
        val announcements: List<AnnouncementRead>,
    )

    private fun loadAll() {
        viewModelScope.launch {
            runCatching {
                AdminData(
                    stats = api.getAdminStats(),
                    approvals = api.getApprovals(),
                    invites = api.listInvites(),
                    announcements = api.listAllAnnouncements(),
                )
            }
                .onSuccess { data ->
                    _state.update {
                        it.copy(
                            stats = data.stats,
                            approvals = data.approvals,
                            invites = data.invites,
                            announcements = data.announcements,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Failed to load admin data")) }
                }
        }
    }

    fun createInvite(maxUses: Int, note: String?, expiresAt: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isCreatingInvite = true, createInviteError = null) }
            runCatching {
                api.createInvite(InviteCodeCreate(maxUses, note?.ifBlank { null }, expiresAt?.ifBlank { null }))
            }
                .onSuccess { invite ->
                    _state.update { it.copy(isCreatingInvite = false, invites = it.invites + invite) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isCreatingInvite = false, createInviteError = e.toUserMessage("Failed to create invite")) }
                }
        }
    }

    fun deleteInvite(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteInvite(id) }
                .onSuccess { _state.update { it.copy(invites = it.invites.filter { i -> i.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to delete invite")) } }
        }
    }

    fun approveUser(id: String) {
        viewModelScope.launch {
            runCatching { api.approveUser(id) }
                .onSuccess { _state.update { it.copy(approvals = it.approvals.filter { u -> u.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to approve user")) } }
        }
    }

    fun rejectUser(id: String) {
        viewModelScope.launch {
            runCatching { api.rejectUser(id) }
                .onSuccess { _state.update { it.copy(approvals = it.approvals.filter { u -> u.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to reject user")) } }
        }
    }

    fun bulkApprove() {
        val ids = _state.value.approvals.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { api.bulkApprove(BulkApproveRequest(ids)) }
                .onSuccess { resp ->
                    // Drop only the ones the server actually approved; a partial
                    // batch leaves the rest in the list with their failure reason
                    // available server-side.
                    val approvedIds = resp.results.filter { it.approved }.map { it.userId }.toSet()
                    _state.update { s ->
                        s.copy(
                            approvals = s.approvals.filterNot { it.id in approvedIds },
                            maintenanceMessage = "Approved ${resp.approvedCount} of ${ids.size}",
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Bulk approve failed")) } }
        }
    }

    fun runRetention() {
        viewModelScope.launch {
            runCatching { api.runRetention() }
                .onSuccess { _state.update { it.copy(maintenanceMessage = "Retention job started") } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun runCleanup() {
        viewModelScope.launch {
            runCatching { api.runCleanup() }
                .onSuccess { _state.update { it.copy(maintenanceMessage = "Cleanup job started") } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun runStorageAudit() {
        viewModelScope.launch {
            runCatching { api.getStorageStats() }
                .onSuccess { _state.update { it.copy(maintenanceMessage = "Storage stats refreshed") } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    // ── Moderation ──────────────────────────────────────────────────────────

    // Shared shape for the four moderation actions: run the call, optimistically
    // reflect the new account_status in the loaded row, surface a result toast.
    fun createAnnouncement(create: AnnouncementCreate) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingAnnouncement = true, announcementError = null) }
            runCatching { api.createAnnouncement(create) }
                .onSuccess { announcement ->
                    _state.update { it.copy(
                        isSavingAnnouncement = false,
                        announcementSaved = true,
                        announcements = it.announcements + announcement,
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSavingAnnouncement = false, announcementError = e.toUserMessage("Failed to create announcement")) }
                }
        }
    }

    fun updateAnnouncement(id: String, update: AnnouncementUpdate) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingAnnouncement = true, announcementError = null) }
            runCatching { api.updateAnnouncement(id, update) }
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(
                            isSavingAnnouncement = false,
                            announcementSaved = true,
                            announcements = s.announcements.map { if (it.id == id) updated else it },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSavingAnnouncement = false, announcementError = e.toUserMessage("Failed to update announcement")) }
                }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteAnnouncement(id) }
                .onSuccess { _state.update { it.copy(announcements = it.announcements.filter { a -> a.id != id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Failed to delete announcement")) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearMaintenanceMessage() { _state.update { it.copy(maintenanceMessage = null) } }
    fun clearStepUpError() { _state.update { it.copy(stepUpError = null) } }
    fun clearCreateInviteError() { _state.update { it.copy(createInviteError = null) } }
    fun clearAnnouncementError() { _state.update { it.copy(announcementError = null) } }
    fun clearAnnouncementSaved() { _state.update { it.copy(announcementSaved = false) } }
}
