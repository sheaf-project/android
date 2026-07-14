package systems.lupine.sheaf.data.api

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import okio.Buffer
import systems.lupine.sheaf.data.model.FrontUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fronts PATCH is a tri-state wire contract: omitted = leave alone,
 * explicit null = clear, value = set. Moshi's codegen only does two of those,
 * which is why this adapter is hand-written. If the "explicit null" half
 * regresses, "mark still ongoing" silently degrades to "leave as-is": the front
 * stays ended, the timeline is wrong, and nothing errors.
 */
class FrontUpdateJsonAdapterTest {

    private val adapter = FrontUpdateJsonAdapter()

    private fun encode(update: FrontUpdate): String {
        val buffer = Buffer()
        adapter.toJson(JsonWriter.of(buffer), update)
        return buffer.readUtf8()
    }

    private fun decode(json: String): FrontUpdate =
        adapter.fromJson(JsonReader.of(Buffer().writeUtf8(json)))

    @Test fun `clearEndedAt emits an explicit null`() {
        assertEquals("""{"ended_at":null}""", encode(FrontUpdate(clearEndedAt = true)))
    }

    @Test fun `a null endedAt without clearEndedAt is omitted entirely`() {
        // Omitted means "leave as-is". Emitting null here would silently end
        // (or un-end) fronts on every unrelated patch.
        assertEquals("{}", encode(FrontUpdate()))
    }

    @Test fun `clearEndedAt wins over a supplied endedAt`() {
        assertEquals(
            """{"ended_at":null}""",
            encode(FrontUpdate(endedAt = "2026-07-14T10:00:00Z", clearEndedAt = true)),
        )
    }

    @Test fun `a value is emitted as a value`() {
        assertEquals(
            """{"ended_at":"2026-07-14T10:00:00Z"}""",
            encode(FrontUpdate(endedAt = "2026-07-14T10:00:00Z")),
        )
    }

    @Test fun `other null fields stay omitted`() {
        val json = encode(FrontUpdate(memberIds = listOf("a", "b")))
        assertEquals("""{"member_ids":["a","b"]}""", json)
    }

    @Test fun `every field together`() {
        val json = encode(
            FrontUpdate(
                clearEndedAt = true,
                memberIds = listOf("a"),
                startedAt = "2026-07-14T09:00:00Z",
                customStatus = "hi",
            )
        )
        assertEquals(
            """{"ended_at":null,"member_ids":["a"],"started_at":"2026-07-14T09:00:00Z","custom_status":"hi"}""",
            json,
        )
    }

    @Test fun `an empty member list is emitted, not omitted`() {
        // Distinct from null: "no members" is a real (if odd) instruction.
        assertEquals("""{"member_ids":[]}""", encode(FrontUpdate(memberIds = emptyList())))
    }

    @Test fun `reading an explicit null sets clearEndedAt`() {
        val parsed = decode("""{"ended_at":null}""")
        assertTrue(parsed.clearEndedAt)
        assertNull(parsed.endedAt)
    }

    @Test fun `reading a value leaves clearEndedAt false`() {
        val parsed = decode("""{"ended_at":"2026-07-14T10:00:00Z"}""")
        assertFalse(parsed.clearEndedAt)
        assertEquals("2026-07-14T10:00:00Z", parsed.endedAt)
    }

    @Test fun `unknown fields are skipped rather than fatal`() {
        val parsed = decode("""{"unknown":{"nested":[1,2]},"started_at":"2026-07-14T09:00:00Z"}""")
        assertEquals("2026-07-14T09:00:00Z", parsed.startedAt)
    }

    @Test fun `round trip preserves the clear instruction`() {
        val original = FrontUpdate(clearEndedAt = true, memberIds = listOf("a"))
        assertEquals(original, decode(encode(original)))
    }
}
