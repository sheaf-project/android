package systems.lupine.sheaf.ui.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MemberTagUpdate
import systems.lupine.sheaf.data.model.TagRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class MemberTagsUiState(
    /** Every tag the system has, the pool to choose from. */
    val allTags: List<TagRead> = emptyList(),
    /** Ids currently on this member. */
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MemberTagsEditorViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(MemberTagsUiState())
    val state: StateFlow<MemberTagsUiState> = _state.asStateFlow()

    private var memberId: String? = null

    fun load(memberId: String) {
        // Guard against the LaunchedEffect re-firing on recomposition: a reload
        // mid-edit would stomp a toggle the user just made.
        if (this.memberId == memberId && !_state.value.isLoading) return
        this.memberId = memberId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // The tag vocabulary is best-effort: a viewer who can read this
            // member's tags but not list all of them still gets to see what is
            // set, just with nothing to add from.
            val all = runCatching { api.listTags() }.getOrDefault(emptyList())
            runCatching { api.getMemberTags(memberId) }
                .onSuccess { mine ->
                    _state.update {
                        it.copy(
                            allTags = all,
                            selected = mine.mapTo(mutableSetOf()) { t -> t.id },
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    /**
     * Add or remove one tag. The endpoint takes the member's whole tag set
     * rather than a delta, so send the result of the toggle.
     *
     * Applies optimistically and rolls back on failure: this is a chip the user
     * taps, and leaving it un-filled until a round-trip completes makes the tap
     * feel broken on a slow connection.
     */
    fun toggle(tagId: String) {
        val id = memberId ?: return
        val before = _state.value.selected
        val after = if (tagId in before) before - tagId else before + tagId
        _state.update { it.copy(selected = after, isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching { api.setMemberTags(id, MemberTagUpdate(tagIds = after.toList())) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            selected = saved.mapTo(mutableSetOf()) { t -> t.id },
                            isSaving = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(selected = before, isSaving = false, error = e.toUserMessage())
                    }
                }
        }
    }
}
