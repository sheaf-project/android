package systems.lupine.sheaf.data.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The per-hop backstop. Whatever a redirect follow-up or an authenticator retry
 * carried onto a request, credentials must not leave for a host that isn't the
 * configured API origin.
 */
class CredentialGuardInterceptorTest {

    private val prefs = mockk<PreferencesRepository>().apply {
        every { baseUrl } returns flowOf("https://app.sheaf.sh")
    }
    private val guard = CredentialGuardInterceptor(prefs)

    private fun proceedWith(request: Request): Request {
        val forwarded = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(forwarded)) } answers {
            Response.Builder()
                .request(forwarded.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .build()
        }
        guard.intercept(chain)
        return forwarded.captured
    }

    private fun credentialed(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer tok")
        .header("CF-Access-Client-Id", "cf-id")
        .header("CF-Access-Client-Secret", "cf-secret")
        .header("Cookie", "sheaf_trusted_device=tdc")
        .build()

    @Test fun `an API-origin hop keeps its credentials`() {
        val sent = proceedWith(credentialed("https://app.sheaf.sh/v1/members"))
        assertEquals("Bearer tok", sent.header("Authorization"))
        assertEquals("cf-id", sent.header("CF-Access-Client-Id"))
        assertEquals("sheaf_trusted_device=tdc", sent.header("Cookie"))
    }

    @Test fun `a foreign-host hop is stripped of every credential`() {
        val sent = proceedWith(credentialed("https://images.example.com/a.png"))
        assertNull(sent.header("Authorization"))
        assertNull(sent.header("CF-Access-Client-Id"))
        assertNull(sent.header("CF-Access-Client-Secret"))
        assertNull(sent.header("Cookie"))
    }

    @Test fun `the CDN host is foreign to the API origin and is stripped`() {
        // A redirect API -> CDN, or a widget avatar fetch, must not carry creds.
        val sent = proceedWith(credentialed("https://cdn.sheaf.sh/avatars/x.png"))
        assertNull(sent.header("Authorization"))
        assertNull(sent.header("CF-Access-Client-Id"))
    }

    @Test fun `non-credential headers are left alone on a foreign host`() {
        val request = Request.Builder()
            .url("https://images.example.com/a.png")
            .header("Accept", "image/*")
            .header("Authorization", "Bearer tok")
            .build()
        val sent = proceedWith(request)
        assertEquals("image/*", sent.header("Accept"))
        assertNull(sent.header("Authorization"))
    }
}
