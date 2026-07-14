package systems.lupine.sheaf.ui.relationships

import systems.lupine.sheaf.data.model.REL_VISIBILITY_PRIVATE
import systems.lupine.sheaf.data.model.RelationshipTypeRead
import systems.lupine.sheaf.data.model.SYMMETRY_DIRECTIONAL
import systems.lupine.sheaf.data.model.SYMMETRY_EITHER
import systems.lupine.sheaf.data.model.SYMMETRY_SYMMETRIC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelationshipEdgePayloadTest {

    private fun type(symmetry: String) = RelationshipTypeRead(
        id = "type-1",
        systemId = "sys",
        name = "T",
        symmetry = symmetry,
        forwardLabel = "fwd",
        reverseLabel = if (symmetry == SYMMETRY_SYMMETRIC) null else "rev",
        createdAt = "",
        updatedAt = "",
    )

    private val node = "node-A"
    private val other = "node-B"

    @Test fun `symmetric keeps this node as source and no mutual`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_SYMMETRIC), forwardDirection = true, mutual = false)
        assertEquals(node, e.sourceId)
        assertEquals(other, e.targetId)
        assertFalse(e.mutual)
    }

    @Test fun `directional forward keeps this node as source`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_DIRECTIONAL), forwardDirection = true, mutual = false)
        assertEquals(node, e.sourceId)
        assertEquals(other, e.targetId)
        assertFalse(e.mutual)
    }

    @Test fun `directional reverse swaps so the other node is source`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_DIRECTIONAL), forwardDirection = false, mutual = false)
        assertEquals(other, e.sourceId)
        assertEquals(node, e.targetId)
        assertFalse(e.mutual)
    }

    @Test fun `either mutual sets mutual and does not swap`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_EITHER), forwardDirection = false, mutual = true)
        assertEquals(node, e.sourceId)
        assertEquals(other, e.targetId)
        assertTrue(e.mutual)
    }

    @Test fun `either directional reverse swaps and stays non-mutual`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_EITHER), forwardDirection = false, mutual = false)
        assertEquals(other, e.sourceId)
        assertEquals(node, e.targetId)
        assertFalse(e.mutual)
    }

    @Test fun `mutual is ignored for non-either types`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_DIRECTIONAL), forwardDirection = true, mutual = true)
        assertFalse(e.mutual)
    }

    @Test fun `visibility is always private`() {
        val e = buildRelationshipEdge(node, other, type(SYMMETRY_SYMMETRIC), forwardDirection = true, mutual = false)
        assertEquals(REL_VISIBILITY_PRIVATE, e.visibility)
    }
}
