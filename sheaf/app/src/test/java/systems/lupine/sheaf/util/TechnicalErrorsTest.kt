package systems.lupine.sheaf.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "Show technical error details" register.
 *
 * Off is the default and is covered by [ErrorMessagesTest]; this is what
 * changes when somebody turns it on, plus the two things that make the switch
 * possible at all: reading the server's `detail` without consuming the body,
 * and carrying the preference in and out of the account's client settings.
 */
class TechnicalErrorsTest {

    private fun httpError(code: Int, body: String = ""): HttpException =
        HttpException(
            Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
        )

    private fun detailBody(detail: String) = """{"detail":"$detail"}"""

    // ── The technical register ───────────────────────────────────────────────

    @Test fun `on, an http failure leads with its status and the server's words`() {
        TechnicalErrors.withEnabled(true) {
            assertEquals(
                "[403] Publishing is turned off on this instance",
                httpError(403, detailBody("Publishing is turned off on this instance"))
                    .toUserMessage(),
            )
        }
    }

    @Test fun `on, a failure with nothing to say still shows its status`() {
        // The status is the point: "[500]" tells a reporter which layer
        // answered, where the summary alone does not. Asserted as a prefix
        // because the summary itself is untouched by this change.
        TechnicalErrors.withEnabled(true) {
            val message = httpError(500).toUserMessage()
            assertTrue(message.startsWith("[500] Server error"), message)
        }
    }

    @Test fun `on, a network failure names the exception behind it`() {
        // Four different causes collapse into one sentence in the friendly
        // register, and which one it was is the thing a bug report is missing.
        TechnicalErrors.withEnabled(true) {
            assertEquals(
                "Can't reach the server - check your internet connection " +
                    "(UnknownHostException: sheaf.example.com)",
                UnknownHostException("sheaf.example.com").toUserMessage(),
            )
        }
    }

    @Test fun `on, an exception with no message still names its type`() {
        TechnicalErrors.withEnabled(true) {
            assertTrue(
                UnknownHostException().toUserMessage().endsWith("(UnknownHostException)"),
                UnknownHostException().toUserMessage(),
            )
        }
    }

    @Test fun `off, nothing technical leaks out`() {
        TechnicalErrors.withEnabled(false) {
            val message = httpError(403, detailBody("Publishing is turned off")).toUserMessage()
            assertEquals("Access denied", message)
            assertTrue("403" !in message)
        }
    }

    @Test fun `the edge answering for the server is still said so in both registers`() {
        // A 403 from Cloudflare is not Sheaf denying access, and that stays
        // true when the status is on show.
        val cloudflare = "<html>cloudflare cf-ray</html>"
        TechnicalErrors.withEnabled(false) {
            val message = httpError(403, cloudflare).toUserMessage()
            assertTrue(message.startsWith("Access blocked by Cloudflare"), message)
            assertTrue("Access denied" !in message, message)
        }
        TechnicalErrors.withEnabled(true) {
            assertTrue(
                "Cloudflare" in httpError(403, cloudflare).toUserMessage(),
                httpError(403, cloudflare).toUserMessage(),
            )
        }
    }

    // ── preferDetail ─────────────────────────────────────────────────────────

    @Test fun `preferDetail shows the server's instruction without the status`() {
        // For the endpoints whose 4xx detail IS what to do next. Replacing one
        // of these with "Invalid request" throws away the only actionable part.
        TechnicalErrors.withEnabled(false) {
            assertEquals(
                "Set your system to public first",
                httpError(400, detailBody("Set your system to public first"))
                    .toUserMessage(preferDetail = true),
            )
        }
    }

    @Test fun `preferDetail falls back to the summary when there is no detail`() {
        TechnicalErrors.withEnabled(false) {
            assertEquals("Invalid request", httpError(400).toUserMessage(preferDetail = true))
        }
    }

    @Test fun `a validation list is not prose, so it is not shown as the message`() {
        // 422 answers with a list of field errors rather than a string. Putting
        // that in front of somebody as a sentence would be worse than the
        // summary.
        TechnicalErrors.withEnabled(false) {
            assertEquals(
                "Invalid data provided",
                httpError(422, """{"detail":[{"loc":["body","name"],"msg":"required"}]}""")
                    .toUserMessage(preferDetail = true),
            )
        }
    }

    // ── Reading the body more than once ──────────────────────────────────────

    @Test fun `the detail can be read repeatedly`() {
        // An error body is a one-shot stream and `string()` closes it, which is
        // why the sharing flows used to read it defensively and warn about
        // `toUserMessage` racing them. Both now read it on the same exception,
        // in whatever order, so peeking rather than consuming is load-bearing.
        val error = httpError(403, detailBody("Incorrect password"))
        assertEquals("Incorrect password", error.errorDetail())
        assertEquals("Incorrect password", error.errorDetail())
        assertEquals("Incorrect password", error.errorDetail())
    }

    @Test fun `formatting a message does not consume the detail`() {
        val error = httpError(403, detailBody("Incorrect password"))
        TechnicalErrors.withEnabled(true) {
            assertEquals("[403] Incorrect password", error.toUserMessage())
        }
        // The sharing classifier reads the same body afterwards to tell
        // "credentials rejected" from "publishing is off".
        assertEquals("Incorrect password", error.errorDetail())
    }

    @Test fun `an empty or unparseable body is simply no detail`() {
        assertNull(httpError(500).errorDetail())
        assertNull(httpError(500, "<html>nginx</html>").errorDetail())
        assertNull(httpError(500, """{"something_else":"x"}""").errorDetail())
        assertNull(httpError(500, """{"detail":""}""").errorDetail())
    }

    // ── Carrying the preference on the account ───────────────────────────────

    @Test fun `the preference round-trips through the client settings blob`() {
        assertEquals(true, TechnicalErrors.fromClientSettings(TechnicalErrors.toClientSettings(true)))
        assertEquals(false, TechnicalErrors.fromClientSettings(TechnicalErrors.toClientSettings(false)))
    }

    @Test fun `an account that never set it reads as null, not false`() {
        // Absent means "this device decides". Reading it as false would reset
        // a device's setting to off on every load against such an account.
        assertNull(TechnicalErrors.fromClientSettings(null))
        assertNull(TechnicalErrors.fromClientSettings(emptyMap()))
        assertNull(TechnicalErrors.fromClientSettings(mapOf("theme" to "dark")))
        assertNull(TechnicalErrors.fromClientSettings(mapOf("developer" to emptyMap<String, Any>())))
    }

    @Test fun `a wrongly typed value is ignored rather than guessed at`() {
        assertNull(
            TechnicalErrors.fromClientSettings(
                mapOf("developer" to mapOf("show_technical_errors" to "true")),
            ),
        )
    }

    @Test fun `the patch body carries only this setting's own group`() {
        // PATCH merges top-level keys, so sending anything wider would clobber
        // a sibling key written by another feature.
        val body = TechnicalErrors.toClientSettings(true)
        assertEquals(setOf("developer"), body.keys)
    }
}
