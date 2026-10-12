package systems.lupine.sheaf.ui.history

import systems.lupine.sheaf.data.model.FrontRead
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which open fronts a back-dated start would break.
 *
 * Starting a front ends every open one at the new start. An open front that
 * began *after* that moment would be given an end before its own beginning,
 * which `ck_fronts_ended_after_started` refuses, and the server answers the
 * whole request with a 500 the user cannot act on. This is the check that
 * turns that into a sentence before the request goes out.
 */
class OpenFrontClashTest {

    private fun front(id: String, startedAt: String) = FrontRead(
        id = id,
        systemId = "s1",
        memberIds = listOf("m1"),
        startedAt = startedAt,
        endedAt = null,
        customStatus = null,
    )

    private val noon = Instant.parse("2026-10-12T12:00:00Z")

    @Test fun `a start before an open front is the case that breaks`() {
        val live = front("f1", "2026-10-12T13:00:00Z")
        assertEquals(listOf("f1"), openFrontsStartingAfter(noon, listOf(live)).map { it.id })
    }

    @Test fun `a start after every open front is fine`() {
        val live = front("f1", "2026-10-12T09:00:00Z")
        assertTrue(openFrontsStartingAfter(noon, listOf(live)).isEmpty())
    }

    @Test fun `an identical start is allowed`() {
        // The front would end at the instant it began: a zero-length entry,
        // not a backwards one, and the constraint permits it.
        val live = front("f1", "2026-10-12T12:00:00Z")
        assertTrue(openFrontsStartingAfter(noon, listOf(live)).isEmpty())
    }

    @Test fun `every clashing front is reported, not just the first`() {
        // All of them get ended, so all of them have to be named: fixing one
        // and resubmitting into the next is a bad way to find out.
        val fronts = listOf(
            front("early", "2026-10-12T09:00:00Z"),
            front("late", "2026-10-12T13:00:00Z"),
            front("later", "2026-10-12T14:00:00Z"),
        )
        assertEquals(
            listOf("late", "later"),
            openFrontsStartingAfter(noon, fronts).map { it.id },
        )
    }

    @Test fun `nothing open means nothing to clash with`() {
        assertTrue(openFrontsStartingAfter(noon, emptyList()).isEmpty())
    }

    @Test fun `an unreadable timestamp does not block the entry`() {
        // The server is the authority on its own rows. Treating a timestamp
        // this client cannot parse as a clash would refuse a legitimate entry
        // over a formatting disagreement.
        assertTrue(openFrontsStartingAfter(noon, listOf(front("f1", "whenever"))).isEmpty())
    }
}
