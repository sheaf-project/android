package systems.lupine.sheaf.ui.sessions

import systems.lupine.sheaf.data.model.SessionRead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which sessions "Revoke all other sessions" actually targets.
 *
 * It used to call `POST /auth/sessions/revoke-others`, which identifies the
 * caller's session from the `sheaf_session` cookie. This client authenticates
 * with a bearer token and keeps only the trusted-device cookie, so every
 * attempt came back 400 "No current session" and the button never revoked
 * anything. It now revokes each handle in turn, which makes "which handles"
 * the part worth pinning: revoking the current one would sign the user out of
 * the phone they are holding.
 */
class RevokeOtherSessionsTest {

    private fun session(id: String, current: Boolean = false) = SessionRead(
        id = id,
        nickname = null,
        clientName = "Sheaf Android",
        createdAt = null,
        createdIp = null,
        lastActiveAt = null,
        lastActiveIp = null,
        isCurrent = current,
    )

    @Test fun `the current session is never revoked`() {
        val sessions = listOf(session("a"), session("b", current = true), session("c"))
        assertEquals(listOf("a", "c"), sessionsToRevoke(sessions).map { it.id })
    }

    @Test fun `nothing to do when this is the only session`() {
        assertTrue(sessionsToRevoke(listOf(session("a", current = true))).isEmpty())
    }

    @Test fun `an empty list is not an error`() {
        assertTrue(sessionsToRevoke(emptyList()).isEmpty())
    }

    @Test fun `a list with no current session still spares nothing by accident`() {
        // The server marks the caller, so this should not happen. If it ever
        // does, revoking everything is the honest reading of "all others" and
        // the worst case is signing this device out, which is recoverable.
        val sessions = listOf(session("a"), session("b"))
        assertEquals(listOf("a", "b"), sessionsToRevoke(sessions).map { it.id })
    }
}
