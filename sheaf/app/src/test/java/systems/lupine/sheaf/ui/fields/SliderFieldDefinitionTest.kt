package systems.lupine.sheaf.ui.fields

import systems.lupine.sheaf.ui.components.SLIDER_MODE_NUMERIC
import systems.lupine.sheaf.ui.components.defaultSliderSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Setting a slider field up: which types carry options, which bounds the
 * server would refuse, and whether a set of colours is one of the named
 * gradients.
 */
class SliderFieldDefinitionTest {

    // ── Which types carry options ─────────────────────────────────────────────

    @Test fun `a slider sends its shape`() {
        val options = fieldOptions("slider", choices = null, slider = defaultSliderSpec())
        assertNotNull(options)
        assertEquals("labeled", options.mode)
    }

    @Test fun `a type with no options sends none`() {
        // The server refuses a non-null options dict on these outright, so a
        // slider spec left lying around in the sheet's state must not ride
        // along when the type was switched back to text.
        for (type in listOf("text", "number", "date", "boolean")) {
            assertNull(
                fieldOptions(type, choices = listOf("a", "b"), slider = defaultSliderSpec()),
                "$type must not carry options",
            )
        }
    }

    @Test fun `a choice type still sends only its choices`() {
        val options = fieldOptions("select", choices = listOf(" a ", "", "b"), slider = null)
        assertEquals(listOf("a", "b"), options?.choices)
        assertNull(options?.mode, "a select has no slider shape to send")
    }

    @Test fun `an empty choices list is freeform rather than an empty list`() {
        assertNull(fieldOptions("multiselect", choices = emptyList(), slider = null))
        assertNull(fieldOptions("multiselect", choices = listOf("  "), slider = null))
    }

    @Test fun `a slider with no spec collected sends nothing`() {
        // Which is what keeps a slider's options intact when the save came
        // from somewhere that never showed the shape editor.
        assertNull(fieldOptions("slider", choices = null, slider = null))
    }

    // ── Bounds the server would refuse ────────────────────────────────────────

    @Test fun `a labeled slider cannot have bad bounds`() {
        assertNull(sliderProblem(defaultSliderSpec()))
        assertTrue(sliderBoundsOk("slider", defaultSliderSpec()))
    }

    @Test fun `max must be above min`() {
        val spec = defaultSliderSpec().copy(mode = SLIDER_MODE_NUMERIC, min = 10.0, max = 10.0)
        assertNotNull(sliderProblem(spec))
        assertFalse(sliderBoundsOk("slider", spec))
        assertFalse(sliderBoundsOk("slider", spec.copy(max = 2.0)))
    }

    @Test fun `a step cannot be bigger than the range`() {
        val spec = defaultSliderSpec().copy(
            mode = SLIDER_MODE_NUMERIC,
            min = 0.0,
            max = 5.0,
            step = 10.0,
        )
        assertNotNull(sliderProblem(spec))
        assertFalse(sliderBoundsOk("slider", spec))
    }

    @Test fun `a step inside the range is fine, and no step at all is too`() {
        val spec = defaultSliderSpec().copy(mode = SLIDER_MODE_NUMERIC, min = 0.0, max = 5.0)
        assertNull(sliderProblem(spec.copy(step = 0.5)))
        assertNull(sliderProblem(spec.copy(step = null)))
    }

    @Test fun `another type's bad numbers never block its save`() {
        // sliderBoundsOk gates the Save button for every type, so a spec left
        // in a bad state must not strand a text field.
        val bad = defaultSliderSpec().copy(mode = SLIDER_MODE_NUMERIC, min = 5.0, max = 1.0)
        assertTrue(sliderBoundsOk("text", bad))
        assertTrue(sliderBoundsOk(null, bad))
    }

    // ── Named gradients ──────────────────────────────────────────────────────

    @Test fun `no colours is no gradient`() {
        assertEquals("none", gradientPresetId(null))
        assertEquals("none", gradientPresetId(emptyList()))
    }

    @Test fun `a preset's own colours are recognised whatever their case`() {
        assertEquals("cool-warm", gradientPresetId(listOf("#5B92C9", "#E08D5E")))
        assertEquals("cool-warm", gradientPresetId(listOf("#5b92c9", "#e08d5e")))
    }

    @Test fun `a three stop preset is recognised too`() {
        assertEquals("soft-hard", gradientPresetId(listOf("#F4EBD8", "#A89B8C", "#3A2E4D")))
    }

    @Test fun `colours matching nothing named are custom`() {
        assertEquals("custom", gradientPresetId(listOf("#123456", "#654321")))
        // Right colours, wrong order: a different gradient, so not the preset.
        assertEquals("custom", gradientPresetId(listOf("#E08D5E", "#5B92C9")))
    }
}
