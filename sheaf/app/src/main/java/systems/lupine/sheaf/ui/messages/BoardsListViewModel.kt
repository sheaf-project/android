package systems.lupine.sheaf.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.BoardSummary
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class BoardsListUiState(
    val isLoading: Boolean = false,
    val boards: List<BoardSummary> = emptyList(),
    val members: List<MemberRead> = emptyList(),
    /** Currently-fronting "caller" perspective; drives unread counts. */
    val callerMemberId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class BoardsListViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(BoardsListUiState(isLoading = true))
    val state: StateFlow<BoardsListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val members = runCatching { api.listMembers() }.getOrDefault(emptyList())
                // Default the caller to whoever is currently fronting (first
                // member of the latest non-ended front). If we can't determine
                // one, leave it null; the boards listing still works, just
                // without unread counts.
                val caller = pickCaller(members)
                val boards = api.listBoards(callerMemberId = caller)
                Triple(members, caller, boards)
            }
                .onSuccess { (members, caller, boards) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            members = members,
                            callerMemberId = caller,
                            boards = boards,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load boards"))
                    }
                }
        }
    }

    fun setCaller(memberId: String) {
        _state.update { it.copy(callerMemberId = memberId) }
        refresh()
    }

    private suspend fun pickCaller(members: List<MemberRead>): String? {
        if (members.isEmpty()) return null
        return runCatching {
            val fronts = api.getCurrentFronts()
            fronts.firstOrNull()?.memberIds?.firstOrNull()
        }.getOrNull() ?: members.firstOrNull()?.id
    }
}
