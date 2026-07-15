package systems.lupine.sheaf.data.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Credentials (bearer, CF-Access secrets, the trusted-device cookie) may reach
 * only the instance's own origins. This is what stops the shared image client
 * from handing the user's session to whatever host an avatar or bio-embedded
 * image points at.
 */
class CredentialOriginTest {

    private fun url(u: String) = u.toHttpUrl()

    private val api = "https://app.sheaf.sh"
    private val cdn = "https://cdn.sheaf.sh"

    @Test fun `a request to the API origin is trusted`() {
        assertTrue(isTrustedCredentialOrigin(url("https://app.sheaf.sh/v1/members"), api, cdn))
    }

    @Test fun `a request to the configured CDN origin is trusted`() {
        assertTrue(isTrustedCredentialOrigin(url("https://cdn.sheaf.sh/avatars/x.png"), api, cdn))
    }

    @Test fun `an external host is never trusted`() {
        assertFalse(isTrustedCredentialOrigin(url("https://evil.example.com/x.png"), api, cdn))
        assertFalse(isTrustedCredentialOrigin(url("https://imgur.com/a.png"), api, cdn))
    }

    @Test fun `a lookalike host is not trusted`() {
        assertFalse(isTrustedCredentialOrigin(url("https://app.sheaf.sh.evil.com/x"), api, cdn))
        assertFalse(isTrustedCredentialOrigin(url("https://notapp.sheaf.sh/x"), api, cdn))
    }

    @Test fun `scheme must match`() {
        // http vs https is a different origin; a downgrade must not carry creds.
        assertFalse(isTrustedCredentialOrigin(url("http://app.sheaf.sh/v1/members"), api, cdn))
    }

    @Test fun `port must match`() {
        assertFalse(isTrustedCredentialOrigin(url("https://app.sheaf.sh:8443/v1/members"), api, cdn))
    }

    @Test fun `the path prefix on the base URL is ignored for origin matching`() {
        val based = "https://example.org/sheaf"
        assertTrue(isTrustedCredentialOrigin(url("https://example.org/v1/members"), based, null))
        assertTrue(isTrustedCredentialOrigin(url("https://example.org/sheaf/v1/members"), based, null))
    }

    @Test fun `host comparison is case-insensitive`() {
        assertTrue(isTrustedCredentialOrigin(url("https://APP.sheaf.sh/v1/members"), api, cdn))
    }

    @Test fun `no CDN configured means only the API origin is trusted`() {
        assertTrue(isTrustedCredentialOrigin(url("https://app.sheaf.sh/v1/members"), api, null))
        assertFalse(isTrustedCredentialOrigin(url("https://cdn.sheaf.sh/x"), api, null))
    }

    @Test fun `a scheme-less CDN base is assumed https`() {
        assertTrue(isTrustedCredentialOrigin(url("https://cdn.sheaf.sh/x"), api, "cdn.sheaf.sh"))
    }

    @Test fun `nothing is trusted when no base is configured`() {
        assertFalse(isTrustedCredentialOrigin(url("https://app.sheaf.sh/v1/members"), null, null))
        assertFalse(isTrustedCredentialOrigin(url("https://app.sheaf.sh/v1/members"), "", null))
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
