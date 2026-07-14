package systems.lupine.sheaf.ui.relationships

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import systems.lupine.sheaf.MainDispatcherRule
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.RelationshipFromViewpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The load latch. It exists so the editor doesn't refetch on every
 * recomposition, but latching a *failed* load meant one offline moment left a
 * member's relationships permanently unloadable until you left the screen.
 */
class RelationshipsEditorViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = mockk<SheafApiService>(relaxed = true)

    private fun edge(id: String) = RelationshipFromViewpoint(
        id = id,
        relationshipTypeId = "t1",
        typeName = "Partner",
        otherId = "m2",
        label = "partner",
        direction = "none",
        mutual = false,
        visibility = "private",
    )

    @Test fun `a successful load is latched and not repeated`() = runTest {
        coEvery { api.getMemberRelationships("m1") } returns listOf(edge("e1"))
        val vm = RelationshipsEditorViewModel(api)

        vm.load(REL_SCOPE_MEMBER, "m1")
        advanceUntilIdle()
        vm.load(REL_SCOPE_MEMBER, "m1")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getMemberRelationships("m1") }
        assertEquals(1, vm.state.value.relationships.size)
    }

    @Test fun `a failed load can be retried`() = runTest {
        coEvery { api.getMemberRelationships("m1") } throws IllegalStateException("offline")
        val vm = RelationshipsEditorViewModel(api)

        vm.load(REL_SCOPE_MEMBER, "m1")
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        // The retry has to actually reach the server: latching the failure meant
        // this call was swallowed and the user was stuck on the error forever.
        coEvery { api.getMemberRelationships("m1") } returns listOf(edge("e1"))
        vm.retry()
        advanceUntilIdle()

        coVerify(exactly = 2) { api.getMemberRelationships("m1") }
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.relationships.size)
    }

    @Test fun `switching to another node loads that node`() = runTest {
        coEvery { api.getMemberRelationships(any()) } returns emptyList()
        val vm = RelationshipsEditorViewModel(api)

        vm.load(REL_SCOPE_MEMBER, "m1")
        advanceUntilIdle()
        vm.load(REL_SCOPE_MEMBER, "m2")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getMemberRelationships("m1") }
        coVerify(exactly = 1) { api.getMemberRelationships("m2") }
    }

    @Test fun `the group scope hits the group endpoints`() = runTest {
        coEvery { api.getGroupRelationships("g1") } returns emptyList()
        val vm = RelationshipsEditorViewModel(api)

        vm.load(REL_SCOPE_GROUP, "g1")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getGroupRelationships("g1") }
        coVerify(exactly = 0) { api.getMemberRelationships(any()) }
    }

    @Test fun `a reload failure after a successful save is surfaced, not swallowed`() = runTest {
        coEvery { api.getMemberRelationships("m1") } returns emptyList()
        val vm = RelationshipsEditorViewModel(api)
        vm.load(REL_SCOPE_MEMBER, "m1")
        advanceUntilIdle()

        coEvery { api.deleteMemberRelationship("e1") } returns Unit
        coEvery { api.getMemberRelationships("m1") } throws IllegalStateException("offline")
        vm.remove("e1")
        advanceUntilIdle()

        // The delete worked; the list on screen is now stale. Say so rather than
        // showing the old list as if nothing happened.
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.isNotBlank())
    }
}
