package systems.lupine.sheaf.ui.importcommon

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import systems.lupine.sheaf.data.model.ImportJobEvent
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.ui.importflow.ImportCategory
import systems.lupine.sheaf.ui.importflow.ImportPreview
import systems.lupine.sheaf.ui.importflow.buildApiImportBodyJson
import systems.lupine.sheaf.ui.importflow.buildOptionsJson
import systems.lupine.sheaf.ui.importflow.importSourceById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * The importers hand-build their submit bodies (the options blob is
 * already-encoded JSON, so a typed class would encode it twice), which means
 * the escaping is ours to get right. The value being interpolated here is a
 * token the user pasted in from somewhere else.
 */
class ImportBodyTest {

    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )

    private fun parse(json: String): Map<String, Any> =
        assertNotNull(mapAdapter.fromJson(json), "body did not parse as JSON: $json")

    @Test fun `a quote in the token cannot break out of the string`() {
        val body = buildApiImportBodyJson(
            token = """pk"token""",
            idempotencyKey = "key-1",
            options = """{"groups":true}""",
        )
        assertEquals("""pk"token""", parse(body)["pk_token"])
    }

    @Test fun `a backslash in the token survives intact`() {
        val body = buildApiImportBodyJson(
            token = """pk\token""",
            idempotencyKey = "key-1",
            options = "{}",
        )
        assertEquals("""pk\token""", parse(body)["pk_token"])
    }

    @Test fun `a newline in the token still yields valid JSON`() {
        // Copying a token out of a chat client picks up a trailing line break
        // more often than not. The old escaper handled quotes and backslashes
        // only, so this produced a literal newline inside a JSON string: the
        // whole import 422'd with nothing useful to show for it.
        val body = buildApiImportBodyJson(
            token = "pk\ntoken\t2",
            idempotencyKey = "key-1",
            options = "{}",
        )
        assertEquals("pk\ntoken\t2", parse(body)["pk_token"])
    }

    @Test fun `a field-injection attempt stays inside the token value`() {
        val hostile = """x","options":{"front_history":true},"junk":"y"""
        val body = buildApiImportBodyJson(
            token = hostile,
            idempotencyKey = "key-1",
            options = """{"front_history":false}""",
        )
        val parsed = parse(body)
        assertEquals(hostile, parsed["pk_token"])
        // The options we passed win; nothing was smuggled in through the token.
        @Suppress("UNCHECKED_CAST")
        val options = parsed["options"] as Map<String, Any>
        assertEquals(false, options["front_history"])
        assertNull(parsed["junk"])
    }

    @Test fun `jsonEscape covers the control range`() {
        assertEquals("""ab""", jsonEscape("ab"))
        // named control escapes, and \uXXXX for the rest below 0x20
        assertEquals("""\b\n\r\t""", jsonEscape("\b\n\r\t"))
        assertEquals("\\u0001", jsonEscape("\u0001"))
    }

    @Test fun `jsonQuote wraps and escapes`() {
        assertEquals(""""a\"b"""", jsonQuote("""a"b"""))
    }

    // ── Terminal result decoding (shared by all eight importers) ────────────

    private fun job(status: String, counts: Map<String, Int> = emptyMap(), events: List<ImportJobEvent> = emptyList()) =
        ImportJobRead(id = "j1", source = "sheaf", status = status, counts = counts, events = events)

    @Test fun `a failed job has no result to show`() {
        // Returning a result here would render a failed import as a success.
        assertNull(job(ImportJobStatus.FAILED).terminalResult())
        assertNull(job(ImportJobStatus.RUNNING).terminalResult())
    }

    @Test fun `a complete job yields its counts and warnings`() {
        val result = job(
            status = ImportJobStatus.COMPLETE,
            counts = mapOf("members_imported" to 12, "groups_imported" to 3),
            events = listOf(
                ImportJobEvent(level = "warning", stage = "members", message = "no avatar", recordRef = "Alex"),
                ImportJobEvent(level = "info", stage = "members", message = "ignored"),
            ),
        ).terminalResult()

        assertNotNull(result)
        assertEquals(listOf("Alex: no avatar"), result.warnings)
        assertEquals(listOf("Members imported" to 12, "Groups imported" to 3), result.rows())
    }

    // ── Options bodies (one builder now, nine sources) ──────────────────────

    private fun options(sourceId: String, values: Map<String, Boolean>, memberIds: List<String>? = null) =
        parse(buildOptionsJson(importSourceById(sourceId)!!, values, memberIds))

    @Test fun `a hidden category is still written, at its default`() {
        // The backend forbids unknown fields but expects the known ones, so a
        // category the export didn't populate has to go out as false rather
        // than be dropped. Nine hand-written builders each got this right on
        // their own; this is the one that has to now.
        val preview = ImportPreview(
            categories = listOf(
                ImportCategory("groups", "Groups (2)", visible = true, default = true),
                ImportCategory("front_history", "Front history", visible = false, default = false),
            ),
        )
        val body = options("simplyplural", preview.defaults())
        assertEquals(true, body["groups"])
        assertEquals(false, body["front_history"])
    }

    @Test fun `sources that want an explicit null member_ids get one`() {
        // Prism, PluralSpace and Ampersand send "member_ids":null; Simply
        // Plural, PluralKit and Tupperbox omit the key entirely. Asserted on
        // the raw body because the map adapter here won't carry a null value.
        val prism = buildOptionsJson(importSourceById("prism")!!, emptyMap(), null)
        val sp = buildOptionsJson(importSourceById("simplyplural")!!, emptyMap(), null)
        assertTrue(""""member_ids":null""" in prism, prism)
        assertTrue("member_ids" !in sp, sp)
    }

    @Test fun `a narrowed selection sends the ids it was given`() {
        val body = options("simplyplural", mapOf("groups" to true), memberIds = listOf("m1", "m2"))
        assertEquals(listOf("m1", "m2"), body["member_ids"])
    }

    @Test fun `constant fields ride along with the toggles`() {
        val body = options("pluralspace", mapOf("polls" to false))
        assertEquals("skip", body["conflict_strategy"])
        assertEquals(false, body["polls"])
    }

    @Test fun `zero counts are dropped and the rest sort by size`() {
        val result = ImportResult(
            counts = mapOf("groups_imported" to 3, "members_imported" to 12, "fronts_imported" to 0),
            warnings = emptyList(),
        )
        assertEquals(listOf("Members imported" to 12, "Groups imported" to 3), result.rows())
    }
}
