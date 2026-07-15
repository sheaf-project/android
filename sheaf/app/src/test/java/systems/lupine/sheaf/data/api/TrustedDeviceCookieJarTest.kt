package systems.lupine.sheaf.data.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scoping test. The jar persists exactly one cookie and attaches it to exactly
 * one path prefix; widening either is a disclosure (the trusted-device token
 * riding along to, say, an avatar CDN request) or a surprise (browser session
 * cookies shadowing our bearer tokens).
 */
class TrustedDeviceCookieJarTest {

    private val prefs = mockk<PreferencesRepository>(relaxed = true).also {
        // isApiOrigin reads the configured base URL to host-scope the cookie.
        every { it.baseUrl } returns flowOf("https://app.sheaf.sh")
    }
    private val jar = TrustedDeviceCookieJar(prefs)

    private val authUrl = "https://app.sheaf.sh/v1/auth/login".toHttpUrl()

    private fun cookie(name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain("app.sheaf.sh").path("/").build()

    @Test fun `the trusted-device cookie is persisted`() {
        coEvery { prefs.saveTrustedDeviceCookie(any(), any()) } returns Unit

        jar.saveFromResponse(authUrl, listOf(cookie("sheaf_trusted_device", "tdc-1")))

        coVerify(exactly = 1) { prefs.saveTrustedDeviceCookie("tdc-1", any()) }
    }

    @Test fun `session and refresh cookies are not persisted`() {
        // These are browser shadow-state; mobile uses the bearer tokens from
        // the JSON body. Persisting them would duplicate the session in a
        // second place with different lifetime rules.
        jar.saveFromResponse(
            authUrl,
            listOf(cookie("sheaf_session", "s"), cookie("sheaf_refresh", "r")),
        )

        coVerify(exactly = 0) { prefs.saveTrustedDeviceCookie(any(), any()) }
    }

    @Test fun `the cookie is attached under the auth path`() {
        every { prefs.trustedDeviceCookieBlocking() } returns "tdc-1"

        val cookies = jar.loadForRequest(authUrl)

        assertEquals(1, cookies.size)
        assertEquals("sheaf_trusted_device", cookies[0].name)
        assertEquals("tdc-1", cookies[0].value)
    }

    @Test fun `the cookie is not attached anywhere else`() {
        every { prefs.trustedDeviceCookieBlocking() } returns "tdc-1"

        assertTrue(jar.loadForRequest("https://app.sheaf.sh/v1/members".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://cdn.sheaf.sh/files/avatar.png".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://app.sheaf.sh/v1/fronts/current".toHttpUrl()).isEmpty())
    }

    @Test fun `nothing is attached when no cookie is stored`() {
        every { prefs.trustedDeviceCookieBlocking() } returns null

        assertTrue(jar.loadForRequest(authUrl).isEmpty())
    }

    @Test fun `the cookie is not attached to a different host's auth path`() {
        // Host-scoping: even a /v1/auth/ request to another instance (e.g. after
        // the user changed servers) must not receive the old instance's token.
        every { prefs.trustedDeviceCookieBlocking() } returns "tdc-1"

        assertTrue(jar.loadForRequest("https://other.example.org/v1/auth/login".toHttpUrl()).isEmpty())
    }

    @Test fun `a cookie from a different host is not stored`() {
        jar.saveFromResponse(
            "https://other.example.org/v1/auth/login".toHttpUrl(),
            listOf(cookie("sheaf_trusted_device", "tdc-evil")),
        )

        coVerify(exactly = 0) { prefs.saveTrustedDeviceCookie(any(), any()) }
    }
}
