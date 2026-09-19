package systems.lupine.sheaf.ui.sharing

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Expiry is fixed at creation (the API has no PATCH for it), so the two things
 * worth pinning are that a picked date survives the round trip to a UTC instant
 * with the meaning the user gave it, and that the staging interaction is caught
 * before the grant is minted rather than after.
 */
class ShareExpiryTest {

    @Test
    fun `a picked date expires at the end of that day, not the start`() {
        val iso = endOfDayUtc(LocalDate.of(2026, 9, 17))
        val local = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        // Whatever the device's offset, it is still the 17th where the user is.
        assertEquals(LocalDate.of(2026, 9, 17), local)

        val start = LocalDate.of(2026, 9, 17)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
        assertTrue(
            Instant.parse(iso).isAfter(start),
            "an expiry at the start of the day would kill the link before the day it names",
        )
    }

    @Test
    fun `with no grace window any future date is fine`() {
        val today = LocalDate.of(2026, 9, 10)
        assertFalse(expiryIsBeforeActivation(today.plusDays(1), graceDays = 0, today = today))
        assertFalse(expiryIsBeforeActivation(null, graceDays = 0, today = today))
    }

    @Test
    fun `an expiry inside the grace window would never have been readable`() {
        val today = LocalDate.of(2026, 9, 10)
        // Goes live on the 17th; expiring on the 15th means it never serves.
        assertTrue(expiryIsBeforeActivation(today.plusDays(5), graceDays = 7, today = today))
        // Expiring on the very day it activates is the same problem.
        assertTrue(expiryIsBeforeActivation(today.plusDays(7), graceDays = 7, today = today))
        // A day later is genuinely readable.
        assertFalse(expiryIsBeforeActivation(today.plusDays(8), graceDays = 7, today = today))
    }

    @Test
    fun `never expiring is never a conflict`() {
        val today = LocalDate.of(2026, 9, 10)
        assertFalse(expiryIsBeforeActivation(null, graceDays = 30, today = today))
    }

    @Test
    fun `a future expiry is not a reason a grant is dormant`() {
        val today = LocalDate.of(2026, 9, 10)
        val future = endOfDayUtc(today.plusDays(30))
        assertEquals("Active", dormantReason("active", future, today))
        assertEquals("Waiting out the grace window", dormantReason("pending", future, today))
    }

    @Test
    fun `a lapsed grant says so, with the date`() {
        val today = LocalDate.of(2026, 9, 10)
        val past = endOfDayUtc(LocalDate.of(2026, 9, 1))
        assertEquals("Expired 1 Sep 2026", dormantReason("active", past, today))
    }

    @Test
    fun `pending outranks an expiry that has already passed`() {
        val today = LocalDate.of(2026, 9, 10)
        val past = endOfDayUtc(LocalDate.of(2026, 9, 1))
        assertEquals("Waiting out the grace window", dormantReason("pending", past, today))
    }

    @Test
    fun `an unparseable or absent timestamp is not treated as an expiry`() {
        val today = LocalDate.of(2026, 9, 10)
        assertEquals("Active", dormantReason("active", null, today))
        assertEquals("Active", dormantReason("active", "not a timestamp", today))
    }
}
