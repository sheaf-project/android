package systems.lupine.sheaf.data.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import systems.lupine.sheaf.data.db.PendingFrontRemoval
import systems.lupine.sheaf.data.db.PendingFrontSwitch
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The offline queue's two decisions: what order to replay in, and whether a
 * failure is worth retrying. Both are silently destructive when wrong.
 */
class SyncQueueTest {

    private fun http(code: Int): HttpException =
        HttpException(Response.error<Unit>(code, "".toResponseBody("text/plain".toMediaType())))

    // ── Ordering ────────────────────────────────────────────────────────────

    @Test fun `ops replay interleaved in the order the user made them`() {
        val ops = mergeQueuedOps(
            removals = listOf(
                PendingFrontRemoval(id = 1, memberId = "m1", createdAt = 200),
                PendingFrontRemoval(id = 2, memberId = "m2", createdAt = 400),
            ),
            switches = listOf(
                PendingFrontSwitch(id = 1, memberIds = "a", createdAt = 100),
                PendingFrontSwitch(id = 2, memberIds = "b", createdAt = 300),
            ),
        )
        // Not all-removals-then-all-switches: a switch made before a removal
        // must be replayed before it, or the rebuilt timeline is wrong.
        assertEquals(listOf(100L, 200L, 300L, 400L), ops.map { it.createdAt })
        assertTrue(ops[0] is QueuedOp.Switch)
        assertTrue(ops[1] is QueuedOp.Removal)
        assertTrue(ops[2] is QueuedOp.Switch)
        assertTrue(ops[3] is QueuedOp.Removal)
    }

    @Test fun `an empty queue produces no ops`() {
        assertEquals(emptyList(), mergeQueuedOps(emptyList(), emptyList()))
    }

    @Test fun `either list alone still comes out sorted`() {
        val switchesOnly = mergeQueuedOps(
            removals = emptyList(),
            switches = listOf(
                PendingFrontSwitch(id = 1, memberIds = "a", createdAt = 500),
                PendingFrontSwitch(id = 2, memberIds = "b", createdAt = 100),
            ),
        )
        assertEquals(listOf(100L, 500L), switchesOnly.map { it.createdAt })
    }

    // ── Failure classification ──────────────────────────────────────────────

    @Test fun `a bad request is permanent - drop it rather than wedge the queue`() {
        assertTrue(isPermanentHttpFailure(http(400)))
        assertTrue(isPermanentHttpFailure(http(404)))
        assertTrue(isPermanentHttpFailure(http(409)))
        assertTrue(isPermanentHttpFailure(http(422)))
    }

    @Test fun `auth failures are transient - the authenticator recovers them`() {
        // Classing these permanent would silently delete the user's queued
        // switch the moment their token expired.
        assertFalse(isPermanentHttpFailure(http(401)))
        assertFalse(isPermanentHttpFailure(http(403)))
    }

    @Test fun `timeout and rate limiting are transient`() {
        assertFalse(isPermanentHttpFailure(http(408)))
        assertFalse(isPermanentHttpFailure(http(429)))
    }

    @Test fun `server errors are transient`() {
        assertFalse(isPermanentHttpFailure(http(500)))
        assertFalse(isPermanentHttpFailure(http(502)))
        assertFalse(isPermanentHttpFailure(http(503)))
    }

    @Test fun `network failures are transient`() {
        assertFalse(isPermanentHttpFailure(IOException("no route to host")))
        assertFalse(isPermanentHttpFailure(RuntimeException("boom")))
    }
}
