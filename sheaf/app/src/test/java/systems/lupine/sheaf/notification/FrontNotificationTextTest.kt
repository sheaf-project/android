package systems.lupine.sheaf.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * What the fronting notification says. The discreet mode's whole value is that
 * the collapsed line gives nothing away, so it is worth a test that says so.
 */
class FrontNotificationTextTest {

    @Test fun `nobody fronting reads as nobody fronting`() {
        assertEquals("No one is fronting", FrontNotificationHelper.frontingText(emptyList()))
    }

    @Test fun `one member`() {
        assertEquals("Ash is fronting", FrontNotificationHelper.frontingText(listOf("Ash")))
    }

    @Test fun `two members are joined with and`() {
        assertEquals(
            "Ash and Bee are fronting",
            FrontNotificationHelper.frontingText(listOf("Ash", "Bee")),
        )
    }

    @Test fun `three or more get commas and a final and`() {
        assertEquals(
            "Ash, Bee and Cy are fronting",
            FrontNotificationHelper.frontingText(listOf("Ash", "Bee", "Cy")),
        )
    }

    @Test fun `the discreet line names nobody and mentions no fronting`() {
        // The point of hiding names is that somebody reading the shade over a
        // shoulder learns nothing. "Someone is fronting" would already be more
        // than nothing, so the collapsed text must not say even that.
        val discreet = FrontNotificationHelper.DISCREET_TEXT
        assertFalse("front" in discreet.lowercase(), discreet)
        listOf("Ash", "Bee").forEach { assertFalse(it in discreet, discreet) }
    }

    @Test fun `the default style is what shipped before it was configurable`() {
        // Upgrading must not change anybody's notification.
        val default = FrontNotificationStyle()
        assertEquals(false, default.useLogo)
        assertEquals(true, default.showNames)
        assertEquals(false, default.respawn)
    }
}
