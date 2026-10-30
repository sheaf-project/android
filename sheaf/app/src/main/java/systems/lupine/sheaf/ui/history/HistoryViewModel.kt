package systems.lupine.sheaf.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.deleteFrontOrQueue
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontUpdate
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.ui.sharing.ShareError
import systems.lupine.sheaf.ui.sharing.message
import systems.lupine.sheaf.ui.sharing.toShareError
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryView { Infinite, Paged }

/**
 * The System Safety settings a deletion has to satisfy.
 *
 * The auth tier guards EVERY deletion, whether or not Fronts is a ticked
 * category, so it is read on load and the credentials are asked for before the
 * request rather than after a refusal. [appliesToFronts] only decides whether
 * the deletion is then held for the grace period.
 */
data class FrontDeleteSafety(
    val authTier: String = "none",
    val totpEnabled: Boolean = false,
    val appliesToFronts: Boolean = false,
    val gracePeriodDays: Int = 0,
) {
    val needsCredentials: Boolean get() = authTier != "none"
}

data class HistoryUiState(
    val fronts: List<FrontRead> = emptyList(),
    val members: Map<String, MemberRead> = emptyMap(),
    val allMembers: List<MemberRead> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    // Infinite mode: false once the server confirms no more cursor pages.
    val hasMore: Boolean = true,
    val error: String? = null,
    val deleteError: String? = null,
    /** What System Safety will ask for, and how long it holds a deletion. */
    val deleteSafety: FrontDeleteSafety = FrontDeleteSafety(),
    /** The entry waiting on credentials, replayed verbatim once they arrive. */
    val stepUpDeleteId: String? = null,
    val stepUpError: String? = null,
    /** Said after a deletion the server queued rather than carried out. */
    val deleteQueuedMessage: String? = null,
    val groups: List<GroupRead> = emptyList(),
    val memberGroups: Map<String, Set<String>> = emptyMap(),
    // Who is fronting right now, and whether starting a front would end them.
    // Both only for validating a new open entry; see loadOpenFrontContext.
    val openFronts: List<FrontRead> = emptyList(),
    val replaceFrontsDefault: Boolean = true,
    // Pagination config.
    val view: HistoryView = HistoryView.Infinite,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    // Paged mode state.
    val currentPage: Int = 1,
    val totalCount: Int? = null,
) {
    /** Paged mode: number of pages, derived from totalCount. Always >= 1. */
    val totalPages: Int
        get() = if (pageSize <= 0) 1
                else maxOf(1, ((totalCount ?: 0) + pageSize - 1) / pageSize)

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}

val PAGE_SIZE_OPTIONS = listOf(25, 50, 100, 200)

/**
 * The open fronts that a new front starting at [startedAt] could not legally
 * end, because they had not begun yet.
 *
 * Starting a front ends every open one *at the new start*. An open front that
 * began after that moment would get an end before its own beginning, which
 * `ck_fronts_ended_after_started` refuses, taking the whole request down with
 * it. Empty means the start is safe.
 *
 * Equal timestamps are fine: a front that began at exactly this instant ends
 * at exactly this instant, which is a zero-length entry rather than a
 * backwards one, and the constraint allows it.
 *
 * A front with an unparseable `started_at` is left out rather than treated as
 * a clash: the server is the authority on its own rows, and guessing here
 * would block a legitimate entry over a timestamp this client could not read.
 */
fun openFrontsStartingAfter(
    startedAt: java.time.Instant,
    openFronts: List<FrontRead>,
): List<FrontRead> = openFronts.filter { front ->
    val began = runCatching { java.time.Instant.parse(front.startedAt) }.getOrNull()
    began != null && began.isAfter(startedAt)
}

/**
 * Compare two timestamps at the precision the edit sheet can actually show.
 *
 * The time pickers offer hours and minutes, so the seconds on a stored
 * timestamp are not something the user can see or change. Treating a
 * same-minute value as unchanged is what keeps them: the field is left out of
 * the patch entirely and the server keeps what it already had.
 */
private fun sameMinute(a: String?, b: String?): Boolean {
    if (a == null || b == null) return a == b
    val left = runCatching { java.time.Instant.parse(a) }.getOrNull() ?: return false
    val right = runCatching { java.time.Instant.parse(b) }.getOrNull() ?: return false
    return left.truncatedTo(java.time.temporal.ChronoUnit.MINUTES) ==
        right.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
}

/**
 * What an edit actually changed, as a patch carrying only those fields.
 *
 * The sheet used to submit every field on every save, with both timestamps
 * rebuilt from pickers that had already dropped their seconds. So changing
 * only a status rewrote both times to the minute, which shows up as a spurious
 * edit in the web history and, worse, breaks contiguous-front coalescing:
 * chains are walked by strict equality between one entry's end and the next
 * one's start, so rounding one side detaches the chain and resets that
 * member's "fronting since".
 *
 * Omitting a field is also how seconds survive. The server reads an absent
 * field as "leave it alone", so an untouched time keeps its original value
 * exactly, sub-second part and all.
 */
internal fun frontEditPatch(
    original: FrontRead,
    memberIds: List<String>,
    startedAt: String,
    endedAt: String?,
    customStatus: String?,
): FrontUpdate {
    val membersChanged = memberIds.toSet() != original.memberIds.toSet()
    val endStateChanged = when {
        endedAt == null -> original.endedAt != null
        else -> original.endedAt == null || !sameMinute(endedAt, original.endedAt)
    }
    val statusChanged = customStatus != original.customStatus
    return FrontUpdate(
        memberIds = memberIds.takeIf { membersChanged },
        startedAt = startedAt.takeIf { !sameMinute(it, original.startedAt) },
        endedAt = endedAt.takeIf { endStateChanged },
        // Only when the entry really had an end and now has none, so an edit
        // that never touched the end cannot reopen a closed entry.
        clearEndedAt = endStateChanged && endedAt == null,
        customStatus = customStatus.takeIf { statusChanged },
        clearCustomStatus = statusChanged && customStatus == null,
    )
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val api: SheafApiService,
    private val cache: LocalCache,
    private val networkMonitor: NetworkMonitor,
    private val prefs: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(isLoading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    // Infinite mode: opaque cursor for the next page. Null = haven't fetched
    // yet (load first page with no cursor) or no more pages.
    private var nextCursor: String? = null

    init {
        viewModelScope.launch {
            // Hydrate view-mode + page-size from prefs before kicking off
            // the initial load so the first request uses the user's chosen
            // page size rather than the default.
            val viewPref = prefs.historyView.first()
            val sizePref = prefs.historyPageSize.first()
            val resolvedSize = if (sizePref in PAGE_SIZE_OPTIONS) sizePref else HistoryUiState.DEFAULT_PAGE_SIZE
            _state.update {
                it.copy(
                    view = if (viewPref == "paged") HistoryView.Paged else HistoryView.Infinite,
                    pageSize = resolvedSize,
                )
            }
            loadInitial()
        }
        loadDeleteSafety()
    }

    /**
     * What a deletion will be asked for, fetched up front.
     *
     * Best-effort: not knowing means the delete goes out bare, and the server's
     * own refusal still routes to the credentials sheet. Knowing just means the
     * sheet opens before the refusal rather than after it.
     */
    private fun loadDeleteSafety() {
        viewModelScope.launch {
            runCatching {
                val safety = api.getSystemSafety()
                val totp = runCatching { api.getMe().totpEnabled }.getOrDefault(false)
                FrontDeleteSafety(
                    authTier = safety.settings.authTier,
                    totpEnabled = totp,
                    appliesToFronts = safety.settings.appliesToFronts,
                    gracePeriodDays = safety.settings.gracePeriodDays,
                )
            }.onSuccess { safety -> _state.update { it.copy(deleteSafety = safety) } }
        }
    }

    fun loadInitial() {
        nextCursor = null
        val s = _state.value
        if (s.view == HistoryView.Paged) {
            loadPagedPage(1)
        } else {
            loadFirstInfinitePage()
        }
    }

    private fun loadFirstInfinitePage() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.fronts.isEmpty(), error = null) }
            val online = networkMonitor.isOnline.first()
            if (!online) {
                loadFromCache()
                return@launch
            }
            val limit = _state.value.pageSize
            coroutineScope {
                val frontsD = async {
                    runCatching {
                        api.listFrontsPaginated(limit = limit, cursor = null, includeTotal = null)
                    }
                }
                val membersD = async { runCatching { api.listMembers() } }
                val frontsResp = frontsD.await()
                val membersResult = membersD.await()

                if (frontsResp.isFailure) {
                    val err = frontsResp.exceptionOrNull()
                    loadFromCache(error = if (_state.value.fronts.isEmpty()) err?.toUserMessage() else null)
                    return@coroutineScope
                }
                val response = frontsResp.getOrNull()!!
                if (!response.isSuccessful) {
                    loadFromCache(error = "Couldn't load history (${response.code()})")
                    return@coroutineScope
                }
                val fronts = response.body() ?: emptyList()
                nextCursor = response.headers()["X-Sheaf-Next-Cursor"]
                val hasMore = response.headers()["X-Sheaf-Has-More"] == "true"

                cache.saveHistory(fronts)
                val memberMap = membersResult.getOrNull()?.associateBy { it.id }
                    ?: _state.value.members
                val allMembers = membersResult.getOrNull()?.sortedBy { it.displayNameOrName }
                    ?: _state.value.allMembers
                _state.update {
                    it.copy(
                        fronts = fronts,
                        members = memberMap,
                        allMembers = allMembers,
                        isLoading = false,
                        hasMore = hasMore,
                        currentPage = 1,
                        totalCount = null,
                    )
                }
            }
        }
    }

    private fun loadPagedPage(page: Int) {
        viewModelScope.launch {
            val limit = _state.value.pageSize
            val safePage = maxOf(1, page)
            val offset = (safePage - 1) * limit
            _state.update {
                it.copy(
                    // Show a top spinner when we have no rows at all or we're
                    // landing on a brand new page from a different size.
                    isLoading = it.fronts.isEmpty(),
                    isLoadingMore = it.fronts.isNotEmpty(),
                    error = null,
                )
            }
            val online = networkMonitor.isOnline.first()
            if (!online) {
                loadFromCache()
                return@launch
            }
            coroutineScope {
                val frontsD = async {
                    runCatching {
                        api.listFrontsPaginated(
                            limit = limit,
                            offset = offset,
                            cursor = null,
                            includeTotal = true,
                        )
                    }
                }
                val membersD = async {
                    if (_state.value.allMembers.isEmpty()) runCatching { api.listMembers() }
                    else Result.success(_state.value.allMembers)
                }
                val frontsResp = frontsD.await()
                val membersResult = membersD.await()

                if (frontsResp.isFailure) {
                    val err = frontsResp.exceptionOrNull()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = err?.toUserMessage() ?: "Couldn't load page",
                        )
                    }
                    return@coroutineScope
                }
                val response = frontsResp.getOrNull()!!
                if (!response.isSuccessful) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = "Couldn't load history (${response.code()})",
                        )
                    }
                    return@coroutineScope
                }
                val fronts = response.body() ?: emptyList()
                val total = response.headers()["X-Sheaf-Total-Count"]?.toIntOrNull()

                if (safePage == 1) cache.saveHistory(fronts)
                val memberMap = membersResult.getOrNull()?.associateBy { it.id }
                    ?: _state.value.members
                val allMembers = membersResult.getOrNull()?.sortedBy { it.displayNameOrName }
                    ?: _state.value.allMembers
                _state.update {
                    it.copy(
                        fronts = fronts,
                        members = memberMap,
                        allMembers = allMembers,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                        currentPage = safePage,
                        totalCount = total ?: it.totalCount,
                    )
                }
            }
        }
    }

    private suspend fun loadFromCache(error: String? = null) {
        val cachedFronts = cache.getHistory() ?: emptyList()
        val cachedMembers = cache.getMembers() ?: emptyList()
        val memberMap = cachedMembers.associateBy { it.id }
        _state.update {
            it.copy(
                fronts = cachedFronts,
                members = memberMap,
                allMembers = cachedMembers.sortedBy { m -> m.displayNameOrName },
                isLoading = false,
                isLoadingMore = false,
                hasMore = false,
                error = error,
            )
        }
    }

    fun loadMore() {
        // Only meaningful in infinite mode; paged mode uses goToPage.
        if (_state.value.view != HistoryView.Infinite) return
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        val cursor = nextCursor ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            runCatching {
                api.listFrontsPaginated(
                    limit = _state.value.pageSize,
                    cursor = cursor,
                    includeTotal = null,
                )
            }
                .onSuccess { response ->
                    if (!response.isSuccessful) {
                        _state.update {
                            it.copy(
                                isLoadingMore = false,
                                error = "Couldn't load more (${response.code()})",
                            )
                        }
                        return@onSuccess
                    }
                    val newFronts = response.body() ?: emptyList()
                    val hasMore = response.headers()["X-Sheaf-Has-More"] == "true"
                    nextCursor = response.headers()["X-Sheaf-Next-Cursor"]
                    _state.update {
                        it.copy(
                            fronts = it.fronts + newFronts,
                            isLoadingMore = false,
                            hasMore = hasMore,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoadingMore = false, error = e.toUserMessage())
                    }
                }
        }
    }

    fun goToPage(page: Int) {
        if (_state.value.view != HistoryView.Paged) return
        val capped = page.coerceIn(1, _state.value.totalPages)
        if (capped == _state.value.currentPage && _state.value.fronts.isNotEmpty()) return
        loadPagedPage(capped)
    }

    fun setView(view: HistoryView) {
        if (view == _state.value.view) return
        _state.update { it.copy(view = view, fronts = emptyList()) }
        viewModelScope.launch {
            prefs.saveHistoryView(if (view == HistoryView.Paged) "paged" else "infinite")
        }
        loadInitial()
    }

    fun setPageSize(size: Int) {
        if (size == _state.value.pageSize || size !in PAGE_SIZE_OPTIONS) return
        _state.update { it.copy(pageSize = size, fronts = emptyList()) }
        viewModelScope.launch {
            prefs.saveHistoryPageSize(size)
        }
        loadInitial()
    }

    /**
     * Delete a history entry, or ask for credentials first if System Safety
     * wants them.
     *
     * Two things this used to get wrong. It sent a bare DELETE, which the
     * server refuses with 400 "Password required" under any auth tier, ticked
     * category or not, leaving a "Delete failed" dialog with nowhere to type.
     * And it removed the row on any 2xx, including the 202 that means the
     * server has only QUEUED the deletion for a grace period, so the entry
     * reappeared on the next load with nothing marking it as pending.
     */
    fun deleteFront(id: String, password: String? = null, totpCode: String? = null) {
        val safety = _state.value.deleteSafety
        if (safety.needsCredentials && password == null && totpCode == null) {
            _state.update { it.copy(stepUpDeleteId = id, stepUpError = null) }
            return
        }
        viewModelScope.launch {
            runCatching { api.deleteFrontOrQueue(id, password, totpCode) }
                .onSuccess { pending ->
                    if (pending?.pendingActionId != null) {
                        // Still there, now carrying pending_delete_at. The row
                        // already knows how to render that (dimmed, with a
                        // badge), so reload rather than guessing at the date.
                        val days = safety.gracePeriodDays
                        _state.update {
                            it.copy(
                                deleteError = null,
                                stepUpDeleteId = null,
                                stepUpError = null,
                                deleteQueuedMessage = "This entry is queued for deletion in " +
                                    "$days day${if (days == 1) "" else "s"}. Cancel it from " +
                                    "System Safety before then.",
                            )
                        }
                        loadInitial()
                    } else {
                        _state.update {
                            it.copy(
                                fronts = it.fronts.filterNot { f -> f.id == id },
                                deleteError = null,
                                stepUpDeleteId = null,
                                stepUpError = null,
                                // Total drops by one when we really delete.
                                totalCount = it.totalCount?.let { t -> (t - 1).coerceAtLeast(0) },
                            )
                        }
                    }
                }
                .onFailure { e ->
                    // The tier can change between load and now, and a wrong
                    // credential comes back the same way, so both land on the
                    // sheet rather than on a dead-end error dialog.
                    when (val err = e.toShareError("Couldn't delete this entry")) {
                        is ShareError.StepUpRequired -> _state.update {
                            it.copy(stepUpDeleteId = id, stepUpError = null)
                        }
                        is ShareError.BadCredentials -> _state.update {
                            it.copy(stepUpDeleteId = id, stepUpError = err.message)
                        }
                        else -> _state.update {
                            it.copy(
                                stepUpDeleteId = null,
                                deleteError = err.message("Couldn't delete this entry"),
                            )
                        }
                    }
                }
        }
    }

    fun confirmDeleteStepUp(password: String?, totpCode: String?) {
        val id = _state.value.stepUpDeleteId ?: return
        deleteFront(id, password?.ifBlank { null }, totpCode?.ifBlank { null })
    }

    fun dismissDeleteStepUp() =
        _state.update { it.copy(stepUpDeleteId = null, stepUpError = null) }

    fun clearDeleteQueuedMessage() = _state.update { it.copy(deleteQueuedMessage = null) }

    fun clearDeleteError() = _state.update { it.copy(deleteError = null) }

    fun addFrontEntry(
        memberIds: List<String>,
        startedAt: String,
        endedAt: String?,
        customStatus: String?,
    ) {
        viewModelScope.launch {
            runCatching {
                val front = api.createFront(
                    FrontCreate(
                        memberIds = memberIds,
                        startedAt = startedAt,
                        // Recording history, not switching. The server skips
                        // the live-roster behaviours for a closed entry.
                        endedAt = endedAt,
                        // Load-bearing on a server that predates ended_at,
                        // which ignores it and opens a front instead. Without
                        // this, that front replaces the live one and back-dates
                        // its end to the historical start: where that precedes
                        // the live front's own start the database constraint
                        // rejects it and the whole request 500s, and where it
                        // does not, whoever was really fronting is silently
                        // ended in the past. A server that does understand
                        // ended_at ignores this, since a closed entry never
                        // replaces anything.
                        replaceFronts = if (endedAt != null) false else null,
                        customStatus = customStatus,
                    )
                )
                // Detection rather than a version check: if the entry came back
                // open despite an end being asked for, the server is old and
                // dropped the field. Closing it now is safe because nothing was
                // replaced on the way in.
                if (endedAt != null && front.endedAt == null) {
                    api.updateFront(front.id, FrontUpdate(endedAt = endedAt))
                }
            }.onSuccess {
                loadInitial()
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage("Couldn't add the front entry")) }
            }
        }
    }

    fun updateFrontEntry(
        id: String,
        memberIds: List<String>,
        startedAt: String,
        endedAt: String?,
        customStatus: String?,
    ) {
        val original = _state.value.fronts.firstOrNull { it.id == id }
        viewModelScope.launch {
            runCatching {
                api.updateFront(
                    id,
                    // Only what changed. Sending everything rewrote both
                    // timestamps on every save, including status-only edits.
                    original?.let {
                        frontEditPatch(it, memberIds, startedAt, endedAt, customStatus)
                    } ?: FrontUpdate(
                        memberIds = memberIds,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        clearEndedAt = endedAt == null,
                        customStatus = customStatus,
                        clearCustomStatus = customStatus == null,
                    ),
                )
            }.onSuccess { updated ->
                _state.update { it.copy(fronts = it.fronts.map { f -> if (f.id == id) updated else f }) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    /**
     * What a new *open* entry would do to the fronts already running.
     *
     * Only the add sheet needs this, and only for the "still ongoing" case:
     * an open entry is a switch, so the server ends every open front at the
     * new start. Back-date that start past when one of them began and its end
     * lands before its own beginning, which the database refuses and the
     * request 500s on. Checking it here turns that into a sentence.
     *
     * Asked for rather than read out of the loaded page: the history list is
     * paginated, so an open front is only *probably* on it, and a validation
     * that is only probably right is worse than none.
     */
    fun loadOpenFrontContext() {
        viewModelScope.launch {
            val replaceDefault = runCatching { cache.getSystem()?.replaceFrontsDefault }
                .getOrNull() ?: true
            val open = runCatching { api.getCurrentFronts() }.getOrNull()
            _state.update {
                it.copy(
                    openFronts = open ?: it.openFronts,
                    replaceFrontsDefault = replaceDefault,
                )
            }
        }
    }

    fun loadGroupsForFilter() {
        if (_state.value.groups.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { api.listGroups() }
                .onSuccess { groups ->
                    _state.update { it.copy(groups = groups) }
                    // Same parallelise-the-N+1 treatment as the home screen.
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
}
