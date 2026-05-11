package systems.lupine.sheaf.ui.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.PollRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class PollsListUiState(
    val isLoading: Boolean = false,
    val open: List<PollRead> = emptyList(),
    val closed: List<PollRead> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class PollsListViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(PollsListUiState(isLoading = true))
    val state: StateFlow<PollsListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val polls = api.listPolls()
                val members = runCatching { api.listMembers() }.getOrDefault(emptyList())
                polls to members
            }
                .onSuccess { (polls, members) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            open = polls.filter { p -> !p.isClosed },
                            closed = polls.filter { p -> p.isClosed },
                            memberNames = members.associate { m -> m.id to m.displayNameOrName },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load polls"))
                    }
                }
        }
    }

    fun delete(pollId: String) {
        viewModelScope.launch {
            runCatching { api.deletePoll(pollId) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete poll")) }
                }
        }
    }
}
