package systems.lupine.sheaf.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.*
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── List ──────────────────────────────────────────────────────────────────────

data class GroupsUiState(
    val groups: List<GroupRead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState(isLoading = true))
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.groups.isEmpty(), error = null) }
            runCatching { api.listGroups() }
                .onSuccess { groups -> _state.update { it.copy(groups = groups, isLoading = false) } }
                .onFailure { e -> _state.update { s -> s.copy(isLoading = false, error = if (s.groups.isEmpty()) e.toUserMessage() else s.error) } }
        }
    }
}

// ── Detail ────────────────────────────────────────────────────────────────────

data class GroupFormState(
    val name: String = "",
    val description: String = "",
    val color: String = "#534AB7",
)

data class GroupDetailUiState(
    val group: GroupRead? = null,
    val members: List<MemberRead> = emptyList(),
    val allMembers: List<MemberRead> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val showMemberSheet: Boolean = false,
    val memberSelection: Set<String> = emptySet(),
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rawId: String? = savedStateHandle["groupId"]
    val isNewGroup: Boolean = rawId == null || rawId == "new"
    private val groupId: String? = if (isNewGroup) null else rawId

    private val _state = MutableStateFlow(GroupDetailUiState(isLoading = !isNewGroup))
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private val _form = MutableStateFlow(GroupFormState())
    val form: StateFlow<GroupFormState> = _form.asStateFlow()

    init {
        if (!isNewGroup && groupId != null) load()
        loadAllMembers()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                val group   = api.getGroup(groupId!!)
                val members = api.getGroupMembers(groupId)
                group to members
            }.onSuccess { (group, members) ->
                _state.update {
                    it.copy(
                        group           = group,
                        members         = members,
                        isLoading       = false,
                        memberSelection = members.map { m -> m.id }.toSet(),
                    )
                }
                _form.value = GroupFormState(
                    name        = group.name,
                    description = group.description ?: "",
                    color       = group.color ?: "#534AB7",
                )
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    private fun loadAllMembers() {
        viewModelScope.launch {
            runCatching { api.listMembers() }
                .onSuccess { members -> _state.update { it.copy(allMembers = members) } }
        }
    }

    fun updateForm(update: GroupFormState.() -> GroupFormState) { _form.update(update) }

    fun save() {
        val f = _form.value
        if (f.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                if (isNewGroup) {
                    api.createGroup(GroupCreate(
                        name        = f.name.trim(),
                        description = f.description.takeIf { it.isNotBlank() },
                        color       = f.color.takeIf { it.isNotBlank() },
                    ))
                } else {
                    api.updateGroup(groupId!!, GroupUpdate(
                        name        = f.name.trim(),
                        description = f.description.takeIf { it.isNotBlank() },
                        color       = f.color.takeIf { it.isNotBlank() },
                    ))
                }
            }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.toUserMessage()) } }
        }
    }

    fun delete() {
        if (groupId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true) }
            runCatching { api.deleteGroup(groupId) }
                .onSuccess { _state.update { it.copy(isDeleting = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(isDeleting = false, error = e.toUserMessage()) } }
        }
    }

    fun openMemberSheet()  { _state.update { it.copy(showMemberSheet = true) } }
    fun closeMemberSheet() { _state.update { it.copy(showMemberSheet = false) } }

    fun toggleMember(id: String) {
        _state.update { s ->
            val sel = s.memberSelection.toMutableSet()
            if (id in sel) sel.remove(id) else sel.add(id)
            s.copy(memberSelection = sel)
        }
    }

    fun saveMembers() {
        if (groupId == null) return
        viewModelScope.launch {
            runCatching {
                api.setGroupMembers(
                    groupId,
                    GroupMemberUpdate(memberIds = _state.value.memberSelection.toList()),
                )
            }.onSuccess { members ->
                _state.update { it.copy(members = members, showMemberSheet = false) }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
            }
        }
    }
}
