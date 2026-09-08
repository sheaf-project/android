package systems.lupine.sheaf.ui.fields

import systems.lupine.sheaf.data.model.CustomFieldRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What one arrow tap sends for a custom field. The server sets each field's
 * order from its position in this list, and that order is what a member's
 * profile and any shared page show their fields in.
 */
class FieldOrderingTest {

    private fun field(id: String, order: Int) = CustomFieldRead(
        id = id,
        systemId = "s1",
        name = "Field $id",
        fieldType = "text",
        order = order,
        privacy = "private",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private val fields = listOf(field("a", 0), field("b", 1), field("c", 2))

    @Test fun `moving down swaps with the next field`() {
        assertEquals(listOf("b", "a", "c"), reorderedFieldIds(fields, 0, 1))
    }

    @Test fun `moving up swaps with the previous field`() {
        assertEquals(listOf("a", "c", "b"), reorderedFieldIds(fields, 2, -1))
    }

    @Test fun `the ends do not move`() {
        assertNull(reorderedFieldIds(fields, 0, -1))
        assertNull(reorderedFieldIds(fields, 2, 1))
    }

    @Test fun `an index off the list moves nothing`() {
        assertNull(reorderedFieldIds(fields, 7, -1))
        assertNull(reorderedFieldIds(emptyList(), 0, 1))
    }

    @Test fun `every field is sent, not just the moved pair`() {
        // A partial list would renumber the fields it names against an order
        // the rest still hold, which is how a reorder ends up interleaved.
        assertEquals(3, reorderedFieldIds(fields, 1, 1)!!.size)
        assertEquals(fields.map { it.id }.toSet(), reorderedFieldIds(fields, 1, 1)!!.toSet())
    }
}
