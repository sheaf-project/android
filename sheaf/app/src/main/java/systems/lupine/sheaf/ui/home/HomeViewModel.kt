package systems.lupine.sheaf.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.db.PendingFrontRemoval
import systems.lupine.sheaf.data.db.PendingFrontSwitch
import systems.lupine.sheaf.data.db.PendingOperationsDao
import systems.lupine.sheaf.data.model.AnnouncementPublic
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontUpdate
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.SystemRead
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.sync.SyncWorker
import systems.lupine.sheaf.notification.FrontNotificationHelper
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val isOnline: Boolean = true,
    val pendingOpCount: Int = 0,
) {
    val visibleAnnouncements: List<AnnouncementPublic>
        get() = announcements.filter { it.id !in dismissedAnnouncementIds }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val notificationHelper: FrontNotificationHelper,
    private val cache: LocalCache,
    private val pendingOpsDao: PendingOperationsDao,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // Track online state and pending op count continuously.
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.update { it.copy(isOnline = online) }
                // When coming back online, trigger a refresh and run any pending ops.
                if (online) {
                    load()
                    scheduleSyncIfNeeded()
                }
            }
        }
        viewModelScope.launch {
            combine(
                pendingOpsDao.switchCountFlow(),
                pendingOpsDao.removalCountFlow(),
            ) { switches, removals -> switches + removals }
                .collect { count -> _state.update { it.copy(pendingOpCount = count) } }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.allMembers.isEmpty(), error = null) }
            val online = networkMonitor.isOnline.first()
            if (online) {
                runCatching {
                    val user = runCatching { api.getMe() }.getOrNull()
                    val system = api.getOwnSystem()
                    val fronts = api.getCurrentFronts()
                    val members = api.listMembers()
                    val announcements = runCatching { api.getAnnouncements() }.getOrDefault(emptyList())
                    // Persist to cache.
                    cache.saveSystem(system)
                    cache.saveMembers(members)
                    cache.saveFronts(fronts)
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
                        } catch (_: SecurityException) {}
                    }
                }.onFailure { e ->
                    // Network call failed even though we thought we were online — fall back to cache.
                    loadFromCache(error = if (_state.value.allMembers.isEmpty()) e.toUserMessage() else null)
                }
            } else {
                loadFromCache()
            }
        }
    }

    private suspend fun loadFromCache(error: String? = null) {
        val members = cache.getMembers() ?: emptyList()
        val fronts = cache.getFronts() ?: emptyList()
        val system = cache.getSystem()
        val frontingIds = fronts.flatMap { it.memberIds }.toSet()
        val frontingMembers = members.filter { it.id in frontingIds }
        _state.update { s ->
            s.copy(
                system = system ?: s.system,
                currentFronts = fronts,
                frontingMembers = frontingMembers,
                allMembers = members,
                isLoading = false,
                error = error ?: if (members.isEmpty()) s.error else null,
            )
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
            if (networkMonitor.isOnline.first()) {
                runCatching {
                    _state.value.currentFronts.forEach { front ->
                        api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                    }
                    api.createFront(FrontCreate(memberIds = sel.toList(), startedAt = Instant.now().toString()))
                }.onSuccess {
                    _state.update { it.copy(isSwitching = false, showSwitchSheet = false) }
                    load()
                }.onFailure { e ->
                    _state.update { it.copy(isSwitching = false, error = e.toUserMessage()) }
                }
            } else {
                pendingOpsDao.deleteAllSwitches()
                pendingOpsDao.insertSwitch(PendingFrontSwitch(memberIds = sel.joinToString(",")))
                SyncWorker.schedule(appContext)
                _state.update { it.copy(isSwitching = false, showSwitchSheet = false) }
            }
        }
    }

    fun removeFromFront(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            if (networkMonitor.isOnline.first()) {
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
                    _state.update { it.copy(error = e.toUserMessage()) }
                    return@launch
                }
            } else {
                pendingOpsDao.insertRemoval(PendingFrontRemoval(memberId = memberId))
                SyncWorker.schedule(appContext)
            }
            load()
        }
    }

    private suspend fun scheduleSyncIfNeeded() {
        val hasPending = pendingOpsDao.getAllSwitches().isNotEmpty() ||
            pendingOpsDao.getAllRemovals().isNotEmpty()
        if (hasPending) SyncWorker.schedule(appContext)
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
