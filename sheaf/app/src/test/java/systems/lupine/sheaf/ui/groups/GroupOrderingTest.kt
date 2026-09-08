package systems.lupine.sheaf.ui.groups

import systems.lupine.sheaf.data.model.GroupRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The order groups are listed in, and what one arrow tap sends.
 *
 * The server assigns `order = position in the list it is sent`, so the id list
 * these produce IS the stored order. Getting it wrong does not fail loudly; it
 * quietly rearranges somebody's groups.
 */
class GroupOrderingTest {

    private fun group(
        id: String,
        name: String,
        order: Int = 0,
        parentId: String? = null,
    ) = GroupRead(
        id = id,
        systemId = "s1",
        name = name,
        description = null,
        color = null,
        parentId = parentId,
        order = order,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun ids(groups: List<GroupRead>) =
        orderGroupsHierarchically(groups).map { (g, _) -> g.id }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Test fun `a system that never reordered anything stays alphabetical`() {
        // The server backfills every existing group to 0, so the name tiebreak
        // is the whole of the ordering for anybody who has not touched it.
        val groups = listOf(group("c", "Charlie"), group("a", "Alpha"), group("b", "Bravo"))
        assertEquals(listOf("a", "b", "c"), ids(groups))
    }

    @Test fun `order beats name`() {
        val groups = listOf(group("a", "Alpha", order = 2), group("b", "Bravo", order = 1))
        assertEquals(listOf("b", "a"), ids(groups))
    }

    @Test fun `children follow their parent, ordered among themselves`() {
        val groups = listOf(
            group("p2", "Parent two", order = 1),
            group("p1", "Parent one", order = 0),
            group("c2", "Child two", order = 1, parentId = "p1"),
            group("c1", "Child one", order = 0, parentId = "p1"),
        )
        assertEquals(listOf("p1", "c1", "c2", "p2"), ids(groups))
    }

    @Test fun `an orphan is listed rather than dropped`() {
        // A search filter can strand a child whose parent didn't match.
        val groups = listOf(group("c", "Child", parentId = "missing"), group("a", "Alpha"))
        assertEquals(listOf("a", "c"), ids(groups))
    }

    // ── Moving ────────────────────────────────────────────────────────────────

    @Test fun `moving down swaps with the next sibling`() {
        val groups = listOf(group("a", "Alpha"), group("b", "Bravo"), group("c", "Charlie"))
        assertEquals(listOf("b", "a", "c"), reorderedGroupIds(groups, "a", 1))
    }

    @Test fun `moving up swaps with the previous sibling`() {
        val groups = listOf(group("a", "Alpha"), group("b", "Bravo"), group("c", "Charlie"))
        assertEquals(listOf("a", "c", "b"), reorderedGroupIds(groups, "c", -1))
    }

    @Test fun `the ends do not move`() {
        val groups = listOf(group("a", "Alpha"), group("b", "Bravo"))
        assertNull(reorderedGroupIds(groups, "a", -1))
        assertNull(reorderedGroupIds(groups, "b", 1))
        assertNull(reorderedGroupIds(groups, "nope", 1))
    }

    @Test fun `a move sends every group, not just the pair`() {
        // The server sets order from list position, so a partial list would
        // renumber the sent groups against an order the rest still hold.
        val groups = listOf(
            group("p", "Parent"),
            group("c1", "Child one", order = 0, parentId = "p"),
            group("c2", "Child two", order = 1, parentId = "p"),
            group("z", "Zulu"),
        )
        assertEquals(listOf("p", "c2", "c1", "z"), reorderedGroupIds(groups, "c2", -1))
    }

    @Test fun `a child moves among its siblings, not into another parent`() {
        // Reparenting is a different operation, and one this list does not
        // offer: a move must never change who a group belongs to.
        val groups = listOf(
            group("p1", "Parent one", order = 0),
            group("p2", "Parent two", order = 1),
            group("a", "A child", order = 0, parentId = "p1"),
            group("b", "B child", order = 1, parentId = "p1"),
            group("x", "X child", order = 0, parentId = "p2"),
        )
        val moved = reorderedGroupIds(groups, "b", -1)
        assertEquals(listOf("p1", "b", "a", "p2", "x"), moved)
    }

    @Test fun `a root moving down carries its children with it`() {
        // The list is a tree, so a root and its subtree move as one block.
        val groups = listOf(
            group("p1", "Parent one", order = 0),
            group("c", "Child", parentId = "p1"),
            group("p2", "Parent two", order = 1),
        )
        assertEquals(listOf("p2", "p1", "c"), reorderedGroupIds(groups, "p1", 1))
    }
}
