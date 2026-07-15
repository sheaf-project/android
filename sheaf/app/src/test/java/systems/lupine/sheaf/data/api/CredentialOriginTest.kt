package systems.lupine.sheaf.data.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Credentials (bearer, CF-Access secrets, the trusted-device cookie) may reach
 * only the configured API origin. Images use a separate credential-free client,
 * so the CDN is deliberately not trusted here.
 */
class CredentialOriginTest {

    private fun url(u: String) = u.toHttpUrl()

    private val api = "https://app.sheaf.sh"

    @Test fun `a request to the API origin matches`() {
        assertTrue(originMatches(url("https://app.sheaf.sh/v1/members"), api))
    }

    @Test fun `an external host never matches`() {
        assertFalse(originMatches(url("https://evil.example.com/x.png"), api))
        assertFalse(originMatches(url("https://imgur.com/a.png"), api))
    }

    @Test fun `the image CDN host is not the API origin`() {
        // The API client must not send the session to the CDN; images fetch it
        // via their own client with no credentials anyway.
        assertFalse(originMatches(url("https://cdn.sheaf.sh/avatars/x.png"), api))
    }

    @Test fun `a lookalike host does not match`() {
        assertFalse(originMatches(url("https://app.sheaf.sh.evil.com/x"), api))
        assertFalse(originMatches(url("https://notapp.sheaf.sh/x"), api))
    }

    @Test fun `scheme must match`() {
        // http vs https is a different origin; a downgrade must not carry creds.
        assertFalse(originMatches(url("http://app.sheaf.sh/v1/members"), api))
    }

    @Test fun `port must match`() {
        assertFalse(originMatches(url("https://app.sheaf.sh:8443/v1/members"), api))
    }

    @Test fun `the path prefix on the base URL is ignored`() {
        val based = "https://example.org/sheaf"
        assertTrue(originMatches(url("https://example.org/v1/members"), based))
        assertTrue(originMatches(url("https://example.org/sheaf/v1/members"), based))
    }

    @Test fun `host comparison is case-insensitive`() {
        assertTrue(originMatches(url("https://APP.sheaf.sh/v1/members"), api))
    }

    @Test fun `nothing matches when no base is configured`() {
        assertFalse(originMatches(url("https://app.sheaf.sh/v1/members"), null))
        assertFalse(originMatches(url("https://app.sheaf.sh/v1/members"), ""))
    }

    // ── Server-change detection ─────────────────────────────────────────────

    @Test fun `the same origin is recognised despite path or trailing slash`() {
        assertTrue(sameConfiguredOrigin("https://app.sheaf.sh", "https://app.sheaf.sh/"))
        assertTrue(sameConfiguredOrigin("https://example.org/sheaf", "https://example.org/other"))
        assertTrue(sameConfiguredOrigin("app.sheaf.sh", "https://app.sheaf.sh"))
    }

    @Test fun `a different host is a different origin`() {
        assertFalse(sameConfiguredOrigin("https://app.sheaf.sh", "https://other.example.org"))
    }

    @Test fun `first-time setup counts as a change`() {
        // previous is unset, new is a real server: must be treated as a switch
        // so the (empty) teardown runs and nothing stale is assumed.
        assertFalse(sameConfiguredOrigin(null, "https://app.sheaf.sh"))
        assertFalse(sameConfiguredOrigin("", "https://app.sheaf.sh"))
    }

    @Test fun `two unset values are the same`() {
        assertTrue(sameConfiguredOrigin(null, null))
        assertTrue(sameConfiguredOrigin("", "   "))
    }
}
