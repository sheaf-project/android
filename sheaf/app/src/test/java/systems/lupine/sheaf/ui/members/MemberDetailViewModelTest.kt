package systems.lupine.sheaf.ui.members

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import systems.lupine.sheaf.MainDispatcherRule
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MemberCreate
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.MarkdownImageDelegate
import kotlin.test.Test

/**
 * The duplicate-member guard. Creating a member is two server round trips (the
 * member, then its custom-field values); if the second fails, the user is
 * looking at an error on a screen that has already created someone. Pressing
 * Save again must update that member, not mint a second one.
 */
class MemberDetailViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = mockk<SheafApiService>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val markdownImages = mockk<MarkdownImageDelegate>(relaxed = true)

    private fun newMemberViewModel() = MemberDetailViewModel(
        api = api,
        moshi = Moshi.Builder().build(),
        context = context,
        markdownImages = markdownImages,
        savedStateHandle = SavedStateHandle(mapOf("memberId" to "new")),
    )

    private fun member(id: String) = MemberRead(
        id = id,
        systemId = "s1",
        name = "Alex",
        displayName = null,
        description = null,
        pronouns = null,
        avatarUrl = null,
        color = null,
        birthday = null,
        privacy = "private",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    @Test fun `retrying a save after the first attempt failed does not create a second member`() = runTest {
        val vm = newMemberViewModel()
        advanceUntilIdle()
        vm.updateForm { copy(name = "Alex") }
        // Staged so the save has a second step to fail on.
        vm.setCustomFieldValue("f1", "value")

        // First save: the member is created, then the custom-field flush blows up.
        coEvery { api.createMember(any()) } returns member("m-created")
        coEvery { api.setMemberFieldValues(any(), any()) } throws IllegalStateException("boom")
        vm.save()
        advanceUntilIdle()

        // Second save: must PATCH the member the first attempt already created.
        coEvery { api.setMemberFieldValues(any(), any()) } returns emptyList()
        coEvery { api.patchMemberRaw(any(), any()) } returns member("m-created")
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.createMember(any<MemberCreate>()) }
        coVerify(exactly = 1) { api.patchMemberRaw("m-created", any()) }
        // And the field values land against that same member, not a new one.
        coVerify { api.setMemberFieldValues("m-created", any()) }
    }

    @Test fun `a clean save creates exactly one member`() = runTest {
        val vm = newMemberViewModel()
        advanceUntilIdle()
        vm.updateForm { copy(name = "Alex") }

        coEvery { api.createMember(any()) } returns member("m-created")
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.createMember(any<MemberCreate>()) }
        coVerify(exactly = 0) { api.patchMemberRaw(any(), any()) }
    }

    @Test fun `a blank name saves nothing`() = runTest {
        val vm = newMemberViewModel()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { api.createMember(any<MemberCreate>()) }
    }
}
