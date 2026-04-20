package systems.lupine.sheaf.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontUpdate
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val fronts: List<FrontRead> = emptyList(),
    val members: Map<String, MemberRead> = emptyMap(),
    val allMembers: List<MemberRead> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val deleteError: String? = null,
)

private const val PAGE_SIZE = 30

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val api: SheafApiService,
    private val cache: LocalCache,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(isLoading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var currentOffset = 0

    init { loadInitial() }

    fun loadInitial() {
        currentOffset = 0
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.fronts.isEmpty(), error = null) }
            val online = networkMonitor.isOnline.first()
            if (online) {
                runCatching {
                    val fronts = api.listFronts(limit = PAGE_SIZE, offset = 0)
                    val memberMap = api.listMembers().associateBy { it.id }
                    fronts to memberMap
                }.onSuccess { (fronts, memberMap) ->
                    currentOffset = fronts.size
                    cache.saveHistory(fronts)
                    _state.update {
                        it.copy(
                            fronts = fronts,
                            members = memberMap,
                            allMembers = memberMap.values.sortedBy { m -> m.displayNameOrName },
                            isLoading = false,
                            hasMore = fronts.size == PAGE_SIZE,
                        )
                    }
                }.onFailure { e ->
                    loadFromCache(error = if (_state.value.fronts.isEmpty()) e.toUserMessage() else null)
                }
            } else {
                loadFromCache()
            }
        }
    }

    private suspend fun loadFromCache(error: String? = null) {
        val cachedFronts = cache.getHistory() ?: emptyList()
        val cachedMembers = cache.getMembers() ?: emptyList()
        val memberMap = cachedMembers.associateBy { it.id }
        currentOffset = cachedFronts.size
        _state.update {
            it.copy(
                fronts = cachedFronts,
                members = memberMap,
                allMembers = cachedMembers.sortedBy { m -> m.displayNameOrName },
                isLoading = false,
                hasMore = false,
                error = error,
            )
        }
    }

    fun deleteFront(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteFront(id) }
                .onSuccess { _state.update { it.copy(fronts = it.fronts.filterNot { f -> f.id == id }, deleteError = null) } }
                .onFailure { e -> _state.update { it.copy(deleteError = e.toUserMessage()) } }
        }
    }

    fun addFrontEntry(memberIds: List<String>, startedAt: String, endedAt: String?) {
        viewModelScope.launch {
            runCatching {
                val front = api.createFront(FrontCreate(memberIds = memberIds, startedAt = startedAt))
                if (endedAt != null) api.updateFront(front.id, FrontUpdate(endedAt = endedAt))
            }.onSuccess {
                loadInitial()
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun updateFrontEntry(id: String, memberIds: List<String>, startedAt: String, endedAt: String?) {
        viewModelScope.launch {
            runCatching {
                api.updateFront(id, FrontUpdate(memberIds = memberIds, startedAt = startedAt, endedAt = endedAt))
            }.onSuccess { updated ->
                _state.update { it.copy(fronts = it.fronts.map { f -> if (f.id == id) updated else f }) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    fun loadMore() {
        if (_state.value.isLoadingMore || !_state.value.hasMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            runCatching { api.listFronts(limit = PAGE_SIZE, offset = currentOffset) }
                .onSuccess { newFronts ->
                    currentOffset += newFronts.size
                    _state.update {
                        it.copy(
                            fronts = it.fronts + newFronts,
                            isLoadingMore = false,
                            hasMore = newFronts.size == PAGE_SIZE,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoadingMore = false, error = e.toUserMessage()) } }
        }
    }
}
