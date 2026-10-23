package systems.lupine.sheaf.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Resolving the server-relative file paths the API returns.
 *
 * The case that mattered: the server returns
 * `/v1/files/avatars/<system>/<file>.png`, the watch handed that straight to
 * Coil and to `Request.Builder().url(...)`, and every image avatar on the
 * watch rendered as an empty circle while the same avatar was fine on the
 * phone. Both failures were silent, which is why this is pinned at the
 * resolver rather than left to the two call sites.
 *
 * The rules are the phone's `RelativeUrlInterceptor`, deliberately: the two
 * clients must not disagree about where a file lives.
 */
class FileUrlsTest {

    private val base = "https://sheaf.example.com"
    private val cdn = "https://files.example.net"
    private val avatar = "/v1/files/avatars/abc/def.png"

    @Test fun `a server-relative path resolves against the instance`() {
        assertEquals(
            "https://sheaf.example.com/v1/files/avatars/abc/def.png",
            resolveFileUrl(avatar, base, cdnBase = null),
        )
    }

    @Test fun `a server-relative path prefers the CDN when there is one`() {
        assertEquals(
            "https://files.example.net/v1/files/avatars/abc/def.png",
            resolveFileUrl(avatar, base, cdn),
        )
    }

    @Test fun `a bare relative path is treated as server-relative`() {
        assertEquals(
            "https://sheaf.example.com/avatars/abc.png",
            resolveFileUrl("avatars/abc.png", base, cdnBase = null),
        )
        assertEquals(
            "https://files.example.net/avatars/abc.png",
            resolveFileUrl("avatars/abc.png", base, cdn),
        )
    }

    @Test fun `an absolute URL on the API host moves to the CDN`() {
        assertEquals(
            "https://files.example.net/v1/files/avatars/abc/def.png",
            resolveFileUrl("$base$avatar", base, cdn),
        )
    }

    @Test fun `an absolute URL on the API host is left alone without a CDN`() {
        assertEquals(
            "https://sheaf.example.com/v1/files/avatars/abc/def.png",
            resolveFileUrl("$base$avatar", base, cdnBase = null),
        )
    }

    @Test fun `somebody else's absolute URL is never rewritten`() {
        // A member may point their avatar at an external host. Moving that to
        // the CDN would ask for a file that was never uploaded there.
        val foreign = "https://cdn.somewhere-else.org/pic.png"
        assertEquals(foreign, resolveFileUrl(foreign, base, cdn))
        assertEquals(foreign, resolveFileUrl(foreign, base, cdnBase = null))
    }

    @Test fun `a trailing slash on either base does not double up`() {
        assertEquals(
            "https://sheaf.example.com/v1/files/avatars/abc/def.png",
            resolveFileUrl(avatar, "$base/", cdnBase = null),
        )
        assertEquals(
            "https://files.example.net/v1/files/avatars/abc/def.png",
            resolveFileUrl(avatar, base, "$cdn/"),
        )
    }

    @Test fun `a relative path with nothing to resolve against is unusable`() {
        // Before the watch has been paired there is no base URL. Returning
        // null lets the caller draw the initials circle instead of asking for
        // a URL that cannot work.
        assertNull(resolveFileUrl(avatar, baseUrl = null, cdnBase = null))
        assertNull(resolveFileUrl(avatar, baseUrl = "", cdnBase = ""))
        assertNull(resolveFileUrl("avatars/abc.png", baseUrl = null, cdnBase = null))
    }

    @Test fun `nothing in means nothing out`() {
        assertNull(resolveFileUrl(null, base, cdn))
        assertNull(resolveFileUrl("", base, cdn))
        assertNull(resolveFileUrl("   ", base, cdn))
    }

    @Test fun `a blank CDN is the same as no CDN`() {
        // The config carries null for an instance without one, but an operator
        // can leave the setting as an empty string, and "https:///v1/..." is
        // not a URL anybody can fetch.
        assertEquals(
            "https://sheaf.example.com/v1/files/avatars/abc/def.png",
            resolveFileUrl(avatar, base, cdnBase = "  "),
        )
    }

    @Test fun `a host that merely starts the same is not the API host`() {
        // `https://sheaf.example.com.evil.test/...` shares a prefix with the
        // base URL but is a different host, so it must not be rewritten onto
        // the CDN. The trailing slash in the prefix check is what stops it.
        val lookalike = "https://sheaf.example.com.evil.test/v1/files/x.png"
        assertEquals(lookalike, resolveFileUrl(lookalike, base, cdn))
    }
}
