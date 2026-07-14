package systems.lupine.sheaf.data.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Retrofit is configured with the placeholder base http://localhost/ and every
 * endpoint path is absolute, so these mirror what the interceptor actually sees.
 */
class ApplyBaseUrlTest {

    private fun rewrite(request: String, base: String): String =
        applyBaseUrl(request.toHttpUrl(), base.toHttpUrl()).toString()

    @Test fun `origin-root base swaps scheme host and port`() {
        assertEquals(
            "https://app.sheaf.sh/v1/members",
            rewrite("http://localhost/v1/members", "https://app.sheaf.sh"),
        )
    }

    @Test fun `base with a path prefix keeps the prefix`() {
        assertEquals(
            "https://example.org/sheaf/v1/members",
            rewrite("http://localhost/v1/members", "https://example.org/sheaf"),
        )
    }

    @Test fun `trailing slash on the prefix is not doubled`() {
        assertEquals(
            "https://example.org/sheaf/v1/members",
            rewrite("http://localhost/v1/members", "https://example.org/sheaf/"),
        )
    }

    @Test fun `nested path prefix is preserved in order`() {
        assertEquals(
            "https://example.org/apps/sheaf/v1/fronts/current",
            rewrite("http://localhost/v1/fronts/current", "https://example.org/apps/sheaf"),
        )
    }

    @Test fun `non-default port is carried over`() {
        assertEquals(
            "https://example.org:8443/sheaf/v1/members",
            rewrite("http://localhost/v1/members", "https://example.org:8443/sheaf"),
        )
    }

    @Test fun `query params survive the rewrite`() {
        assertEquals(
            "https://example.org/sheaf/v1/export?format=openplural",
            rewrite("http://localhost/v1/export?format=openplural", "https://example.org/sheaf"),
        )
    }

    @Test fun `encoded path segments are not double-encoded`() {
        assertEquals(
            "https://example.org/sheaf/v1/members/a%20b",
            rewrite("http://localhost/v1/members/a%20b", "https://example.org/sheaf"),
        )
    }

    @Test fun `cleartext loopback base is left as http`() {
        assertEquals(
            "http://10.0.2.2:8000/v1/members",
            rewrite("http://localhost/v1/members", "http://10.0.2.2:8000"),
        )
    }
}
