package systems.lupine.sheaf.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.deleteTagOrQueue
import systems.lupine.sheaf.data.model.TagCreate
import systems.lupine.sheaf.data.model.TagRead
import systems.lupine.sheaf.data.model.TagUpdate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

// Step-up auth + grace-period state for tag delete. Backend gate is the
// `applies_to_tags` System Safety flag; auth tier is the system-wide one.
data class TagDeleteSafety(
    val authTier: String = "none",
    val totpEnabled: Boolean = false,
    val appliesToTags: Boolean = false,
    val gracePeriodDays: Int = 0,
)

data class TagsManagerUiState(
    val isLoading: Boolean = false,
    val tags: List<TagRead> = emptyList(),
    val error: String? = null,
    val isCreating: Boolean = false,
    val createError: String? = null,
    val editingTagId: String? = null,
    val isUpdating: Boolean = false,
    val pendingDelete: TagRead? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val resultMessage: String? = null,
    val safety: TagDeleteSafety = TagDeleteSafety(),
)

@HiltViewModel
class TagsManagerViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(TagsManagerUiState(isLoading = true))
    val state: StateFlow<TagsManagerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val tags = api.listTags().sortedBy { it.name.lowercase() }
                val safety = runCatching {
                    val s = api.getSystemSafety()
                    val u = runCatching { api.getMe() }.getOrNull()
                    TagDeleteSafety(
                        authTier = s.settings.authTier,
                        totpEnabled = u?.totpEnabled == true,
                        appliesToTags = s.settings.appliesToTags,
                        gracePeriodDays = s.settings.gracePeriodDays,
                    )
                }.getOrDefault(TagDeleteSafety())
                tags to safety
            }
                .onSuccess { (tags, safety) ->
                    _state.update { it.copy(tags = tags, safety = safety, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load tags")) }
                }
        }
    }

    fun createTag(name: String, color: String?) {
        if (name.isBlank()) return
        _state.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            runCatching { api.createTag(TagCreate(name = name.trim(), color = color)) }
                .onSuccess { tag ->
                    _state.update {
                        it.copy(
                            isCreating = false,
                            tags = (it.tags + tag).sortedBy { t -> t.name.lowercase() },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isCreating = false, createError = e.toUserMessage("Failed to create tag")) }
                }
        }
    }

    fun startEdit(tagId: String) { _state.update { it.copy(editingTagId = tagId) } }
    fun cancelEdit() { _state.update { it.copy(editingTagId = null) } }

    fun updateTag(id: String, name: String, color: String?) {
        if (name.isBlank()) return
        _state.update { it.copy(isUpdating = true) }
        viewModelScope.launch {
            runCatching { api.updateTag(id, TagUpdate(name = name.trim(), color = color)) }
                .onSuccess { updated ->
                    _state.update {
                        it.copy(
                            isUpdating = false,
                            editingTagId = null,
                            tags = it.tags.map { t -> if (t.id == id) updated else t }
                                .sortedBy { t -> t.name.lowercase() },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUpdating = false, error = e.toUserMessage("Failed to update tag")) }
                }
        }
    }

    fun openDelete(tag: TagRead) { _state.update { it.copy(pendingDelete = tag, deleteError = null) } }
    fun closeDelete() { _state.update { it.copy(pendingDelete = null, deleteError = null) } }
    fun clearResult() { _state.update { it.copy(resultMessage = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    fun confirmDelete(password: String?, totpCode: String?) {
        val tag = _state.value.pendingDelete ?: return
        _state.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            runCatching { api.deleteTagOrQueue(tag.id, password, totpCode) }
                .onSuccess { pending ->
                    val grace = _state.value.safety.gracePeriodDays
                    val msg = if (pending?.pendingActionId != null) {
                        "Tag \"${tag.name}\" queued for deletion in $grace day${if (grace == 1) "" else "s"}. " +
                            "Cancel from System Safety before then."
                    } else {
                        "Tag \"${tag.name}\" deleted."
                    }
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            pendingDelete = null,
                            tags = if (pending?.pendingActionId != null) it.tags
                                   else it.tags.filterNot { t -> t.id == tag.id },
                            resultMessage = msg,
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else e.toUserMessage("Failed to delete tag")
                    _state.update { it.copy(isDeleting = false, deleteError = msg) }
                }
        }
    }
}
