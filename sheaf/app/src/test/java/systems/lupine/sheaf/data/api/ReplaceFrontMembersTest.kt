package systems.lupine.sheaf.data.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.FrontReplace
import systems.lupine.sheaf.data.model.FrontUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Sheaf is multi-instance: a phone can be pointed at an instance running any
 * older server. Add-to-front and remove-from-front are core actions, so they
 * must keep working when the single-front replace endpoint isn't there yet.
 */
class ReplaceFrontMembersTest {

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())),
    )

    private val front = FrontRead(
        id = "f1",
        systemId = "s1",
        memberIds = listOf("a"),
        startedAt = "2026-08-01T00:00:00Z",
        endedAt = null,
    )

    @Test fun `uses the replace endpoint when the server has it`() = runTest {
        val api = mockk<SheafApiService>()
        coEvery { api.replaceFront(any(), any()) } returns front

        api.replaceFrontMembers("f1", listOf("a", "b"))

        coVerify(exactly = 1) {
            api.replaceFront("f1", FrontReplace(memberIds = listOf("a", "b")))
        }
        coVerify(exactly = 0) { api.updateFront(any(), any()) }
    }

    @Test fun `falls back to the in-place patch on a server without the endpoint`() = runTest {
        // 404 is what a server predating the route returns for the subpath.
        val api = mockk<SheafApiService>()
        coEvery { api.replaceFront(any(), any()) } throws httpError(404)
        coEvery { api.updateFront(any(), any()) } returns front

        val result = api.replaceFrontMembers("f1", listOf("a", "b"))

        assertEquals(front, result)
        coVerify(exactly = 1) {
            api.updateFront("f1", FrontUpdate(memberIds = listOf("a", "b")))
        }
    }

    @Test fun `falls back on 405 too`() = runTest {
        val api = mockk<SheafApiService>()
        coEvery { api.replaceFront(any(), any()) } throws httpError(405)
        coEvery { api.updateFront(any(), any()) } returns front

        api.replaceFrontMembers("f1", listOf("a"))

        coVerify(exactly = 1) { api.updateFront(any(), any()) }
    }

    @Test fun `does not swallow other server errors`() = runTest {
        // A 409 means the change itself was rejected (duplicate member set).
        // Retrying it as a PATCH would either fail again or, worse, quietly
        // apply something the server just refused.
        val api = mockk<SheafApiService>()
        coEvery { api.replaceFront(any(), any()) } throws httpError(409)

        assertFailsWith<HttpException> { api.replaceFrontMembers("f1", listOf("a")) }
        coVerify(exactly = 0) { api.updateFront(any(), any()) }
    }

    @Test fun `does not swallow auth failures`() = runTest {
        val api = mockk<SheafApiService>()
        coEvery { api.replaceFront(any(), any()) } throws httpError(401)

        assertFailsWith<HttpException> { api.replaceFrontMembers("f1", listOf("a")) }
        coVerify(exactly = 0) { api.updateFront(any(), any()) }
    }

    @Test fun `passes the boundary timestamp through to the replace call`() = runTest {
        // The offline queue replays a removal with the time it was made, so the
        // history boundary lands where the user actually made the change.
        val api = mockk<SheafApiService>()
        coEvery { api.replaceFront(any(), any()) } returns front

        api.replaceFrontMembers("f1", listOf("a"), startedAt = "2026-08-01T12:00:00Z")

        coVerify {
            api.replaceFront(
                "f1",
                FrontReplace(memberIds = listOf("a"), startedAt = "2026-08-01T12:00:00Z"),
            )
        }
    }
}
