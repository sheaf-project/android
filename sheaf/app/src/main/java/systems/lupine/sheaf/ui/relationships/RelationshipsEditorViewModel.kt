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
import systems.lupine.sheaf.data.model.RelationshipEdgeUpdate
import systems.lupine.sheaf.ui.sharing.ShareError
import systems.lupine.sheaf.ui.sharing.loadRaiseGate
import systems.lupine.sheaf.ui.sharing.message
import systems.lupine.sheaf.ui.sharing.toShareError
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
    val raiseGate: systems.lupine.sheaf.ui.sharing.RaiseGate =
        systems.lupine.sheaf.ui.sharing.RaiseGate(),
    // The edge whose raise is waiting on credentials.
    val stepUpEdgeId: String? = null,
    val stepUpVisibility: String? = null,
    val stepUpError: String? = null,
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
                    // Only latch on success. Latching before the request meant one
                    // failed load (offline, 5xx) suppressed every later attempt for
                    // this node, with no way back short of leaving the screen.
                    loadedFor = scope to nodeId
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

    /** Re-run a load that failed (the failed attempt is not latched, so this retries). */
    fun retry() = load(scope, nodeId)

    fun loadRaiseGate() {
        viewModelScope.launch {
            _state.update { it.copy(raiseGate = api.loadRaiseGate()) }
        }
    }

    /**
     * Move one edge up or down the privacy ladder.
     *
     * Only a raise to public on a MEMBER edge can be gated: no share view flag
     * reaches group edges and the projection never queries that table, so those
     * are always instant.
     */
    fun setVisibility(edgeId: String, visibility: String) {
        val current = _state.value.relationships.find { it.id == edgeId }?.visibility
        val gated = scope != REL_SCOPE_GROUP &&
            systems.lupine.sheaf.ui.sharing.isRaiseToPublic(current, visibility)
        if (gated && _state.value.raiseGate.stepUpNeeded) {
            _state.update {
                it.copy(stepUpEdgeId = edgeId, stepUpVisibility = visibility, stepUpError = null)
            }
            return
        }
        submitVisibility(edgeId, visibility, null, null)
    }

    fun confirmStepUp(password: String?, totpCode: String?) {
        val id = _state.value.stepUpEdgeId ?: return
        val visibility = _state.value.stepUpVisibility ?: return
        submitVisibility(id, visibility, password, totpCode)
    }

    fun dismissStepUp() {
        _state.update { it.copy(stepUpEdgeId = null, stepUpVisibility = null, stepUpError = null) }
    }

    private fun submitVisibility(
        edgeId: String,
        visibility: String,
        password: String?,
        totpCode: String?,
    ) {
        val body = RelationshipEdgeUpdate(
            visibility = visibility,
            password = password?.ifBlank { null },
            totpCode = totpCode?.ifBlank { null },
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, stepUpError = null) }
            runCatching {
                if (scope == REL_SCOPE_GROUP) api.updateGroupRelationship(edgeId, body)
                else api.updateMemberRelationship(edgeId, body)
            }
                .onSuccess {
                    _state.update {
                        it.copy(stepUpEdgeId = null, stepUpVisibility = null, stepUpError = null)
                    }
                    reloadRelationships()
                }
                .onFailure { e ->
                    when (val err = e.toShareError("Couldn't change who can see this")) {
                        is ShareError.StepUpRequired -> _state.update {
                            it.copy(
                                isSaving = false,
                                stepUpEdgeId = edgeId,
                                stepUpVisibility = visibility,
                                stepUpError = null,
                            )
                        }
                        is ShareError.BadCredentials -> _state.update {
                            it.copy(
                                isSaving = false,
                                stepUpEdgeId = edgeId,
                                stepUpVisibility = visibility,
                                stepUpError = err.message,
                            )
                        }
                        else -> _state.update {
                            it.copy(
                                isSaving = false,
                                stepUpEdgeId = null,
                                stepUpVisibility = null,
                                error = err.message("Couldn't change who can see this"),
                            )
                        }
                    }
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

    // Re-fetch just this node's edges (labels/direction are server-resolved). The
    // mutation itself already succeeded, so a failure here means the list on screen
    // is stale rather than wrong: say so instead of silently showing the old list.
    private suspend fun reloadRelationships() {
        runCatching {
            if (scope == REL_SCOPE_GROUP) api.getGroupRelationships(nodeId)
            else api.getMemberRelationships(nodeId)
        }
            .onSuccess { rels -> _state.update { it.copy(isSaving = false, relationships = rels) } }
            .onFailure { e ->
                _state.update {
                    it.copy(isSaving = false, error = e.toUserMessage("Saved, but couldn't refresh the list"))
                }
            }
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
