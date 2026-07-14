package systems.lupine.sheaf.ui.relationships

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.RelationshipTypeCreate
import systems.lupine.sheaf.data.model.RelationshipTypeRead
import systems.lupine.sheaf.data.model.RelationshipTypeUpdate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class RelationshipTypesUiState(
    val isLoading: Boolean = true,
    val types: List<RelationshipTypeRead> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RelationshipTypesViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(RelationshipTypesUiState())
    val state: StateFlow<RelationshipTypesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listRelationshipTypes() }
                .onSuccess { types ->
                    _state.update { it.copy(isLoading = false, types = types.sortedBy { t -> t.name.lowercase() }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load relationship types")) }
                }
        }
    }

    fun create(body: RelationshipTypeCreate, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { api.createRelationshipType(body) }
                .onSuccess { created ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            types = (it.types + created).sortedBy { t -> t.name.lowercase() },
                        )
                    }
                    onDone()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage("Couldn't create relationship type")) }
                }
        }
    }

    fun update(id: String, body: RelationshipTypeUpdate, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { api.updateRelationshipType(id, body) }
                .onSuccess { updated ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            types = it.types.map { t -> if (t.id == id) updated else t }
                                .sortedBy { t -> t.name.lowercase() },
                        )
                    }
                    onDone()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage("Couldn't update relationship type")) }
                }
        }
    }

    // Deleting a type cascades server-side, removing every edge that used it.
    fun delete(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching { api.deleteRelationshipType(id) }
                .onSuccess {
                    _state.update { it.copy(types = it.types.filterNot { t -> t.id == id }) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete relationship type")) }
                }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
