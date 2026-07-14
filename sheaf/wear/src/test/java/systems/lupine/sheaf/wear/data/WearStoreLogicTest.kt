package systems.lupine.sheaf.wear.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The watch's drop-vs-keep decision for a failed queued switch. It has to agree
 * with the phone's SyncWorker.isPermanentHttpFailure, or the two surfaces treat
 * the same server response differently: one drops the switch, the other retries
 * it forever.
 */
class WearStoreLogicTest {

    @Test fun `a bad request is permanent`() {
        assertTrue(isPermanentSwitchError(400))
        assertTrue(isPermanentSwitchError(404))
        assertTrue(isPermanentSwitchError(409))
        assertTrue(isPermanentSwitchError(422))
    }

    @Test fun `auth, timeout and rate-limit are transient`() {
        assertFalse(isPermanentSwitchError(401))
        assertFalse(isPermanentSwitchError(403))
        assertFalse(isPermanentSwitchError(408))
        assertFalse(isPermanentSwitchError(429))
    }

    @Test fun `server errors and non-4xx are transient`() {
        assertFalse(isPermanentSwitchError(500))
        assertFalse(isPermanentSwitchError(503))
        assertFalse(isPermanentSwitchError(399))
        assertFalse(isPermanentSwitchError(200))
    }

    @Test fun `member display fallback prefers display name`() {
        assertEquals("Ash", wearMember(name = "ashley", displayName = "Ash").displayNameOrName)
        assertEquals("ashley", wearMember(name = "ashley", displayName = "  ").displayNameOrName)
    }

    @Test fun `member initials never come out empty`() {
        assertEquals("AB", wearMember("Alex Bell").initials)
        assertEquals("A", wearMember("Alex").initials)
        assertEquals("?", wearMember(" ").initials)
        assertEquals("?", wearMember("").initials)
    }

    private fun wearMember(name: String, displayName: String? = null) = WearMember(
        id = "m1",
        name = name,
        displayName = displayName,
        description = null,
        pronouns = null,
        avatarUrl = null,
        color = null,
    )
}
