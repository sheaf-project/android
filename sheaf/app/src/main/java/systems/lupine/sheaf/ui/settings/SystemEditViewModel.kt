package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SystemUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemEditForm(
    val name: String = "",
    val description: String = "",
    val tag: String = "",
    val avatarUrl: String = "",
    val color: String = "",
    val privacy: String = "private",
)

data class SystemEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SystemEditViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemEditUiState())
    val state: StateFlow<SystemEditUiState> = _state.asStateFlow()

    private val _form = MutableStateFlow(SystemEditForm())
    val form: StateFlow<SystemEditForm> = _form.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getOwnSystem() }
                .onSuccess { system ->
                    _form.value = SystemEditForm(
                        name = system.name,
                        description = system.description ?: "",
                        tag = system.tag ?: "",
                        avatarUrl = system.avatarUrl ?: "",
                        color = system.color ?: "",
                        privacy = system.privacy,
                    )
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun updateForm(update: SystemEditForm.() -> SystemEditForm) {
        _form.update { it.update() }
    }

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                api.updateOwnSystem(SystemUpdate(
                    name = f.name.takeIf { it.isNotBlank() },
                    description = f.description.takeIf { it.isNotBlank() },
                    tag = f.tag.takeIf { it.isNotBlank() },
                    avatarUrl = f.avatarUrl.takeIf { it.isNotBlank() },
                    color = f.color.takeIf { it.isNotBlank() },
                    privacy = f.privacy,
                ))
            }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.message) } }
        }
    }
}
