package systems.lupine.sheaf.ui.fields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.CustomFieldCreate
import systems.lupine.sheaf.data.model.CustomFieldRead
import systems.lupine.sheaf.data.model.CustomFieldUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomFieldsUiState(
    val fields: List<CustomFieldRead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class CustomFieldsViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomFieldsUiState(isLoading = true))
    val state: StateFlow<CustomFieldsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listFields() }
                .onSuccess { fields -> _state.update { it.copy(fields = fields, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun createField(name: String, fieldType: String, privacy: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                api.createField(CustomFieldCreate(
                    name = name,
                    fieldType = fieldType,
                    order = _state.value.fields.size,
                    privacy = privacy,
                ))
            }.onSuccess { field ->
                _state.update { it.copy(fields = it.fields + field, isSaving = false) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateField(id: String, name: String, privacy: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { api.updateField(id, CustomFieldUpdate(name = name, privacy = privacy)) }
                .onSuccess { updated ->
                    _state.update { s ->
                        s.copy(
                            fields = s.fields.map { if (it.id == id) updated else it },
                            isSaving = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }

    fun deleteField(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteField(id) }
                .onSuccess { _state.update { it.copy(fields = it.fields.filterNot { f -> f.id == id }) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
