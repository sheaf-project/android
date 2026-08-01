package systems.lupine.sheaf.ui.components

import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingDeleteTest {

    private fun inHours(h: Long) = OffsetDateTime.now().plusHours(h)
    private fun inMinutes(m: Long) = OffsetDateTime.now().plusMinutes(m)

    @Test fun `a null or blank timestamp parses to null`() {
        // Every row drops the badge in unconditionally, so "not pending" has to
        // be a quiet null rather than an exception.
        assertNull(parseFinalizeAt(null))
        assertNull(parseFinalizeAt(""))
        assertNull(parseFinalizeAt("   "))
    }

    @Test fun `an unparseable timestamp is null rather than a crash`() {
        // Server-shaped data we didn't expect shouldn't take down a list.
        assertNull(parseFinalizeAt("not a date"))
        assertNull(parseFinalizeAt("2026-13-45T99:99:99Z"))
    }

    @Test fun `a real timestamp parses`() {
        assertEquals(
            OffsetDateTime.parse("2026-08-01T12:00:00Z"),
            parseFinalizeAt("2026-08-01T12:00:00Z"),
        )
    }

    @Test fun `under a day reads in hours`() {
        assertEquals("in 18h", formatFinalizeCountdown(inHours(18).plusMinutes(1)))
        assertEquals("in 1h", formatFinalizeCountdown(inMinutes(61)))
    }

    @Test fun `never overstates the time left`() {
        // Erring long on a destructive-action deadline can cost someone the
        // window to cancel; erring short only makes them act sooner. So every
        // boundary rounds down.
        assertEquals("in 1h", formatFinalizeCountdown(inMinutes(119)))
        assertEquals("in 1 day", formatFinalizeCountdown(inHours(25)))
        assertEquals("in 2 days", formatFinalizeCountdown(inHours(71)))
    }

    @Test fun `the last hour says so in words rather than in 0h`() {
        // Flooring to "in 0h" would read as already gone while the entity is
        // still very much cancellable.
        assertEquals("in under an hour", formatFinalizeCountdown(inMinutes(5)))
        assertEquals("in under an hour", formatFinalizeCountdown(inMinutes(59)))
    }

    @Test fun `a day or more reads in days`() {
        assertEquals("in 1 day", formatFinalizeCountdown(inHours(24).plusMinutes(1)))
        assertEquals("in 2 days", formatFinalizeCountdown(inHours(48).plusMinutes(1)))
        assertEquals("in 7 days", formatFinalizeCountdown(inHours(24 * 7).plusMinutes(1)))
    }

    @Test fun `an elapsed deadline reads as imminent, never negative`() {
        // The finalise sweep runs on its own schedule, so a row can outlive its
        // own deadline briefly. "in -3h" would be nonsense.
        assertEquals("any moment", formatFinalizeCountdown(inHours(-3)))
        assertEquals("any moment", formatFinalizeCountdown(OffsetDateTime.now()))
    }
}
