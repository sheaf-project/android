package systems.lupine.sheaf.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizeBaseUrlTest {
    @Test fun `bare host gets https prefix`() {
        assertEquals("https://app.sheaf.sh", normalizeBaseUrl("app.sheaf.sh"))
    }

    @Test fun `host with port gets https prefix`() {
        assertEquals("https://app.sheaf.sh:8080", normalizeBaseUrl("app.sheaf.sh:8080"))
    }

    @Test fun `existing https is preserved`() {
        assertEquals("https://app.sheaf.sh", normalizeBaseUrl("https://app.sheaf.sh"))
    }

    @Test fun `existing http is preserved verbatim`() {
        assertEquals("http://localhost:3000", normalizeBaseUrl("http://localhost:3000"))
    }

    @Test fun `mixed-case scheme is preserved`() {
        // toHttpUrlOrNull normalises to lowercase later; we just need to not
        // treat it as schemeless.
        assertEquals("HTTPS://app.sheaf.sh", normalizeBaseUrl("HTTPS://app.sheaf.sh"))
    }

    @Test fun `whitespace is stripped`() {
        assertEquals("https://app.sheaf.sh", normalizeBaseUrl("  app.sheaf.sh  "))
    }

    @Test fun `trailing slash is stripped`() {
        assertEquals("https://app.sheaf.sh", normalizeBaseUrl("https://app.sheaf.sh/"))
    }

    @Test fun `bare host with trailing slash gets prefix and trim`() {
        assertEquals("https://app.sheaf.sh", normalizeBaseUrl("app.sheaf.sh/"))
    }

    @Test fun `empty input stays empty`() {
        assertEquals("", normalizeBaseUrl(""))
        assertEquals("", normalizeBaseUrl("   "))
    }

    @Test fun `path-only input is treated as schemeless host`() {
        // Edge: user types `localhost` (intended http localhost). They have
        // to type `http://localhost` explicitly to opt out of TLS.
        assertEquals("https://localhost", normalizeBaseUrl("localhost"))
    }
}
