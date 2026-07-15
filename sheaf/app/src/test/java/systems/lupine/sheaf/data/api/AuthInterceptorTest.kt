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
 * The end-to-end check on the credential leak: the bearer and Cloudflare Access
 * headers must appear only on requests to the instance's own origins, never on a
 * request to an external image host (the image client shares this interceptor).
 */
class AuthInterceptorTest {

    private val prefs = mockk<PreferencesRepository>().apply {
        every { baseUrl } returns flowOf("https://app.sheaf.sh")
        every { fileCdnBase } returns flowOf("https://cdn.sheaf.sh")
        every { accessToken } returns flowOf("access-tok")
        every { cfClientId } returns flowOf("cf-id")
        every { cfClientSecret } returns flowOf("cf-secret")
    }
    private val interceptor = AuthInterceptor(prefs)

    /** Runs the interceptor against [urlString] and returns the outgoing request. */
    private fun send(urlString: String): Request {
        val request = Request.Builder().url(urlString).build()
        val sent = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(capture(sent)) } answers {
            Response.Builder()
                .request(sent.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .build()
        }
        interceptor.intercept(chain)
        return sent.captured
    }

    @Test fun `API requests carry the bearer and CF headers`() {
        val sent = send("https://app.sheaf.sh/v1/members")
        assertEquals("Bearer access-tok", sent.header("Authorization"))
        assertEquals("cf-id", sent.header("CF-Access-Client-Id"))
        assertEquals("cf-secret", sent.header("CF-Access-Client-Secret"))
    }

    @Test fun `CDN image requests are still credentialed`() {
        val sent = send("https://cdn.sheaf.sh/avatars/x.png")
        assertEquals("Bearer access-tok", sent.header("Authorization"))
        assertEquals("cf-id", sent.header("CF-Access-Client-Id"))
    }

    @Test fun `an external image host receives no credentials`() {
        val sent = send("https://images.example.com/remote-avatar.png")
        assertNull(sent.header("Authorization"))
        assertNull(sent.header("CF-Access-Client-Id"))
        assertNull(sent.header("CF-Access-Client-Secret"))
    }

    @Test fun `the client header is always sent, even to external hosts`() {
        // Not a credential; safe (and useful) everywhere.
        val sent = send("https://images.example.com/remote-avatar.png")
        assertEquals("Sheaf Android/${systems.lupine.sheaf.BuildConfig.VERSION_NAME}", sent.header("X-Sheaf-Client"))
    }

    @Test fun `the in-memory pending token is used during intermediate auth`() {
        every { prefs.accessToken } returns flowOf(null)
        interceptor.pendingToken = "pending-tok"
        val sent = send("https://app.sheaf.sh/v1/users/me")
        assertEquals("Bearer pending-tok", sent.header("Authorization"))
    }
}
