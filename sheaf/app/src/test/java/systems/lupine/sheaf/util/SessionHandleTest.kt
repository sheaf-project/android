package systems.lupine.sheaf.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The handle the session list addresses a session by.
 *
 * The raw session id is the `sheaf_session` cookie credential, so no read
 * surface returns it; the list returns `sha256(id)` truncated to 32 hex
 * characters. This client was comparing the raw `sid` from its own token
 * against that digest to find the current session, which cannot match, so the
 * list showed no "this device" and every row was marked as another device.
 *
 * Expected values computed with the server's own algorithm
 * (`sheaf/auth/sessions.py:session_handle`). If the two ever drift, the only
 * symptom is a flag silently false everywhere, so it is worth pinning to fixed
 * digests rather than to a reimplementation of the same formula.
 */
class SessionHandleTest {

    @Test fun `a handle is the first 32 hex characters of the sha-256`() {
        assertEquals(
            "a765c1df3e91544f89f5555c47390b3c",
            sessionHandle("brvY6W1UUbqx_MKeII4CBAYLFk5yA9ubnt4nV3gr10U"),
        )
        assertEquals("ba7816bf8f01cfea414140de5dae2223", sessionHandle("abc"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb924", sessionHandle(""))
    }

    @Test fun `a handle is 32 characters and lower case hex`() {
        val handle = sessionHandle("some-session-id")
        assertEquals(32, handle.length)
        assertEquals(handle, handle.lowercase())
        assertEquals(true, handle.all { it in "0123456789abcdef" })
    }

    @Test fun `the handle is not the id it came from`() {
        // The whole point: returning the raw id would hand out a replayable
        // credential, which is why a direct comparison could never work.
        val id = "brvY6W1UUbqx_MKeII4CBAYLFk5yA9ubnt4nV3gr10U"
        assertEquals(false, sessionHandle(id) == id)
    }
}
