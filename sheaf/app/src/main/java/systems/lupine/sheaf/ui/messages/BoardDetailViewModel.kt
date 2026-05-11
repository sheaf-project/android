package systems.lupine.sheaf.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MarkSeenRequest
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.MessageCreate
import systems.lupine.sheaf.data.model.MessageRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class BoardDetailUiState(
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val boardKind: String = "system",
    /** Null for the system board, member id for a member wall. */
    val boardMemberId: String? = null,
    val boardTitle: String = "",
    val messages: List<MessageRead> = emptyList(),
    val members: List<MemberRead> = emptyList(),
    /** Member-of-this-system the new post is attributed to. */
    val authorMemberId: String? = null,
    val draft: String = "",
    val error: String? = null,
)

@HiltViewModel
class BoardDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route format: messages/board/{kind}/{memberId} where memberId is "_" for
    // the system board. Encodes the variant cleanly into a nav arg.
    private val boardKind: String = checkNotNull(savedStateHandle["kind"])
    private val rawMemberId: String? = savedStateHandle.get<String>("memberId")
    private val boardMemberId: String? = rawMemberId?.takeIf { it != "_" }

    private val _state = MutableStateFlow(BoardDetailUiState(isLoading = true))
    val state: StateFlow<BoardDetailUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(boardKind = boardKind, boardMemberId = boardMemberId)
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val members = runCatching { api.listMembers() }.getOrDefault(emptyList())
                val caller = members.firstOrNull()?.id
                val page = api.getBoardMessages(
                    boardKind = boardKind,
                    boardMemberId = boardMemberId,
                    callerMemberId = caller,
                )
                Triple(members, page, caller)
            }
                .onSuccess { (members, page, caller) ->
                    val title = if (boardMemberId == null) {
                        "System board"
                    } else {
                        members.firstOrNull { it.id == boardMemberId }?.displayNameOrName
                            ?: "Member wall"
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            messages = page.messages,
                            members = members,
                            authorMemberId = it.authorMemberId ?: caller,
                            boardTitle = title,
                        )
                    }
                    // Best-effort mark-seen for the caller member.
                    if (caller != null) {
                        runCatching {
                            api.markBoardSeen(
                                MarkSeenRequest(
                                    memberId = caller,
                                    boardKind = boardKind,
                                    boardMemberId = boardMemberId,
                                )
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load board"))
                    }
                }
        }
    }

    fun setAuthor(memberId: String) {
        _state.update { it.copy(authorMemberId = memberId) }
    }

    fun setDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun submit() {
        val s = _state.value
        val authorId = s.authorMemberId ?: return
        val body = s.draft.trim()
        if (body.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isPosting = true, error = null) }
            runCatching {
                api.createMessage(
                    MessageCreate(
                        body = body,
                        boardKind = boardKind,
                        boardMemberId = boardMemberId,
                        authorMemberId = authorId,
                    )
                )
            }
                .onSuccess {
                    _state.update { it.copy(isPosting = false, draft = "") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isPosting = false, error = e.toUserMessage("Couldn't post"))
                    }
                }
        }
    }
}
