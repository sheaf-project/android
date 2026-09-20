package systems.lupine.sheaf.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
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

    // Shaped like what Android throws: the handshake exception wraps the
    // validator's verdict a level or two down.
    private fun handshake(cause: Throwable) =
        SSLHandshakeException("handshake failed").apply { initCause(cause) }

    @Test
    fun `untrusted certificate says so instead of blaming the network`() {
        val e = handshake(
            CertificateException(CertPathValidatorException("Trust anchor for certification path not found.")),
        )
        assertEquals(
            "The server's certificate isn't trusted. Sheaf doesn't use certificates installed " +
                "on this device, so the server needs one from a public certificate authority",
            e.toUserMessage(),
        )
    }

    @Test
    fun `expired certificate is not reported as untrusted`() {
        val e = handshake(
            CertificateException(
                CertPathValidatorException("timestamp check failed", CertificateExpiredException()),
            ),
        )
        assertEquals(
            "The server's certificate has expired or isn't valid yet. Check this device's date " +
                "and time, or renew the certificate",
            e.toUserMessage(),
        )
    }

    @Test
    fun `certificate for another hostname says the address does not match`() {
        assertEquals(
            "The server's certificate doesn't match this address. Use the address the " +
                "certificate was issued for",
            SSLPeerUnverifiedException("Hostname 10.0.0.5 not verified").toUserMessage(),
        )
    }

    @Test
    fun `a handshake failure with no certificate cause stays a network error`() {
        assertEquals(
            IOException("x").toUserMessage(),
            SSLHandshakeException("Connection closed by peer").toUserMessage(),
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
