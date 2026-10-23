package systems.lupine.sheaf.ui.components

import systems.lupine.sheaf.data.model.CustomFieldOptions
import systems.lupine.sheaf.data.model.SliderLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The slider field type's pure parts: resolving a definition's options into a
 * drawable shape, and the reading a value produces.
 *
 * The reading matters beyond this screen. The server puts the same string in
 * `value` on a public profile, so if these two drifted, the same slider would
 * read one way in the app and another on the web page it is published to.
 */
class SliderFieldTest {

    @Test fun `a labeled slider runs 0 to 100 and reads as a percentage`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "labeled"))
        assertEquals(0.0, spec.min)
        assertEquals(100.0, spec.max)
        assertEquals("62%", formatSliderValue(62, spec))
    }

    @Test fun `a numeric slider reads as its quantity and unit`() {
        val spec = sliderSpec(
            CustomFieldOptions(mode = "numeric", min = 120.0, max = 220.0, unit = "cm"),
        )
        assertEquals("172.5 cm", formatSliderValue(172.5, spec))
    }

    @Test fun `a numeric slider with no unit is just the number`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "numeric", min = 1.0, max = 5.0))
        assertEquals("4", formatSliderValue(4.0, spec))
    }

    @Test fun `a whole number loses its trailing zero`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "labeled"))
        assertEquals("50%", formatSliderValue(50.0, spec))
    }

    @Test fun `a reading rounds to two places`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "numeric", min = 0.0, max = 10.0))
        assertEquals("3.33", formatSliderValue(3.33333, spec))
    }

    @Test fun `a value that is not a number has no reading`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "labeled"))
        assertEquals("", formatSliderValue("sixty-two", spec))
        assertEquals("", formatSliderValue(null, spec))
        // A boolean is a Number to nobody, but it is worth pinning: Kotlin
        // will not smart-cast it, and a `true` rendering as "1%" would be a
        // reading the member never gave.
        assertEquals("", formatSliderValue(true, spec))
    }

    @Test fun `an unset value is null rather than a confident zero`() {
        assertNull(sliderValueOrNull(null))
        assertNull(sliderValueOrNull("not a number"))
        assertEquals(7.0, sliderValueOrNull(7))
    }

    @Test fun `no options at all still gives a drawable track`() {
        // The server fills every key in, so this is the hand-written or
        // older-row case. A spec whose span is zero would divide by nothing.
        val spec = sliderSpec(null)
        assertEquals(100.0, spec.span)
        assertEquals(SLIDER_MODE_LABELED, spec.mode)
    }

    @Test fun `bounds that are inverted or equal fall back to the mode's own`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "numeric", min = 80.0, max = 20.0))
        assertTrue(spec.span > 0, "an inverted range would collapse the track")
        assertEquals(0.0, spec.min)
        assertEquals(10.0, spec.max)
    }

    @Test fun `a gradient needs two colours to be one`() {
        assertNull(sliderSpec(CustomFieldOptions(gradient = listOf("#FF0000"))).gradient)
        assertNull(sliderSpec(CustomFieldOptions(gradient = emptyList())).gradient)
        assertEquals(
            listOf("#FF0000", "#00FF00"),
            sliderSpec(CustomFieldOptions(gradient = listOf("#FF0000", "#00FF00"))).gradient,
        )
    }

    @Test fun `a labeled slider ignores a unit it has no business carrying`() {
        // The server drops the unit in labeled mode, so holding on to it here
        // would show "62 cm" for a field the server reads as "62%".
        val spec = sliderSpec(CustomFieldOptions(mode = "labeled", unit = "cm"))
        assertNull(spec.unit)
        assertEquals("62%", formatSliderValue(62, spec))
    }

    @Test fun `a step of zero or less is no step at all`() {
        assertNull(sliderSpec(CustomFieldOptions(mode = "numeric", step = 0.0)).step)
        assertNull(sliderSpec(CustomFieldOptions(mode = "numeric", step = -1.0)).step)
    }

    @Test fun `a fraction says where a value sits and never leaves the track`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "numeric", min = 0.0, max = 50.0))
        assertEquals(0.5f, sliderFraction(25.0, spec))
        assertEquals(0f, sliderFraction(-10.0, spec), "below min must pin to the left end")
        assertEquals(1f, sliderFraction(999.0, spec), "above max must pin to the right end")
    }

    // ── What gets stored ─────────────────────────────────────────────────────

    @Test fun `the control's float noise never reaches the stored value`() {
        // Observed on device: dragging a 0..100 labeled slider to the 85 stop
        // produced exactly this, because Material's slider is Float-valued and
        // widening to Double exposes the error. The reading said "85%" while
        // the database held 85.00000762939453.
        val spec = sliderSpec(CustomFieldOptions(mode = "labeled", step = 1.0))
        assertEquals(85.0, snapSliderValue(85.00000762939453, spec))
    }

    @Test fun `a value snaps to the step grid`() {
        val spec = sliderSpec(
            CustomFieldOptions(mode = "numeric", min = 120.0, max = 220.0, step = 0.5),
        )
        assertEquals(153.5, snapSliderValue(153.5000038, spec))
        assertEquals(153.5, snapSliderValue(153.62, spec))
        assertEquals(154.0, snapSliderValue(153.9, spec))
    }

    @Test fun `a grid offset from zero snaps to the field's own bounds`() {
        // The stops are min, min+step, min+2*step ... not multiples of step,
        // so a min that is not on the step grid must not pull values off it.
        val spec = sliderSpec(
            CustomFieldOptions(mode = "numeric", min = 1.5, max = 11.5, step = 2.0),
        )
        assertEquals(5.5, snapSliderValue(5.4, spec))
        assertEquals(1.5, snapSliderValue(1.9, spec))
    }

    @Test fun `with no step the stored value matches the reading`() {
        val spec = sliderSpec(CustomFieldOptions(mode = "numeric", min = 0.0, max = 10.0))
        assertEquals(3.33, snapSliderValue(3.333333, spec))
        assertEquals("3.33", formatSliderValue(snapSliderValue(3.333333, spec), spec))
    }

    @Test fun `a stored value never leaves the field's range`() {
        // Rounding up at the top of the track could otherwise put the value
        // one step past max, which the server refuses outright.
        val spec = sliderSpec(
            CustomFieldOptions(mode = "numeric", min = 0.0, max = 5.0, step = 2.0),
        )
        assertEquals(5.0, snapSliderValue(5.0, spec))
        assertTrue(snapSliderValue(4.9, spec) <= 5.0)
        assertTrue(snapSliderValue(-1.0, spec) >= 0.0)
    }

    @Test fun `labels come back as given`() {
        val spec = sliderSpec(
            CustomFieldOptions(labels = SliderLabels(left = "Calm", right = "Intense")),
        )
        assertEquals("Calm", spec.labels.left)
        assertEquals("Intense", spec.labels.right)
        assertTrue(spec.hasAnyLabel)
        assertTrue(!sliderSpec(CustomFieldOptions()).hasAnyLabel)
    }
}
