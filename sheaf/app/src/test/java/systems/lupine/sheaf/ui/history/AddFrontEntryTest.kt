package systems.lupine.sheaf.ui.history

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Response
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import systems.lupine.sheaf.MainDispatcherRule
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.data.repository.PreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Writing down a front that already happened.
 *
 * This was create-then-patch: open the entry, then close it. Between the two
 * calls the new entry IS the current front and replace_fronts_default is on,
 * so the create ended whoever was really fronting and back-dated their end to
 * the historical start. Where that start preceded the live front's own start
 * the database constraint rejected it and the request 500d; where it did not,
 * the live front was silently ended in the past. Reproduced against a running
 * server, not reasoned about.
 */
class AddFrontEntryTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val api = mockk<SheafApiService>(relaxed = true)
    private val cache = mockk<LocalCache>(relaxed = true)
    private val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
    private val prefs = mockk<PreferencesRepository>(relaxed = true)

    private fun front(id: String, startedAt: String, endedAt: String?) = FrontRead(
        id = id,
        systemId = "s1",
        memberIds = listOf("m1"),
        startedAt = startedAt,
        endedAt = endedAt,
        customStatus = null,
    )

    private fun viewModel(): HistoryViewModel {
        every()
        return HistoryViewModel(api, cache, networkMonitor, prefs)
    }

    private fun every() {
        coEvery { prefs.historyView } returns flowOf("infinite")
        coEvery { prefs.historyPageSize } returns flowOf(50)
        coEvery { networkMonitor.isOnline } returns MutableStateFlow(true)
        coEvery { api.listFrontsPaginated(any(), any(), any(), any()) } returns
            Response.success(emptyList())
        coEvery { api.listMembers() } returns emptyList()
    }

    @Test fun `a closed entry is one call that replaces nothing`() = runTest {
        val body = slot<FrontCreate>()
        coEvery { api.createFront(capture(body)) } answers {
            front("f1", body.captured.startedAt!!, body.captured.endedAt)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.addFrontEntry(
            memberIds = listOf("m1"),
            startedAt = "2026-01-02T10:00:00Z",
            endedAt = "2026-01-02T12:30:00Z",
            customStatus = null,
        )
        advanceUntilIdle()

        assertEquals("2026-01-02T12:30:00Z", body.captured.endedAt)
        // The whole bug in one assertion: a historical entry must never take
        // the live roster with it.
        assertEquals(false, body.captured.replaceFronts)
        // Nothing to close afterwards, so no second call.
        coVerify(exactly = 0) { api.updateFront(any(), any()) }
    }

    @Test fun `an ongoing entry is still a switch`() = runTest {
        val body = slot<FrontCreate>()
        coEvery { api.createFront(capture(body)) } answers {
            front("f2", body.captured.startedAt!!, null)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.addFrontEntry(
            memberIds = listOf("m1"),
            startedAt = "2026-01-02T10:00:00Z",
            endedAt = null,
            customStatus = null,
        )
        advanceUntilIdle()

        assertNull(body.captured.endedAt)
        // Null, not false: starting a front is exactly what replace_fronts is
        // for, and the system's own default decides.
        assertNull(body.captured.replaceFronts)
    }

    @Test fun `a server that drops ended_at gets the entry closed afterwards`() = runTest {
        // Detection rather than a version check. An older server ignores the
        // field and hands back an open front; closing it then is safe because
        // replace_fronts=false meant nothing was ended on the way in.
        val body = slot<FrontCreate>()
        coEvery { api.createFront(capture(body)) } answers {
            front("f3", body.captured.startedAt!!, endedAt = null)
        }
        val vm = viewModel()
        advanceUntilIdle()

        vm.addFrontEntry(
            memberIds = listOf("m1"),
            startedAt = "2026-01-02T10:00:00Z",
            endedAt = "2026-01-02T12:30:00Z",
            customStatus = null,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            api.updateFront("f3", match { it.endedAt == "2026-01-02T12:30:00Z" })
        }
    }
}
