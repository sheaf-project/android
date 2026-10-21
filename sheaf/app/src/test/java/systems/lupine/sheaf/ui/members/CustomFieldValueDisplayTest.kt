package systems.lupine.sheaf.ui.members

import systems.lupine.sheaf.data.model.CustomFieldOptions
import systems.lupine.sheaf.data.model.CustomFieldRead
import systems.lupine.sheaf.data.model.SliderLabels
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How a custom field's value reads on a member's profile, where there is no
 * room for a control and the value has to speak for itself.
 */
class CustomFieldValueDisplayTest {

    private fun field(
        type: String,
        options: CustomFieldOptions? = null,
    ) = CustomFieldRead(
        id = "f1",
        systemId = "s1",
        name = "Field",
        fieldType = type,
        options = options,
        order = 0,
        privacy = "private",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    @Test fun `a slider shows its reading, not its number`() {
        val labeled = field(
            "slider",
            CustomFieldOptions(
                mode = "labeled",
                labels = SliderLabels(left = "Calm", right = "Intense"),
            ),
        )
        assertEquals("62%", customFieldValueDisplay(labeled, 62))
    }

    @Test fun `a numeric slider shows its unit`() {
        val numeric = field(
            "slider",
            CustomFieldOptions(mode = "numeric", min = 120.0, max = 220.0, unit = "cm"),
        )
        assertEquals("172.5 cm", customFieldValueDisplay(numeric, 172.5))
    }

    @Test fun `a slider holding something other than a number shows it plainly`() {
        // A value carried over from when the field was text. Showing it is
        // better than showing nothing: it is what the member put there.
        assertEquals(
            "about sixty",
            customFieldValueDisplay(field("slider", CustomFieldOptions(mode = "labeled")), "about sixty"),
        )
    }

    @Test fun `a type this build does not know shows its value as text`() {
        // The contract in docs/CLIENT_DESIGN.md: an unknown field_type is
        // treated as text rather than failing. A field added to the server
        // after this build shipped still shows what it holds.
        assertEquals("whatever", customFieldValueDisplay(field("colour-wheel"), "whatever"))
        assertEquals("41", customFieldValueDisplay(field("colour-wheel"), 41))
    }

    @Test fun `the types that already worked still do`() {
        assertEquals("Yes", customFieldValueDisplay(field("boolean"), true))
        assertEquals("No", customFieldValueDisplay(field("boolean"), false))
        assertEquals("Jan 2, 2026", customFieldValueDisplay(field("date"), "2026-01-02"))
        assertEquals("a, b", customFieldValueDisplay(field("multiselect"), listOf("a", "b")))
        assertEquals("plain", customFieldValueDisplay(field("text"), "plain"))
    }
}
