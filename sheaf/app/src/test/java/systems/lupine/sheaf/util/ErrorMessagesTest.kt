package systems.lupine.sheaf.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorMessagesTest {

    @Test
    fun `unknown host maps to connectivity message`() {
        assertEquals(
            "Can't reach the server — check your internet connection",
            UnknownHostException("nope").toUserMessage(),
        )
    }

    @Test
    fun `connect exception maps to connectivity message`() {
        assertEquals(
            "Unable to connect — check your internet connection",
            ConnectException("refused").toUserMessage(),
        )
    }

    @Test
    fun `socket timeout maps to retry message`() {
        assertEquals(
            "Connection timed out — please try again",
            SocketTimeoutException().toUserMessage(),
        )
    }

    @Test
    fun `generic IO exception maps to network error`() {
        assertEquals(
            "Network error — check your connection and try again",
            IOException("disk full or whatever").toUserMessage(),
        )
    }

    @Test
    fun `unmapped exception falls back to default`() {
        assertEquals(
            "Something went wrong — please try again",
            IllegalStateException("boom").toUserMessage(),
        )
    }

    @Test
    fun `unmapped exception uses caller-supplied fallback`() {
        assertEquals(
            "custom fallback",
            IllegalStateException("boom").toUserMessage(fallback = "custom fallback"),
        )
    }

    @Test
    fun `http 401 maps to session expired`() {
        assertEquals(
            "Session expired — please log in again",
            httpException(401).toUserMessage(),
        )
    }

    @Test
    fun `http 422 maps to invalid data`() {
        assertEquals(
            "Invalid data provided",
            httpException(422).toUserMessage(),
        )
    }

    @Test
    fun `http 429 maps to rate limit`() {
        assertEquals(
            "Too many requests — please wait a moment and try again",
            httpException(429).toUserMessage(),
        )
    }

    @Test
    fun `http 409 falls through to caller fallback`() {
        // 409 is intentionally caller-handled — we want the supplied fallback through.
        assertEquals(
            "name already taken",
            httpException(409).toUserMessage(fallback = "name already taken"),
        )
    }

    @Test
    fun `cloudflare body trumps generic 403 mapping`() {
        val message = httpException(
            code = 403,
            body = """<html>Access denied | sheaf.example.com used Cloudflare to restrict access</html>""",
        ).toUserMessage()
        assertTrue(
            "cloudflare" in message.lowercase(),
            "expected cloudflare-specific message, got: $message",
        )
    }

    @Test
    fun `cf-ray header marker also triggers cloudflare branch`() {
        val message = httpException(
            code = 502,
            body = """<html>cf-ray: abc123</html>""",
        ).toUserMessage()
        assertTrue(
            "cloudflare" in message.lowercase(),
            "expected cloudflare-specific message, got: $message",
        )
    }

    @Test
    fun `non-cloudflare 502 uses standard mapping`() {
        assertEquals(
            "Server unavailable — please try again later",
            httpException(502, body = "<html>nginx</html>").toUserMessage(),
        )
    }

    private fun httpException(code: Int, body: String = ""): HttpException {
        val response = Response.error<Any>(
            code,
            body.toResponseBody("text/plain".toMediaType()),
        )
        return HttpException(response)
    }
}
