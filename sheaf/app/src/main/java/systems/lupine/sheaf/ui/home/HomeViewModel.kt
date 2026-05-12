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
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.PendingActionRead
import systems.lupine.sheaf.data.model.SafetyChangeRequestRead
import systems.lupine.sheaf.data.model.SystemRead
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.sync.SyncWorker
import systems.lupine.sheaf.notification.FrontNotificationHelper
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val switchEndCurrent: Boolean = true,
    val groups: List<GroupRead> = emptyList(),
    // memberId -> set of groupIds that contain that member, built from
    // /v1/groups/{id}/members on demand. Used by the switch sheet's group
    // filter chip-row to narrow the member list.
    val memberGroups: Map<String, Set<String>> = emptyMap(),
    val switchActiveGroupId: String? = null,
    val isOnline: Boolean = true,
    val pendingOpCount: Int = 0,
    val pendingSafetyActions: List<PendingActionRead> = emptyList(),
    val pendingSafetyChanges: List<SafetyChangeRequestRead> = emptyList(),
    // Pending revision-retention trim notice from /v1/retention. Set when the
    // server has a status="pending" notice (typically a tier downgrade).
    val pendingTrimNotice: systems.lupine.sheaf.data.model.RetentionTrimNoticeRead? = null,
    // Set when the most recent online refresh failed for one of the critical
    // display calls (fronts / members / system) while we *did* have cached
    // data to fall back on. The UI surfaces this so the user knows what
    // they're looking at may be stale.
    val refreshFailed: Boolean = false,
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
            // Cache-first paint: if we have nothing in state yet, immediately
            // hydrate from disk so the screen isn't blank while we wait for
            // the network. Subsequent refreshes (state already populated)
            // skip this — the in-memory state is already at least as fresh
            // as the cache.
            if (_state.value.allMembers.isEmpty()) {
                loadFromCache()
            }
            _state.update {
                it.copy(
                    isLoading = it.allMembers.isEmpty(),
                    error = null,
                    refreshFailed = false,
                )
            }

            val online = networkMonitor.isOnline.first()
            if (!online) {
                // Already painted from cache above (or had state already).
                // Nothing more to do; the offline banner is already up.
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // Fan out the seven calls in parallel. Order matters only for the
            // wire dispatch sequence — getCurrentFronts is started first so
            // the most user-visible piece of data is the earliest packet on
            // the connection. listMembers next because the front display
            // needs it to resolve names. Everything else trails.
            coroutineScope {
                val frontsD        = async { runCatching { api.getCurrentFronts() } }
                val membersD       = async { runCatching { api.listMembers() } }
                val systemD        = async { runCatching { api.getOwnSystem() } }
                val announcementsD = async { runCatching { api.getAnnouncements() } }
                val safetyD        = async { runCatching { api.getSystemSafety() } }
                val retentionD     = async { runCatching { api.getRetention() } }
                val userD          = async { runCatching { api.getMe() } }

                val fronts        = frontsD.await()
                val members       = membersD.await()
                val system        = systemD.await()
                val announcements = announcementsD.await()
                val safety        = safetyD.await()
                val retention     = retentionD.await()
                val user          = userD.await()

                val criticalFailures = listOf(fronts, members, system).count { it.isFailure }
                val anyCriticalFailed = criticalFailures > 0

                // Persist whatever did come back. On partial failure we
                // still want disk to hold the freshest version of each
                // slice rather than tying success to a single all-or-nothing
                // commit.
                fronts.getOrNull()?.let { cache.saveFronts(it) }
                members.getOrNull()?.let { cache.saveMembers(it) }
                system.getOrNull()?.let { cache.saveSystem(it) }

                if (anyCriticalFailed && _state.value.allMembers.isEmpty()) {
                    // No cache + a critical call failed: there's nothing to
                    // paint, so surface the network error in the empty-state
                    // view (the Retry button there reruns load()).
                    val firstError = listOf(fronts, members, system)
                        .firstNotNullOfOrNull { it.exceptionOrNull() }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = firstError?.toUserMessage() ?: "Couldn't load",
                            refreshFailed = false,
                        )
                    }
                    return@coroutineScope
                }

                val newFronts        = fronts.getOrNull()        ?: _state.value.currentFronts
                val newMembers       = members.getOrNull()       ?: _state.value.allMembers
                val newSystem        = system.getOrNull()        ?: _state.value.system
                val newAnnouncements = announcements.getOrNull() ?: _state.value.announcements
                val newUser          = user.getOrNull()          ?: _state.value.user
                val frontingIds = newFronts.flatMap { it.memberIds }.toSet()
                val frontingMembers = newMembers.filter { it.id in frontingIds }
                val safetyResp = safety.getOrNull()
                val trimNotice = if (retention.isSuccess) {
                    retention.getOrNull()?.trimNotice?.takeIf { it.status == "pending" }
                } else {
                    _state.value.pendingTrimNotice
                }

                _state.update {
                    it.copy(
                        user = newUser,
                        system = newSystem,
                        currentFronts = newFronts,
                        frontingMembers = frontingMembers,
                        allMembers = newMembers,
                        announcements = newAnnouncements,
                        pendingSafetyActions = safetyResp?.pendingActions ?: it.pendingSafetyActions,
                        pendingSafetyChanges = safetyResp?.pendingChanges ?: it.pendingSafetyChanges,
                        pendingTrimNotice = trimNotice,
                        isLoading = false,
                        refreshFailed = anyCriticalFailed,
                        error = null,
                    )
                }
                if (prefs.frontNotification.first()) {
                    try {
                        notificationHelper.post(frontingMembers.map { it.displayNameOrName })
                    } catch (_: SecurityException) {}
                }
            }
        }
    }

    private suspend fun loadFromCache() {
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
            )
        }
    }

    fun dismissAnnouncement(id: String) {
        _state.update { it.copy(dismissedAnnouncementIds = it.dismissedAnnouncementIds + id) }
    }

    fun openSwitchSheet() {
        val s = _state.value
        val currentIds = s.currentFronts.flatMap { it.memberIds }.toSet()
        // Prefill the end-current toggle from the system pref (web UI does the
        // same: `replaceFronts ?? (system.replace_fronts_default ?? true)`).
        val defaultEndCurrent = s.system?.replaceFrontsDefault ?: true
        _state.update {
            it.copy(
                showSwitchSheet = true,
                switchSelection = currentIds,
                switchEndCurrent = defaultEndCurrent,
                switchActiveGroupId = null,
            )
        }
        loadGroupsForFilter()
    }

    fun setSwitchActiveGroup(groupId: String?) {
        _state.update { it.copy(switchActiveGroupId = groupId) }
    }

    private fun loadGroupsForFilter() {
        if (_state.value.groups.isNotEmpty()) return  // already loaded
        viewModelScope.launch {
            runCatching { api.listGroups() }
                .onSuccess { groups ->
                    _state.update { it.copy(groups = groups) }
                    // Build the memberId -> groupIds map. N is small (5-30
                    // groups typical) but issuing them sequentially used to
                    // be the visible bottleneck for the switch sheet — fan
                    // out and awaitAll.
                    val perGroup = coroutineScope {
                        groups.map { g ->
                            async {
                                g.id to runCatching { api.getGroupMembers(g.id) }
                                    .getOrDefault(emptyList())
                            }
                        }.awaitAll()
                    }
                    val map = mutableMapOf<String, MutableSet<String>>()
                    perGroup.forEach { (groupId, members) ->
                        members.forEach { m ->
                            map.getOrPut(m.id) { mutableSetOf() }.add(groupId)
                        }
                    }
                    _state.update { it.copy(memberGroups = map.mapValues { (_, v) -> v.toSet() }) }
                }
        }
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

    fun setSwitchEndCurrent(value: Boolean) {
        _state.update { it.copy(switchEndCurrent = value) }
    }

    fun confirmSwitch() {
        val s = _state.value
        val sel = s.switchSelection
        if (sel.isEmpty()) return
        val replaceFronts = s.switchEndCurrent
        viewModelScope.launch {
            _state.update { it.copy(isSwitching = true, error = null) }
            if (networkMonitor.isOnline.first()) {
                runCatching {
                    // Server handles end-current atomically when replace_fronts=true.
                    api.createFront(
                        FrontCreate(
                            memberIds = sel.toList(),
                            startedAt = Instant.now().toString(),
                            replaceFronts = replaceFronts,
                        )
                    )
                }.onSuccess {
                    _state.update { it.copy(isSwitching = false, showSwitchSheet = false) }
                    load()
                }.onFailure { e ->
                    _state.update { it.copy(isSwitching = false, error = e.toUserMessage()) }
                }
            } else {
                pendingOpsDao.deleteAllSwitches()
                pendingOpsDao.insertSwitch(
                    PendingFrontSwitch(
                        memberIds = sel.joinToString(","),
                        replaceFronts = replaceFronts,
                    )
                )
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
