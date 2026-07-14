package systems.lupine.sheaf.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The front-history ring buffer is stored as hand-rolled JSON (Moshi is kept
 * out of the cross-process tile read path). A parse bug here shows the history
 * screen and the timeline tile an empty or garbled list, silently.
 */
class FrontHistoryCodecTest {

    private fun roundTrip(entries: List<FrontHistoryEntry>): List<FrontHistoryEntry> =
        parseFrontHistoryJson(frontHistoryToJson(entries))

    @Test fun `a single entry round trips`() {
        val entries = listOf(FrontHistoryEntry(1_700_000_000_000, listOf("m1", "m2"), ongoing = true))
        assertEquals(entries, roundTrip(entries))
    }

    @Test fun `several entries round trip in order`() {
        val entries = listOf(
            FrontHistoryEntry(1_000, listOf("a")),
            FrontHistoryEntry(2_000, listOf("b", "c"), ongoing = true),
            FrontHistoryEntry(3_000, listOf("d")),
        )
        assertEquals(entries, roundTrip(entries))
    }

    @Test fun `the ongoing flag survives both ways`() {
        val ongoing = FrontHistoryEntry(1_000, listOf("a"), ongoing = true)
        val ended = FrontHistoryEntry(2_000, listOf("a"), ongoing = false)
        assertEquals(listOf(ongoing, ended), roundTrip(listOf(ongoing, ended)))
        // The flag is only written when set, so its absence must decode as false.
        assertTrue(frontHistoryToJson(listOf(ended)).contains("\"o\"").not())
    }

    @Test fun `an entry with no members survives`() {
        // "Nobody is fronting" is a real state and must not be dropped.
        val entries = listOf(FrontHistoryEntry(1_000, emptyList()))
        assertEquals(entries, roundTrip(entries))
    }

    @Test fun `an empty history round trips`() {
        assertEquals(emptyList(), roundTrip(emptyList()))
        assertEquals("[]", frontHistoryToJson(emptyList()))
    }

    @Test fun `garbage decodes to empty rather than throwing`() {
        // Whatever is in prefs came from an older build or a corrupted write;
        // the tile renderer must not crash on it.
        assertEquals(emptyList(), parseFrontHistoryJson(""))
        assertEquals(emptyList(), parseFrontHistoryJson("null"))
        assertEquals(emptyList(), parseFrontHistoryJson("{\"t\":1}"))
        assertEquals(emptyList(), parseFrontHistoryJson("not json at all"))
    }

    @Test fun `a missing timestamp decodes as zero, not a crash`() {
        val parsed = parseFrontHistoryJson("""[{"m":["a"]}]""")
        assertEquals(1, parsed.size)
        assertEquals(0L, parsed[0].timestamp)
        assertEquals(listOf("a"), parsed[0].memberIds)
    }

    @Test fun `many entries round trip`() {
        val entries = (1..MAX_HISTORY).map { FrontHistoryEntry(it * 1_000L, listOf("m$it")) }
        assertEquals(entries, roundTrip(entries))
    }
}
