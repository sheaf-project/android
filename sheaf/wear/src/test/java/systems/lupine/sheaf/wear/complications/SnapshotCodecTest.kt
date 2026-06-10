package systems.lupine.sheaf.wear.complications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the fronter/member snapshot codec and the pure text-fitting helpers
 * the watchface tiles and complications depend on. These run as plain JVM
 * tests (Moshi is pure JVM); no Android framework needed.
 */
class SnapshotCodecTest {

    // ── Snapshot codec ─────────────────────────────────────────────────────

    @Test
    fun `fronter names with commas survive a round trip`() {
        // Regression: the previous hand-rolled codec split objects on commas,
        // so a comma in a display name corrupted the whole snapshot.
        val rows = listOf(
            FronterRow(id = "1", name = "Bob, Jr.", since = "2024-01-01T00:00:00Z"),
            FronterRow(id = "2", name = "Alder", since = "2024-01-02T00:00:00Z"),
        )
        val back = parseFrontersJson(encodeFrontersJson(rows))
        assertEquals(rows, back)
        assertEquals("Bob, Jr.", back[0].name)
    }

    @Test
    fun `fronter names with quotes and backslashes survive a round trip`() {
        val rows = listOf(
            FronterRow(id = "1", name = "the \"quiet\" one", since = ""),
            FronterRow(id = "2", name = "back\\slash", since = ""),
        )
        assertEquals(rows, parseFrontersJson(encodeFrontersJson(rows)))
    }

    @Test
    fun `member names with commas and emoji survive a round trip`() {
        val rows = listOf(
            MemberRow(id = "1", name = "Vex, the loud", emoji = "🦊"),
            MemberRow(id = "2", name = "Quiet", emoji = ""),
        )
        assertEquals(rows, parseMembersJson(encodeMembersJson(rows)))
    }

    @Test
    fun `empty list round trips`() {
        assertEquals(emptyList(), parseFrontersJson(encodeFrontersJson(emptyList())))
    }

    @Test
    fun `malformed input decodes to an empty list rather than throwing`() {
        assertEquals(emptyList(), parseFrontersJson("not json at all"))
        assertEquals(emptyList(), parseFrontersJson(""))
        assertEquals(emptyList(), parseMembersJson("{broken"))
    }

    @Test
    fun `snapshot written in the legacy hand-rolled shape still decodes`() {
        // The old encoder emitted exactly this wire shape, so upgrades read
        // pre-existing snapshots without a forced resync.
        val legacy = """[{"id":"a","name":"Al","since":"2024-03-01T08:00:00Z"}]"""
        val rows = parseFrontersJson(legacy)
        assertEquals(1, rows.size)
        assertEquals("Al", rows[0].name)
        assertEquals("2024-03-01T08:00:00Z", rows[0].since)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val rows = parseFrontersJson("""[{"id":"a","name":"Al"}]""")
        assertEquals("", rows[0].since)
    }

    // ── Ordering helpers ───────────────────────────────────────────────────

    private val unordered = listOf(
        FronterRow("1", "Newest", "2024-01-03T00:00:00Z"),
        FronterRow("2", "Oldest", "2024-01-01T00:00:00Z"),
        FronterRow("3", "Middle", "2024-01-02T00:00:00Z"),
    )

    @Test
    fun `byOldestFirst puts the earliest since first`() {
        assertEquals(listOf("Oldest", "Middle", "Newest"), unordered.byOldestFirst().map { it.name })
    }

    @Test
    fun `byNewestFirst puts the latest since first`() {
        assertEquals(listOf("Newest", "Middle", "Oldest"), unordered.byNewestFirst().map { it.name })
    }

    @Test
    fun `byOldestFirst sorts blank since values to the end`() {
        val withBlank = unordered + FronterRow("4", "Unknown", "")
        assertEquals("Unknown", withBlank.byOldestFirst().last().name)
    }

    @Test
    fun `earliestSince ignores blanks and returns the minimum`() {
        val rows = listOf(
            FronterRow("1", "A", ""),
            FronterRow("2", "B", "2024-01-05T00:00:00Z"),
            FronterRow("3", "C", "2024-01-02T00:00:00Z"),
        )
        assertEquals("2024-01-02T00:00:00Z", rows.earliestSince())
    }

    @Test
    fun `earliestSince is null when nothing has a since`() {
        assertEquals(null, listOf(FronterRow("1", "A", "")).earliestSince())
    }

    // ── Short-text fitting ─────────────────────────────────────────────────

    @Test
    fun `fitFrontersShortText shows None when empty`() {
        assertEquals("None", fitFrontersShortText(emptyList()))
    }

    @Test
    fun `fitFrontersShortText shows a lone name within budget`() {
        assertEquals("Alice", fitFrontersShortText(listOf("Alice")))
    }

    @Test
    fun `fitFrontersShortText keeps the overflow suffix visible`() {
        // Two fronters: the "+N" tail must survive even if the primary name
        // has to be truncated, else the user can't tell there are more.
        val out = fitFrontersShortText(listOf("Alexander", "Bob", "Cara"))
        assertTrue(out.endsWith("+2"), "expected a +2 overflow tail, got '$out'")
    }

    // ── Long-name fitting ──────────────────────────────────────────────────

    @Test
    fun `fitNames returns empty for no names`() {
        assertEquals("", fitNames(emptyList(), budget = 24))
    }

    @Test
    fun `fitNames keeps everything when it fits`() {
        assertEquals("Al, Bo", fitNames(listOf("Al", "Bo"), budget = 24))
    }

    @Test
    fun `fitNames appends an overflow count when names exceed the budget`() {
        val out = fitNames(listOf("Alexander", "Bartholomew", "Cassiopeia", "Demetrius"), budget = 16)
        assertTrue(out.contains("+"), "expected an overflow marker, got '$out'")
    }
}
