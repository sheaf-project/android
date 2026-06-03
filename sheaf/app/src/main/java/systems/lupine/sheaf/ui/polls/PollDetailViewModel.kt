package systems.lupine.sheaf.ui.polls

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
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.PollRead
import systems.lupine.sheaf.data.model.VoteCast
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class PollDetailUiState(
    val isLoading: Boolean = false,
    val isVoting: Boolean = false,
    val poll: PollRead? = null,
    val members: List<MemberRead> = emptyList(),
    /** Selected option ids the user has staged for their vote (not yet sent). */
    val selectedOptionIds: Set<String> = emptySet(),
    /** Which member they're voting as. Defaults to the first member. */
    val votedAsMemberId: String? = null,
    /** Member ids currently fronting (any front, any member). Used to
     *  gate the vote button when the poll has
     *  restrict_voting_to_fronters set. Empty until the first refresh. */
    val frontingMemberIds: Set<String> = emptySet(),
    val error: String? = null,
    val saved: Boolean = false,
) {
    /** True when the poll restricts to fronters and the chosen
     *  voted-as member isn't currently in the front. Used both to
     *  disable the vote button and to show an inline explanation. */
    val voterBlockedByRestriction: Boolean
        get() = poll?.restrictVotingToFronters == true
            && votedAsMemberId != null
            && votedAsMemberId !in frontingMemberIds
}

@HiltViewModel
class PollDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pollId: String = checkNotNull(savedStateHandle["pollId"])

    private val _state = MutableStateFlow(PollDetailUiState(isLoading = true))
    val state: StateFlow<PollDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val poll = api.getPoll(pollId)
                val members = runCatching { api.listMembers() }.getOrDefault(emptyList())
                // Fronts only matter when the poll restricts voting; skip
                // the call otherwise so we don't fan out a /fronts/current
                // request on every poll open.
                val fronting = if (poll.restrictVotingToFronters) {
                    runCatching { api.getCurrentFronts() }.getOrDefault(emptyList())
                        .flatMap { it.memberIds }
                        .toSet()
                } else emptySet()
                Triple(poll, members, fronting)
            }
                .onSuccess { (poll, members, fronting) ->
                    _state.update {
                        // Prefer the first member that already voted (so the user
                        // can see and amend their existing vote) if the owner has
                        // visibility into votes. Otherwise default to the first
                        // member alphabetically — user can swap.
                        val ownVoteMemberId = poll.votes?.firstOrNull()?.votedAsMemberId
                        val defaultMember = ownVoteMemberId ?: members.firstOrNull()?.id
                        val staged = poll.votes
                            ?.firstOrNull { v -> v.votedAsMemberId == defaultMember }
                            ?.optionIds
                            ?.toSet()
                            .orEmpty()
                        it.copy(
                            isLoading = false,
                            poll = poll,
                            members = members,
                            frontingMemberIds = fronting,
                            votedAsMemberId = it.votedAsMemberId ?: defaultMember,
                            selectedOptionIds = if (it.selectedOptionIds.isNotEmpty()) it.selectedOptionIds else staged,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load poll"))
                    }
                }
        }
    }

    fun setVotedAsMember(memberId: String) {
        // When the user changes which member they're voting as, also seed
        // selectedOptionIds from that member's existing vote if known.
        val poll = _state.value.poll
        val existing = poll?.votes
            ?.firstOrNull { it.votedAsMemberId == memberId }
            ?.optionIds
            ?.toSet()
            .orEmpty()
        _state.update {
            it.copy(votedAsMemberId = memberId, selectedOptionIds = existing)
        }
    }

    fun toggleOption(optionId: String) {
        val poll = _state.value.poll ?: return
        _state.update { s ->
            val nextSet = if (poll.kind == "single_choice") {
                if (optionId in s.selectedOptionIds) emptySet() else setOf(optionId)
            } else {
                if (optionId in s.selectedOptionIds) s.selectedOptionIds - optionId
                else s.selectedOptionIds + optionId
            }
            s.copy(selectedOptionIds = nextSet)
        }
    }

    fun submitVote() {
        val s = _state.value
        val poll = s.poll ?: return
        val memberId = s.votedAsMemberId ?: return
        if (s.selectedOptionIds.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isVoting = true, error = null) }
            runCatching {
                api.castVote(
                    poll.id,
                    VoteCast(votedAsMemberId = memberId, optionIds = s.selectedOptionIds.toList()),
                )
            }
                .onSuccess {
                    _state.update { it.copy(isVoting = false, saved = true) }
                    refresh()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isVoting = false, error = e.toUserMessage("Couldn't submit vote"))
                    }
                }
        }
    }

    fun withdrawVote() {
        val s = _state.value
        val poll = s.poll ?: return
        val memberId = s.votedAsMemberId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isVoting = true, error = null) }
            runCatching { api.withdrawVote(poll.id, memberId) }
                .onSuccess {
                    _state.update { it.copy(isVoting = false, selectedOptionIds = emptySet()) }
                    refresh()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isVoting = false, error = e.toUserMessage("Couldn't withdraw vote"))
                    }
                }
        }
    }
}
