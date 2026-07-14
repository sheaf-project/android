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
    /** When non-null, [submit] sets parent_message_id on the new post so
     *  the server records it as a reply. UI shows a "Replying to X" banner
     *  above the composer and an × to clear. Mirrors web's `replyTo`. */
    val replyTo: MessageRead? = null,
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
                // Caller = the current fronter (who the board is read/posted as),
                // matching the boards list. Falls back to the first member only
                // when nothing is fronting. Using firstOrNull() here marked the
                // wrong member's board seen and defaulted new posts to them.
                val caller = runCatching {
                    api.getCurrentFronts().firstOrNull()?.memberIds?.firstOrNull()
                }.getOrNull() ?: members.firstOrNull()?.id
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

    /**
     * Set or clear the message that the composer's next post will reply to.
     * Passing null clears the reply context — composer reverts to top-level
     * post mode.
     */
    fun setReplyTo(message: MessageRead?) {
        _state.update { it.copy(replyTo = message) }
    }

    fun submit() {
        val s = _state.value
        val authorId = s.authorMemberId ?: return
        val body = s.draft.trim()
        if (body.isBlank()) return
        // Capture parent id from state once so a concurrent setReplyTo
        // can't change what we end up posting under.
        val parentMessageId = s.replyTo?.id
        viewModelScope.launch {
            _state.update { it.copy(isPosting = true, error = null) }
            runCatching {
                api.createMessage(
                    MessageCreate(
                        body = body,
                        boardKind = boardKind,
                        boardMemberId = boardMemberId,
                        authorMemberId = authorId,
                        parentMessageId = parentMessageId,
                    )
                )
            }
                .onSuccess {
                    _state.update {
                        it.copy(isPosting = false, draft = "", replyTo = null)
                    }
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
