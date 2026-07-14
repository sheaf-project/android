package systems.lupine.sheaf.data.repository

import android.util.Log
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.db.PendingOperationsDao
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The wiper is what stops one account seeing another's cached data, and stops
 * offline actions queued under one account replaying against another's
 * credentials. Both halves have to happen even when the other fails.
 */
class AccountDataWiperTest {

    private val cache = mockk<LocalCache>()
    private val pendingOps = mockk<PendingOperationsDao>()
    private val wiper = AccountDataWiper(cache, pendingOps)

    @BeforeTest fun stubLog() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @AfterTest fun unstubLog() = unmockkStatic(Log::class)

    @Test fun `wipes the cache and both queues`() = runTest {
        coEvery { cache.clearAll() } just Runs
        coEvery { pendingOps.deleteAllSwitches() } just Runs
        coEvery { pendingOps.deleteAllRemovals() } just Runs

        wiper.wipe()

        coVerify(exactly = 1) { cache.clearAll() }
        coVerify(exactly = 1) { pendingOps.deleteAllSwitches() }
        coVerify(exactly = 1) { pendingOps.deleteAllRemovals() }
    }

    @Test fun `a failing cache wipe still clears the offline queue`() = runTest {
        // The regression this guards: all three calls used to sit inside one
        // runCatching, so a throw here skipped both queue deletes and left the
        // previous account's queued switches to replay under the new session.
        coEvery { cache.clearAll() } throws IllegalStateException("db locked")
        coEvery { pendingOps.deleteAllSwitches() } just Runs
        coEvery { pendingOps.deleteAllRemovals() } just Runs

        wiper.wipe()

        coVerify(exactly = 1) { pendingOps.deleteAllSwitches() }
        coVerify(exactly = 1) { pendingOps.deleteAllRemovals() }
    }

    @Test fun `a failing switch wipe still clears removals`() = runTest {
        coEvery { cache.clearAll() } just Runs
        coEvery { pendingOps.deleteAllSwitches() } throws IllegalStateException("db locked")
        coEvery { pendingOps.deleteAllRemovals() } just Runs

        wiper.wipe()

        coVerify(exactly = 1) { pendingOps.deleteAllRemovals() }
    }

    @Test fun `wipe never throws at the caller`() = runTest {
        // Sign-in and sign-out both call this; a throw here must not strand the
        // user mid-auth.
        coEvery { cache.clearAll() } throws IllegalStateException("boom")
        coEvery { pendingOps.deleteAllSwitches() } throws IllegalStateException("boom")
        coEvery { pendingOps.deleteAllRemovals() } throws IllegalStateException("boom")

        wiper.wipe()
    }
}
