package systems.lupine.sheaf.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// System relationships: a private, intra-system graph the user draws among
// their own members (or groups). Two layers - reusable relationship *types*
// (the vocabulary) and *edges* (links between two members / two groups). The
// server resolves per-viewpoint labels and direction, so the client renders
// what it's given rather than computing labels itself.

// ── Symmetry / direction / visibility values (string-typed per project convention) ──

const val SYMMETRY_SYMMETRIC = "symmetric"
const val SYMMETRY_DIRECTIONAL = "directional"
const val SYMMETRY_EITHER = "either"

const val REL_DIRECTION_NONE = "none"
const val REL_DIRECTION_OUTGOING = "outgoing"
const val REL_DIRECTION_INCOMING = "incoming"

const val REL_VISIBILITY_PRIVATE = "private"

// ── Relationship types ────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RelationshipTypeRead(
    val id: String,
    @Json(name = "system_id") val systemId: String,
    val name: String,
    val symmetry: String,
    @Json(name = "forward_label") val forwardLabel: String,
    @Json(name = "reverse_label") val reverseLabel: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
) {
    // One-line summary for list rows: just the label when symmetric, else the
    // forward -> reverse pair.
    val summary: String
        get() = if (symmetry == SYMMETRY_SYMMETRIC || reverseLabel.isNullOrBlank()) {
            forwardLabel
        } else {
            "$forwardLabel -> $reverseLabel"
        }
}

@JsonClass(generateAdapter = true)
data class RelationshipTypeCreate(
    val name: String,
    val symmetry: String,
    @Json(name = "forward_label") val forwardLabel: String,
    // Required by the server for directional/either; must be null for symmetric.
    @Json(name = "reverse_label") val reverseLabel: String? = null,
)

// Partial update. symmetry is immutable server-side (absent here on purpose).
// Null fields are dropped by Moshi codegen, giving PATCH semantics for free.
@JsonClass(generateAdapter = true)
data class RelationshipTypeUpdate(
    val name: String? = null,
    @Json(name = "forward_label") val forwardLabel: String? = null,
    @Json(name = "reverse_label") val reverseLabel: String? = null,
)

// ── Edges (members and groups share this shape) ───────────────────────────────

@JsonClass(generateAdapter = true)
data class RelationshipEdgeCreate(
    @Json(name = "source_id") val sourceId: String,
    @Json(name = "target_id") val targetId: String,
    @Json(name = "relationship_type_id") val relationshipTypeId: String,
    // Only meaningful for `either` types; the server forces it off otherwise.
    val mutual: Boolean = false,
    val visibility: String = REL_VISIBILITY_PRIVATE,
)

@JsonClass(generateAdapter = true)
data class RelationshipEdgeRead(
    val id: String,
    @Json(name = "source_id") val sourceId: String,
    @Json(name = "target_id") val targetId: String,
    @Json(name = "relationship_type_id") val relationshipTypeId: String,
    val mutual: Boolean,
    val visibility: String,
    @Json(name = "created_at") val createdAt: String,
)

// An edge as it reads from one node's viewpoint. `label` and `direction` are
// resolved by the server for the viewing node; `otherId` is the other endpoint.
@JsonClass(generateAdapter = true)
data class RelationshipFromViewpoint(
    val id: String,
    @Json(name = "relationship_type_id") val relationshipTypeId: String,
    @Json(name = "type_name") val typeName: String,
    @Json(name = "other_id") val otherId: String,
    val label: String,
    val direction: String,
    val mutual: Boolean,
    val visibility: String,
)

// ── Client-side presets ───────────────────────────────────────────────────────

// Template values to prefill the "new type" form. Systems start with no types;
// there is no server-seeded default set (mirrors web's RELATIONSHIP_PRESETS).
data class RelationshipPreset(
    val label: String,
    val name: String,
    val symmetry: String,
    val forwardLabel: String,
    val reverseLabel: String? = null,
)

val RELATIONSHIP_PRESETS: List<RelationshipPreset> = listOf(
    RelationshipPreset("Partner", "Partner", SYMMETRY_SYMMETRIC, "partner"),
    RelationshipPreset("Friend", "Friend", SYMMETRY_SYMMETRIC, "friend"),
    RelationshipPreset("Sibling", "Sibling", SYMMETRY_SYMMETRIC, "sibling"),
    RelationshipPreset("Parent / Child", "Parent", SYMMETRY_DIRECTIONAL, "parent", "child"),
    RelationshipPreset("Protector / Protectee", "Protector", SYMMETRY_EITHER, "protector", "protectee"),
    RelationshipPreset("Caretaker", "Caretaker", SYMMETRY_EITHER, "caretaker", "cared for"),
    RelationshipPreset("Split from", "Split", SYMMETRY_DIRECTIONAL, "split from", "split off"),
)
