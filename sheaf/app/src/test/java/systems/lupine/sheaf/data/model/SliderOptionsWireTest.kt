package systems.lupine.sheaf.data.model

import systems.lupine.sheaf.di.NetworkModule
import systems.lupine.sheaf.ui.components.SLIDER_MODE_NUMERIC
import systems.lupine.sheaf.ui.components.defaultSliderSpec
import systems.lupine.sheaf.ui.components.sliderSpec
import systems.lupine.sheaf.ui.components.toOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a slider's options look like on the wire, in both directions.
 *
 * Worth pinning at the JSON rather than at the data class, for two reasons.
 * A slider's options are the one body on this client the server REPLACES
 * rather than merges, so a key this builds and leaves out is a key the server
 * resets to its default: omission is how a label, a unit or a gradient gets
 * cleared, and it is equally how one would get silently wiped. And the server
 * refuses an options object with a key it does not recognise for the type, so
 * a stray `choices` riding along on a slider would 422 the whole save.
 */
class SliderOptionsWireTest {

    private val adapter = NetworkModule.provideMoshi().adapter(CustomFieldOptions::class.java)

    private fun json(options: CustomFieldOptions) = adapter.toJson(options)

    @Test fun `a labeled slider sends only what its mode means`() {
        val wire = json(defaultSliderSpec().toOptions())
        assertTrue(wire.contains("\"mode\":\"labeled\""), wire)
        // The range IS the mode in labeled form, so stating it would be
        // telling the server something it is about to overrule anyway.
        assertFalse(wire.contains("\"min\""), wire)
        assertFalse(wire.contains("\"max\""), wire)
        assertFalse(wire.contains("\"unit\""), wire)
        assertFalse(wire.contains("\"step\""), wire)
    }

    @Test fun `no slider ever sends choices`() {
        // `choices` shares the options object with the slider keys, and the
        // server rejects it outright for a slider: options may only contain
        // the keys its own type uses.
        val numeric = defaultSliderSpec().copy(mode = SLIDER_MODE_NUMERIC, unit = "kg")
        assertFalse(json(numeric.toOptions()).contains("choices"), json(numeric.toOptions()))
        assertFalse(json(defaultSliderSpec().toOptions()).contains("choices"))
    }

    @Test fun `a numeric slider sends its own range and unit`() {
        val spec = defaultSliderSpec().copy(
            mode = SLIDER_MODE_NUMERIC,
            min = 120.0,
            max = 220.0,
            step = 0.5,
            unit = "cm",
        )
        val wire = json(spec.toOptions())
        assertTrue(wire.contains("\"mode\":\"numeric\""), wire)
        assertTrue(wire.contains("\"min\":120"), wire)
        assertTrue(wire.contains("\"max\":220"), wire)
        assertTrue(wire.contains("\"step\":0.5"), wire)
        assertTrue(wire.contains("\"unit\":\"cm\""), wire)
    }

    @Test fun `a step left blank means any value, and is left out`() {
        val spec = defaultSliderSpec().copy(mode = SLIDER_MODE_NUMERIC, step = null)
        assertFalse(json(spec.toOptions()).contains("\"step\""), json(spec.toOptions()))
    }

    @Test fun `emptying a label clears it rather than keeping the old one`() {
        // The server rebuilds the options from what arrives, so a label left
        // out comes back null. That is the clear, and it is the only one
        // available: there is no explicit null to send.
        val spec = defaultSliderSpec().copy(
            labels = SliderLabels(left = "Calm", center = "   ", right = ""),
        )
        val wire = json(spec.toOptions())
        assertTrue(wire.contains("\"left\":\"Calm\""), wire)
        assertFalse(wire.contains("\"center\""), wire)
        assertFalse(wire.contains("\"right\""), wire)
    }

    @Test fun `a label keeps its surrounding text but loses the padding`() {
        val spec = defaultSliderSpec().copy(labels = SliderLabels(left = "  Calm  "))
        assertTrue(json(spec.toOptions()).contains("\"left\":\"Calm\""))
    }

    @Test fun `a gradient of one colour is not sent as a gradient`() {
        // The server needs two to eight stops; one would be a 422, and the
        // field would refuse to save for a reason nobody asked for.
        val spec = defaultSliderSpec().copy(gradient = listOf("#FF0000"))
        assertFalse(json(spec.toOptions()).contains("gradient"), json(spec.toOptions()))
    }

    @Test fun `a gradient of two colours is sent in order`() {
        val spec = defaultSliderSpec().copy(gradient = listOf("#5B92C9", "#E08D5E"))
        assertTrue(
            json(spec.toOptions()).contains("\"gradient\":[\"#5B92C9\",\"#E08D5E\"]"),
            json(spec.toOptions()),
        )
    }

    @Test fun `the server's own options decode whole`() {
        val fromServer = """
            {"mode":"numeric","min":0,"max":10,"step":1,"unit":"kg",
             "labels":{"left":"Light","center":null,"right":"Heavy"},
             "gradient":["#5B92C9","#E08D5E"]}
        """.trimIndent()
        val spec = sliderSpec(adapter.fromJson(fromServer))
        assertEquals(SLIDER_MODE_NUMERIC, spec.mode)
        assertEquals(0.0, spec.min)
        assertEquals(10.0, spec.max)
        assertEquals(1.0, spec.step)
        assertEquals("kg", spec.unit)
        assertEquals("Light", spec.labels.left)
        assertNull(spec.labels.center)
        assertEquals("Heavy", spec.labels.right)
        assertEquals(listOf("#5B92C9", "#E08D5E"), spec.gradient)
    }

    @Test fun `a key this build has never heard of does not break the decode`() {
        // The server may add to a slider's options the way it added the type.
        // A field list that failed to decode over one unknown key would take
        // every other field down with it.
        val spec = sliderSpec(
            adapter.fromJson("""{"mode":"labeled","labels":{"left":"Calm"},"snap":true}"""),
        )
        assertEquals("Calm", spec.labels.left)
    }

    @Test fun `a slider's options round-trip without losing its shape`() {
        // The edit dialog seeds itself from the server's answer and sends it
        // back when only the name changed, so this is the path that would
        // quietly flatten a gradient or a unit.
        val original = defaultSliderSpec().copy(
            mode = SLIDER_MODE_NUMERIC,
            min = 1.0,
            max = 7.0,
            step = 0.5,
            unit = "h",
            labels = SliderLabels(left = "Short", right = "Long"),
            gradient = listOf("#111111", "#EEEEEE"),
        )
        val returned = sliderSpec(adapter.fromJson(json(original.toOptions())))
        assertEquals(original, returned)
    }
}
