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
import systems.lupine.sheaf.data.model.RelationshipEdgeCreate
import systems.lupine.sheaf.data.model.RelationshipFromViewpoint
import systems.lupine.sheaf.data.model.RelationshipTypeRead
import systems.lupine.sheaf.data.model.SYMMETRY_EITHER
import systems.lupine.sheaf.data.model.SYMMETRY_SYMMETRIC
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

const val REL_SCOPE_MEMBER = "member"
const val REL_SCOPE_GROUP = "group"

// A pickable other endpoint (member or group) in the account.
data class RelationshipNodeRef(val id: String, val name: String)

data class RelationshipsEditorUiState(
    val isLoading: Boolean = true,
    val relationships: List<RelationshipFromViewpoint> = emptyList(),
    val types: List<RelationshipTypeRead> = emptyList(),
    // Other nodes that can be picked as a counterparty (self excluded).
    val candidates: List<RelationshipNodeRef> = emptyList(),
    // Every node's name (incl. self / archived) so an edge's other endpoint always resolves.
    val nameById: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * Backs [RelationshipsEditor] for one member or group. Loads that node's
 * relationships (already resolved to per-viewpoint labels + direction by the
 * server), the relationship types, and the pickable counterparties.
 */
@HiltViewModel
class RelationshipsEditorViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(RelationshipsEditorUiState())
    val state: StateFlow<RelationshipsEditorUiState> = _state.asStateFlow()

    private var scope: String = REL_SCOPE_MEMBER
    private var nodeId: String = ""
    private var loadedFor: Pair<String, String>? = null

    fun load(scope: String, nodeId: String) {
        if (loadedFor == scope to nodeId) return
        this.scope = scope
        this.nodeId = nodeId
        loadedFor = scope to nodeId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val relationships = runCatching {
                if (scope == REL_SCOPE_GROUP) api.getGroupRelationships(nodeId)
                else api.getMemberRelationships(nodeId)
            }
            val types = runCatching { api.listRelationshipTypes() }.getOrDefault(emptyList())
            // Candidate other-nodes + a name map for resolving edge endpoints.
            val (candidates, nameById) = runCatching {
                if (scope == REL_SCOPE_GROUP) {
                    val groups = api.listGroups()
                    val names = groups.associate { it.id to it.name }
                    val picks = groups.filter { it.id != nodeId }
                        .map { RelationshipNodeRef(it.id, it.name) }
                    picks to names
                } else {
                    val members = api.listMembers()
                    val names = members.associate { it.id to it.displayNameOrName }
                    val picks = members.filter { it.id != nodeId && !it.isArchived }
                        .map { RelationshipNodeRef(it.id, it.displayNameOrName) }
                    picks to names
                }
            }.getOrDefault(emptyList<RelationshipNodeRef>() to emptyMap())

            relationships
                .onSuccess { rels ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            relationships = rels,
                            types = types,
                            candidates = candidates.sortedBy { c -> c.name.lowercase() },
                            nameById = nameById,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load relationships")) }
                }
        }
    }

    fun add(edge: RelationshipEdgeCreate) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                if (scope == REL_SCOPE_GROUP) api.createGroupRelationship(edge)
                else api.createMemberRelationship(edge)
            }
                .onSuccess { reloadRelationships() }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage("Couldn't add relationship")) }
                }
        }
    }

    fun remove(edgeId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching {
                if (scope == REL_SCOPE_GROUP) api.deleteGroupRelationship(edgeId)
                else api.deleteMemberRelationship(edgeId)
            }
                .onSuccess { reloadRelationships() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't remove relationship")) }
                }
        }
    }

    // Re-fetch just this node's edges (labels/direction are server-resolved).
    private suspend fun reloadRelationships() {
        runCatching {
            if (scope == REL_SCOPE_GROUP) api.getGroupRelationships(nodeId)
            else api.getMemberRelationships(nodeId)
        }
            .onSuccess { rels -> _state.update { it.copy(isSaving = false, relationships = rels) } }
            .onFailure { _state.update { it.copy(isSaving = false) } }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

/**
 * Build the edge-create payload from the editor's selections, mirroring web's
 * handleAdd. Pure so it can be unit-tested.
 *
 * @param forwardDirection true when *this* node is the forward-label side
 *   (source). Ignored for symmetric types and for mutual `either` edges.
 */
fun buildRelationshipEdge(
    nodeId: String,
    otherId: String,
    type: RelationshipTypeRead,
    forwardDirection: Boolean,
    mutual: Boolean,
): RelationshipEdgeCreate {
    val effectiveMutual = mutual && type.symmetry == SYMMETRY_EITHER
    return when {
        // Symmetric: order is irrelevant; the server canonicalises.
        type.symmetry == SYMMETRY_SYMMETRIC ->
            RelationshipEdgeCreate(sourceId = nodeId, targetId = otherId, relationshipTypeId = type.id)
        // Mutual either: both ends read the forward label; direction is dropped.
        effectiveMutual ->
            RelationshipEdgeCreate(sourceId = nodeId, targetId = otherId, relationshipTypeId = type.id, mutual = true)
        // Directional / either forward: this node is the source.
        forwardDirection ->
            RelationshipEdgeCreate(sourceId = nodeId, targetId = otherId, relationshipTypeId = type.id)
        // Reverse: swap so the other node is the source (the forward-label side).
        else ->
            RelationshipEdgeCreate(sourceId = otherId, targetId = nodeId, relationshipTypeId = type.id)
    }
}
