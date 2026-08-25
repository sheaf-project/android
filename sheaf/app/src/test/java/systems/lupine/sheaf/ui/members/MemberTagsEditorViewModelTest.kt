package systems.lupine.sheaf.ui.members

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import systems.lupine.sheaf.MainDispatcherRule
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MemberTagUpdate
import systems.lupine.sheaf.data.model.TagRead
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The tag endpoint takes a member's whole tag set rather than a delta, so a
 * toggle that sends the wrong thing silently strips every other tag off the
 * member. That is the failure this covers.
 */
class MemberTagsEditorViewModelTest {

    @get:Rule val dispatcher = MainDispatcherRule()

    private fun tag(id: String, name: String = id) = TagRead(
        id = id,
        systemId = "s1",
        name = name,
        color = null,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private val all = listOf(tag("a"), tag("b"), tag("c"))

    private fun api(mine: List<TagRead>): SheafApiService = mockk {
        coEvery { listTags() } returns all
        coEvery { getMemberTags("m1") } returns mine
    }

    @Test fun `loads the system's tags and the member's current set`() = runTest {
        val vm = MemberTagsEditorViewModel(api(listOf(tag("b"))))
        vm.load("m1")
        advanceUntilIdle()

        assertEquals(all, vm.state.value.allTags)
        assertEquals(setOf("b"), vm.state.value.selected)
    }

    @Test fun `adding a tag sends the whole resulting set, not just the new one`() = runTest {
        val service = api(listOf(tag("b")))
        val body = slot<MemberTagUpdate>()
        coEvery { service.setMemberTags("m1", capture(body)) } answers {
            body.captured.tagIds.map { tag(it) }
        }
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()

        vm.toggle("a")

        advanceUntilIdle()

        // Sending only "a" here would drop "b" from the member entirely.
        assertEquals(setOf("a", "b"), body.captured.tagIds.toSet())
        assertEquals(setOf("a", "b"), vm.state.value.selected)
    }

    @Test fun `removing a tag sends the remainder`() = runTest {
        val service = api(listOf(tag("a"), tag("b")))
        val body = slot<MemberTagUpdate>()
        coEvery { service.setMemberTags("m1", capture(body)) } answers {
            body.captured.tagIds.map { tag(it) }
        }
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()

        vm.toggle("a")

        advanceUntilIdle()

        assertEquals(listOf("b"), body.captured.tagIds)
        assertEquals(setOf("b"), vm.state.value.selected)
    }

    @Test fun `removing the last tag sends an empty set rather than skipping the call`() = runTest {
        val service = api(listOf(tag("a")))
        val body = slot<MemberTagUpdate>()
        coEvery { service.setMemberTags("m1", capture(body)) } returns emptyList()
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()

        vm.toggle("a")

        advanceUntilIdle()

        assertEquals(emptyList(), body.captured.tagIds)
        assertEquals(emptySet(), vm.state.value.selected)
    }

    @Test fun `the selection reconciles with what the server reports back`() = runTest {
        // The server is the authority on the resulting set; if it disagrees
        // with our optimistic guess, its answer wins.
        val service = api(listOf(tag("a")))
        coEvery { service.setMemberTags("m1", any()) } returns listOf(tag("a"), tag("c"))
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()

        vm.toggle("b")

        advanceUntilIdle()

        assertEquals(setOf("a", "c"), vm.state.value.selected)
    }

    @Test fun `a failed toggle rolls back and surfaces the error`() = runTest {
        val service = api(listOf(tag("a")))
        coEvery { service.setMemberTags("m1", any()) } throws HttpException(
            Response.error<Any>(403, "".toResponseBody("application/json".toMediaType())),
        )
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()

        vm.toggle("b")

        advanceUntilIdle()

        // Leaving the chip filled after a refused write would tell the user
        // the tag stuck when it did not.
        assertEquals(setOf("a"), vm.state.value.selected)
        assertNotNull(vm.state.value.error)
        assertEquals(false, vm.state.value.isSaving)
    }

    @Test fun `a missing tag vocabulary still shows what the member has`() = runTest {
        // listTags is best-effort: a viewer without it should still see the
        // member's own tags rather than an error.
        val service: SheafApiService = mockk {
            coEvery { listTags() } throws HttpException(
                Response.error<Any>(403, "".toResponseBody("application/json".toMediaType())),
            )
            coEvery { getMemberTags("m1") } returns listOf(tag("a"))
        }
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()

        assertEquals(emptyList(), vm.state.value.allTags)
        assertEquals(setOf("a"), vm.state.value.selected)
        assertNull(vm.state.value.error)
    }

    @Test fun `reloading the same member mid-edit does not stomp the selection`() = runTest {
        // The composable's LaunchedEffect can re-fire; a reload that reset the
        // selection would undo a toggle the user just made.
        val service = api(listOf(tag("a")))
        coEvery { service.setMemberTags("m1", any()) } returns listOf(tag("a"), tag("b"))
        val vm = MemberTagsEditorViewModel(service)
        vm.load("m1")
        advanceUntilIdle()
        vm.toggle("b")
        advanceUntilIdle()

        vm.load("m1")

        advanceUntilIdle()

        assertEquals(setOf("a", "b"), vm.state.value.selected)
        coVerify(exactly = 1) { service.getMemberTags("m1") }
    }
}
