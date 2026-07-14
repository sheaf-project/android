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
import systems.lupine.sheaf.data.model.RelationshipGraph
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

const val GRAPH_SCOPE_MEMBERS = "members"
const val GRAPH_SCOPE_GROUPS = "groups"

data class RelationshipGraphUiState(
    val isLoading: Boolean = true,
    val scope: String = GRAPH_SCOPE_MEMBERS,
    val graph: RelationshipGraph? = null,
    val error: String? = null,
)

@HiltViewModel
class RelationshipGraphViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(RelationshipGraphUiState())
    val state: StateFlow<RelationshipGraphUiState> = _state.asStateFlow()

    init { load(GRAPH_SCOPE_MEMBERS) }

    fun setScope(scope: String) {
        if (scope != _state.value.scope) load(scope)
    }

    fun load(scope: String = _state.value.scope) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, scope = scope, error = null) }
            runCatching { api.getRelationshipGraph(scope) }
                .onSuccess { graph -> _state.update { it.copy(isLoading = false, graph = graph) } }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load the graph")) }
                }
        }
    }
}
