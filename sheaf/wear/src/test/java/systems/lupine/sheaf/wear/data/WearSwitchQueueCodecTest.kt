package systems.lupine.sheaf.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The offline switch queue is line-delimited `uuid|createdAt|replace|ids` in
 * SharedPreferences. A parse bug drops a queued switch, or replays it with the
 * wrong member set / replace flag / createdAt (which becomes the front's
 * started_at when the queue drains).
 */
class WearSwitchQueueCodecTest {

    private fun roundTrip(s: WearQueuedSwitch): WearQueuedSwitch? =
        WearSwitchQueue.parseLine(WearSwitchQueue.encode(s))

    @Test fun `a switch round trips`() {
        val s = WearQueuedSwitch("u-1", listOf("m1", "m2"), replaceFronts = true, createdAt = 1_700_000_000_000)
        assertEquals(s, roundTrip(s))
    }

    @Test fun `the replace flag survives both states`() {
        val add = WearQueuedSwitch("u-1", listOf("m1"), replaceFronts = false, createdAt = 1)
        val replace = WearQueuedSwitch("u-2", listOf("m1"), replaceFronts = true, createdAt = 2)
        assertEquals(false, roundTrip(add)!!.replaceFronts)
        assertEquals(true, roundTrip(replace)!!.replaceFronts)
    }

    @Test fun `a multi-member switch keeps every member`() {
        val s = WearQueuedSwitch("u-1", listOf("m1", "m2", "m3"), replaceFronts = true, createdAt = 5)
        assertEquals(listOf("m1", "m2", "m3"), roundTrip(s)!!.memberIds)
    }

    @Test fun `a malformed line is dropped, not half-parsed`() {
        assertNull(WearSwitchQueue.parseLine(""))
        assertNull(WearSwitchQueue.parseLine("u-1|123"))            // too few fields
        assertNull(WearSwitchQueue.parseLine("u-1|notanumber|1|m1")) // bad createdAt
        assertNull(WearSwitchQueue.parseLine("|123|1|m1"))          // blank uuid
        assertNull(WearSwitchQueue.parseLine("u-1|123|1|"))         // no members
    }

    @Test fun `an unknown replace token is treated as false, not invalid`() {
        // Documenting the current behaviour: only "1" means replace.
        val parsed = WearSwitchQueue.parseLine("u-1|123|x|m1")
        assertEquals(false, parsed!!.replaceFronts)
    }
}
