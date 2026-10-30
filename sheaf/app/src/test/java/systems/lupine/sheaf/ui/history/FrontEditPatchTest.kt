package systems.lupine.sheaf.ui.history

import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.di.NetworkModule
import systems.lupine.sheaf.data.model.FrontUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What an edit sends.
 *
 * Every save used to submit both timestamps, rebuilt from pickers that had
 * already dropped their seconds, so changing only a status rewrote both times
 * to the minute. That showed up as a spurious edit in the web history and,
 * worse, broke contiguous-front coalescing: the server walks a chain by strict
 * equality between one entry's end and the next one's start, so rounding one
 * side detaches it and resets that member's "fronting since". Reproduced on a
 * live instance, where a status-only edit moved a member's fronting-since from
 * 07:00 to 09:15.
 */
class FrontEditPatchTest {

    private val original = FrontRead(
        id = "f1",
        systemId = "s1",
        startedAt = "2026-10-06T07:00:00.123456Z",
        endedAt = "2026-10-06T09:15:30.250000Z",
        memberIds = listOf("a"),
        customStatus = "tired",
    )

    private fun patch(
        memberIds: List<String> = original.memberIds,
        startedAt: String = original.startedAt,
        endedAt: String? = original.endedAt,
        customStatus: String? = original.customStatus,
    ) = frontEditPatch(original, memberIds, startedAt, endedAt, customStatus)

    @Test fun `a status-only edit leaves both timestamps alone`() {
        // The case that broke coalescing. The sheet hands back times rounded
        // to the minute, which are the same minute as the originals, so
        // neither is sent and the server keeps them exactly.
        val update = patch(
            startedAt = "2026-10-06T07:00:00Z",
            endedAt = "2026-10-06T09:15:30Z",
            customStatus = "wired",
        )
        assertNull(update.startedAt)
        assertNull(update.endedAt)
        assertFalse(update.clearEndedAt)
        assertEquals("wired", update.customStatus)
    }

    @Test fun `an edit that changes nothing sends nothing`() {
        val update = patch()
        assertEquals(FrontUpdate(), update)
    }

    @Test fun `a real time change is sent`() {
        val update = patch(startedAt = "2026-10-06T08:30:00Z")
        assertEquals("2026-10-06T08:30:00Z", update.startedAt)
        assertNull(update.endedAt)
    }

    @Test fun `emptying the status clears it`() {
        // The screen trims an empty status to null. Null alone means "leave
        // as-is", so the clear needs saying explicitly or the old status
        // survives a save that looked like it removed one.
        val update = patch(customStatus = null)
        assertTrue(update.clearCustomStatus)
        assertNull(update.customStatus)
    }

    @Test fun `an entry with no status does not ask to clear one`() {
        val fresh = original.copy(customStatus = null)
        val update = frontEditPatch(fresh, fresh.memberIds, fresh.startedAt, fresh.endedAt, null)
        assertFalse(update.clearCustomStatus)
    }

    @Test fun `reopening an entry clears its end`() {
        val update = patch(endedAt = null)
        assertTrue(update.clearEndedAt)
        assertNull(update.endedAt)
    }

    @Test fun `an already-open entry is not reopened by an untouched edit`() {
        // clearEndedAt used to be set from `endedAt == null` alone, so every
        // save on an open entry restated the clear.
        val open = original.copy(endedAt = null)
        val update = frontEditPatch(open, open.memberIds, open.startedAt, null, open.customStatus)
        assertFalse(update.clearEndedAt)
    }

    @Test fun `closing an open entry sends the end`() {
        val open = original.copy(endedAt = null)
        val update = frontEditPatch(
            open, open.memberIds, open.startedAt, "2026-10-06T10:00:00Z", open.customStatus,
        )
        assertEquals("2026-10-06T10:00:00Z", update.endedAt)
        assertFalse(update.clearEndedAt)
    }

    @Test fun `a membership change is sent, and reordering alone is not`() {
        assertEquals(listOf("a", "b"), patch(memberIds = listOf("a", "b")).memberIds)
        val reordered = original.copy(memberIds = listOf("a", "b"))
        val update = frontEditPatch(
            reordered, listOf("b", "a"), reordered.startedAt, reordered.endedAt,
            reordered.customStatus,
        )
        assertNull(update.memberIds)
    }

    @Test fun `an unparseable timestamp is sent rather than assumed unchanged`() {
        val odd = original.copy(startedAt = "not a timestamp")
        val update = frontEditPatch(
            odd, odd.memberIds, "2026-10-06T07:00:00Z", odd.endedAt, odd.customStatus,
        )
        assertEquals("2026-10-06T07:00:00Z", update.startedAt)
    }

    // ── What reaches the wire ────────────────────────────────────────────────

    private val adapter = NetworkModule.provideMoshi().adapter(FrontUpdate::class.java)

    @Test fun `clearing the status puts an explicit null on the wire`() {
        // Moshi drops nulls, so without the flag the field never appeared and
        // the server read the save as "status untouched".
        val json = adapter.toJson(patch(customStatus = null))
        assertTrue(""""custom_status":null""" in json, json)
    }

    @Test fun `a status-only edit mentions no timestamps at all`() {
        val json = adapter.toJson(
            patch(
                startedAt = "2026-10-06T07:00:00Z",
                endedAt = "2026-10-06T09:15:30Z",
                customStatus = "wired",
            ),
        )
        assertFalse("started_at" in json, json)
        assertFalse("ended_at" in json, json)
        assertTrue("custom_status" in json, json)
    }
}
