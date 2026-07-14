package systems.lupine.sheaf.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontUpdate
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryView { Infinite, Paged }

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
    val groups: List<GroupRead> = emptyList(),
    val memberGroups: Map<String, Set<String>> = emptyMap(),
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

    fun deleteFront(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteFront(id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            fronts = it.fronts.filterNot { f -> f.id == id },
                            deleteError = null,
                            // Total drops by one when we successfully delete.
                            totalCount = it.totalCount?.let { t -> (t - 1).coerceAtLeast(0) },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(deleteError = e.toUserMessage()) } }
        }
    }

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
                        customStatus = customStatus,
                    )
                )
                if (endedAt != null) api.updateFront(front.id, FrontUpdate(endedAt = endedAt))
            }.onSuccess {
                loadInitial()
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
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
        viewModelScope.launch {
            runCatching {
                api.updateFront(
                    id,
                    FrontUpdate(
                        memberIds = memberIds,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        // The edit dialog always submits a definite end state,
                        // so a null endedAt here means "still ongoing" — clear
                        // it on the server rather than leaving it untouched.
                        clearEndedAt = endedAt == null,
                        customStatus = customStatus,
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
