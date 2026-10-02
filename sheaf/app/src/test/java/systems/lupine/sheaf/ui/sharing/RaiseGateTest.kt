package systems.lupine.sheaf.ui.sharing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether Public is offered at all.
 *
 * The rule that matters here is the one about lowering: an instance with
 * publishing switched off refuses a raise, and hiding the option is correct
 * for everything except a record that is already public, where hiding it would
 * take away the only way to make it less visible.
 */
class RaiseGateTest {

    private val publishingOn = RaiseGate(publishingAvailable = true)
    private val publishingOff = RaiseGate(publishingAvailable = false)

    @Test fun `with publishing on, everything offers public`() {
        assertTrue(publishingOn.offersPublic(null))
        assertTrue(publishingOn.offersPublic("private"))
        assertTrue(publishingOn.offersPublic("public"))
    }

    @Test fun `with publishing off, a raise is not offered`() {
        assertFalse(publishingOff.offersPublic("private"))
        assertFalse(publishingOff.offersPublic("friends"))
    }

    @Test fun `a record already public keeps the option, so it can be lowered`() {
        // Nothing may stand between somebody and reducing their own exposure.
        // Hiding Public here would also hide the record's own current value.
        assertTrue(publishingOff.offersPublic("public"))
    }

    @Test fun `a create form counts as a raise from nothing`() {
        // The backend refuses public on the create paths too, so an absent
        // stored value is not a licence to offer it.
        assertFalse(publishingOff.offersPublic(null))
    }

    @Test fun `an ungated surface is never restricted`() {
        // Group relationship edges: the public projection never queries them,
        // so the server stores public as asked. Disabling it would invent a
        // restriction that does not exist.
        assertTrue(publishingOff.offersPublic(null, gated = false))
        assertTrue(publishingOff.offersPublic("private", gated = false))
    }

    @Test fun `the note names the state rather than describing a rule`() {
        // It opens with the same clause as the sharing screen's card, so the
        // two surfaces are recognisably about one thing.
        assertTrue(PUBLISHING_OFF_NOTE.startsWith("Public profiles are turned off on this instance"))
        assertTrue("lower it" in PUBLISHING_OFF_NOTE, PUBLISHING_OFF_NOTE)
    }
}
