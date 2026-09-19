package systems.lupine.sheaf.util

import systems.lupine.sheaf.data.model.ServerVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Version strings are where this sort of check rots: a self-hoster on a build
 * off main, a fork, a release candidate. The rule these pin is that an
 * unreadable version says "I don't know" rather than "too old" - behaviour is
 * decided by feature detection, and this only ever picks the wording.
 */
class ServerVersionsTest {

    private fun server(v: String?, tag: String? = null) =
        ServerVersion(version = v, gitTag = tag)

    @Test fun `plain versions parse`() {
        assertEquals(ServerSemVer(1, 5, 1), parseServerVersion("1.5.1"))
        assertEquals(ServerSemVer(1, 5, 0), parseServerVersion("1.5"))
        assertEquals(ServerSemVer(2, 0, 0), parseServerVersion("2"))
    }

    @Test fun `a leading v and a build suffix are ignored`() {
        assertEquals(ServerSemVer(1, 5, 1), parseServerVersion("v1.5.1"))
        assertEquals(ServerSemVer(1, 5, 1), parseServerVersion("1.5.1.dev3+g8ab21c"))
        assertEquals(ServerSemVer(1, 5, 1), parseServerVersion("1.5.1-rc1"))
    }

    @Test fun `nonsense is null, not zero`() {
        // Zero would compare as "ancient" and tell somebody to upgrade a server
        // that might be newer than us.
        assertNull(parseServerVersion(null))
        assertNull(parseServerVersion(""))
        assertNull(parseServerVersion("   "))
        assertNull(parseServerVersion("nightly"))
    }

    @Test fun `ordering works across components`() {
        assertTrue(parseServerVersion("1.5.1")!! > parseServerVersion("1.5.0")!!)
        assertTrue(parseServerVersion("1.6.0")!! > parseServerVersion("1.5.99")!!)
        assertTrue(parseServerVersion("2.0.0")!! > parseServerVersion("1.99.99")!!)
        assertEquals(parseServerVersion("1.5.1"), parseServerVersion("1.5.1"))
    }

    @Test fun `at least compares against the floor`() {
        assertEquals(true, server("1.5.1").isAtLeast("1.5.1"))
        assertEquals(true, server("1.6.0").isAtLeast("1.5.1"))
        assertEquals(false, server("1.5.0").isAtLeast("1.5.1"))
    }

    @Test fun `a release candidate counts as having the code`() {
        // Somebody running 1.5.1-rc1 has the endpoint. Telling them to upgrade
        // to 1.5.1 for a feature they already have would be the worse error.
        assertEquals(true, server("1.5.1-rc1").isAtLeast("1.5.1"))
    }

    @Test fun `a git tag wins over the package version`() {
        // A build from a tag knows what it is more precisely than its metadata.
        assertEquals(true, server(v = "1.5.0", tag = "v1.5.2").isAtLeast("1.5.1"))
    }

    @Test fun `unknown stays unknown`() {
        assertNull(server(null).isAtLeast("1.5.1"))
        assertNull(server("nightly").isAtLeast("1.5.1"))
        assertNull(null.isAtLeast("1.5.1"))
    }

    // ── Wording ───────────────────────────────────────────────────────────────

    @Test fun `the message names what is needed and what is running`() {
        val message = serverTooOldMessage(
            feature = "filtering by group",
            required = "1.5.1",
            server = server("1.5.0"),
        )
        assertTrue("1.5.1 or later" in message, message)
        assertTrue("this one is 1.5.0" in message, message)
    }

    @Test fun `with no version to report it says only what is needed`() {
        // Better a shorter sentence than "this one is null".
        val message = serverTooOldMessage("filtering by group", "1.5.1", server(null))
        assertTrue("1.5.1 or later" in message, message)
        assertTrue("this one is" !in message, message)
    }
}
