package systems.lupine.sheaf.ui.fields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.ui.sharing.ShareError
import systems.lupine.sheaf.ui.sharing.loadRaiseGate
import systems.lupine.sheaf.ui.sharing.message
import systems.lupine.sheaf.ui.sharing.toShareError
import systems.lupine.sheaf.data.model.CustomFieldCreate
import systems.lupine.sheaf.data.model.CustomFieldOptions
import systems.lupine.sheaf.data.model.CustomFieldRead
import systems.lupine.sheaf.data.model.CustomFieldReorder
import systems.lupine.sheaf.data.model.CustomFieldUpdate
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomFieldsUiState(
    val fields: List<CustomFieldRead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false,
    /** A reorder is in flight; the arrows go quiet until it lands. */
    val isReordering: Boolean = false,
    val raiseGate: systems.lupine.sheaf.ui.sharing.RaiseGate =
        systems.lupine.sheaf.ui.sharing.RaiseGate(),
    // The edit waiting on credentials, replayed verbatim once they arrive.
    val stepUpEdit: PendingFieldEdit? = null,
    val stepUpError: String? = null,
)

data class PendingFieldEdit(
    val id: String,
    val name: String,
    val privacy: String,
    val choices: List<String>?,
    val fieldType: String?,
)

/**
 * The whole ordered id list after moving one field a place up or down, or null
 * when it cannot move that way.
 *
 * The full list rather than the moved pair, because the server assigns
 * order = position in whatever list it is sent: sending everything is what
 * makes the stored order match the screen exactly.
 */
internal fun reorderedFieldIds(
    fields: List<CustomFieldRead>,
    index: Int,
    delta: Int,
): List<String>? {
    val to = index + delta
    if (index !in fields.indices || to !in fields.indices) return null
    val moved = fields.map { it.id }.toMutableList()
    moved[index] = fields[to].id
    moved[to] = fields[index].id
    return moved
}

@HiltViewModel
class CustomFieldsViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomFieldsUiState(isLoading = true))
    val state: StateFlow<CustomFieldsUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { _state.update { it.copy(raiseGate = api.loadRaiseGate()) } }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listFields() }
                .onSuccess { fields -> _state.update { it.copy(fields = fields, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    /**
     * Move a field a place up or down the list.
     *
     * The response is the list in its new order, so it becomes the new state
     * rather than triggering a refetch. Not optimistic: the arrows are a
     * rapid-fire control and a list that springs back on a failed call is
     * worse than one that waits.
     */
    fun moveField(index: Int, delta: Int) {
        if (_state.value.isReordering) return
        val ids = reorderedFieldIds(_state.value.fields, index, delta) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isReordering = true, error = null) }
            runCatching { api.reorderFields(CustomFieldReorder(ids)) }
                .onSuccess { fields ->
                    _state.update { it.copy(fields = fields, isReordering = false) }
                }
                .onFailure { e ->
                    // See the groups view model: 404 or 405 is an older server,
                    // not a failure the user can do anything about by retrying.
                    val message = if (e is HttpException && e.code() in setOf(404, 405)) {
                        "This server doesn't support reordering fields yet."
                    } else {
                        e.toUserMessage("Couldn't reorder fields")
                    }
                    _state.update { it.copy(isReordering = false, error = message) }
                }
        }
    }

    fun createField(
        name: String,
        fieldType: String,
        privacy: String,
        choices: List<String>? = null,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                api.createField(CustomFieldCreate(
                    name = name,
                    fieldType = fieldType,
                    options = choices.toOptionsOrNull(fieldType),
                    order = _state.value.fields.size,
                    privacy = privacy,
                ))
            }.onSuccess { field ->
                _state.update { it.copy(fields = it.fields + field, isSaving = false) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            }
        }
    }

    fun updateField(
        id: String,
        name: String,
        privacy: String,
        choices: List<String>? = null,
        fieldType: String? = null,
    ) {
        val current = _state.value.fields.find { it.id == id }?.privacy
        val edit = PendingFieldEdit(id, name, privacy, choices, fieldType)
        if (systems.lupine.sheaf.ui.sharing.isRaiseToPublic(current, privacy) &&
            _state.value.raiseGate.stepUpNeeded
        ) {
            _state.update { it.copy(stepUpEdit = edit, stepUpError = null) }
            return
        }
        submitField(edit, null, null)
    }

    fun confirmStepUp(password: String?, totpCode: String?) {
        val edit = _state.value.stepUpEdit ?: return
        submitField(edit, password, totpCode)
    }

    fun dismissStepUp() { _state.update { it.copy(stepUpEdit = null, stepUpError = null) } }

    private fun submitField(edit: PendingFieldEdit, password: String?, totpCode: String?) {
        val id = edit.id
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, stepUpError = null) }
            runCatching {
                api.updateField(
                    id,
                    CustomFieldUpdate(
                        name = edit.name,
                        privacy = edit.privacy,
                        // Carry options only for SELECT/MULTISELECT.
                        // Non-choice field types: backend rejects a
                        // non-null options dict with 422.
                        options = edit.choices.toOptionsOrNull(edit.fieldType),
                        password = password?.ifBlank { null },
                        totpCode = totpCode?.ifBlank { null },
                    ),
                )
            }
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(
                            fields = s.fields.map { if (it.id == id) updated else it },
                            isSaving = false,
                            stepUpEdit = null,
                            stepUpError = null,
                        )
                    }
                }
                .onFailure { e ->
                    when (val err = e.toShareError("Couldn't save this field")) {
                        is ShareError.StepUpRequired -> _state.update {
                            it.copy(isSaving = false, stepUpEdit = edit, stepUpError = null)
                        }
                        is ShareError.BadCredentials -> _state.update {
                            it.copy(isSaving = false, stepUpEdit = edit, stepUpError = err.message)
                        }
                        else -> _state.update {
                            it.copy(
                                isSaving = false,
                                stepUpEdit = null,
                                error = err.message("Couldn't save this field"),
                            )
                        }
                    }
                }
        }
    }

    /**
     * Build the options dict to send, given the user-edited list of
     * choices and the field's type. Rules mirror what backend will
     * accept: only SELECT/MULTISELECT carry options, and a null
     * choices list signals "freeform tag mode" (server omits options).
     */
    private fun List<String>?.toOptionsOrNull(fieldType: String?): CustomFieldOptions? {
        if (fieldType != "select" && fieldType != "multiselect") return null
        val cleaned = this?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        if (cleaned.isEmpty()) return null
        return CustomFieldOptions(choices = cleaned)
    }

    fun deleteField(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteField(id) }
                .onSuccess { _state.update { it.copy(fields = it.fields.filterNot { f -> f.id == id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
