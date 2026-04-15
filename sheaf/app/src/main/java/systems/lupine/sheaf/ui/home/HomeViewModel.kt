package systems.lupine.sheaf.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AnnouncementPublic
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontUpdate
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.SystemRead
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.notification.FrontNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class HomeUiState(
    val user: UserRead? = null,
    val system: SystemRead? = null,
    val currentFronts: List<FrontRead> = emptyList(),
    val frontingMembers: List<MemberRead> = emptyList(),
    val allMembers: List<MemberRead> = emptyList(),
    val announcements: List<AnnouncementPublic> = emptyList(),
    val dismissedAnnouncementIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSwitching: Boolean = false,
    val error: String? = null,
    val showSwitchSheet: Boolean = false,
    val switchSelection: Set<String> = emptySet(),
) {
    val visibleAnnouncements: List<AnnouncementPublic>
        get() = announcements.filter { it.id !in dismissedAnnouncementIds }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val notificationHelper: FrontNotificationHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            // Only show full spinner on first load; subsequent calls refresh silently
            _state.update { it.copy(isLoading = it.allMembers.isEmpty(), error = null) }
            runCatching {
                val user = runCatching { api.getMe() }.getOrNull()
                val system = api.getOwnSystem()
                val fronts = api.getCurrentFronts()
                val members = api.listMembers()
                val announcements = runCatching { api.getAnnouncements() }.getOrDefault(emptyList())
                val frontingIds = fronts.flatMap { it.memberIds }.toSet()
                val frontingMembers = members.filter { it.id in frontingIds }
                _state.update {
                    it.copy(
                        user = user,
                        system = system,
                        currentFronts = fronts,
                        frontingMembers = frontingMembers,
                        allMembers = members,
                        announcements = announcements,
                        isLoading = false,
                    )
                }
                if (prefs.frontNotification.first()) {
                    try {
                        notificationHelper.post(frontingMembers.map { it.displayNameOrName })
                    } catch (_: SecurityException) {
                        // POST_NOTIFICATIONS permission not granted — skip silently
                    }
                }
            }.onFailure { e ->
                _state.update { s -> s.copy(isLoading = false, error = if (s.allMembers.isEmpty()) e.message else s.error) }
            }
        }
    }

    fun dismissAnnouncement(id: String) {
        _state.update { it.copy(dismissedAnnouncementIds = it.dismissedAnnouncementIds + id) }
    }

    fun openSwitchSheet() {
        val currentIds = _state.value.currentFronts.flatMap { it.memberIds }.toSet()
        _state.update { it.copy(showSwitchSheet = true, switchSelection = currentIds) }
    }

    fun closeSwitchSheet() {
        _state.update { it.copy(showSwitchSheet = false) }
    }

    fun toggleMemberSelection(memberId: String) {
        _state.update { s ->
            val sel = s.switchSelection.toMutableSet()
            if (memberId in sel) sel.remove(memberId) else sel.add(memberId)
            s.copy(switchSelection = sel)
        }
    }

    fun confirmSwitch() {
        val sel = _state.value.switchSelection
        if (sel.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSwitching = true, error = null) }
            runCatching {
                // End all current fronts
                _state.value.currentFronts.forEach { front ->
                    api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                }
                // Create new front
                api.createFront(FrontCreate(memberIds = sel.toList(), startedAt = Instant.now().toString()))
            }.onSuccess {
                _state.update { it.copy(isSwitching = false, showSwitchSheet = false) }
                load()
            }.onFailure { e ->
                _state.update { it.copy(isSwitching = false, error = e.message) }
            }
        }
    }

    fun removeFromFront(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching {
                _state.value.currentFronts.filter { memberId in it.memberIds }.forEach { front ->
                    val remaining = front.memberIds - memberId
                    if (remaining.isEmpty()) {
                        api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                    } else {
                        api.updateFront(front.id, FrontUpdate(memberIds = remaining))
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
                return@launch
            }
            load()
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
