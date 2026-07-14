package systems.lupine.sheaf.data.repository

import android.util.Log
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.db.PendingOperationsDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes all account-scoped local state that is NOT namespaced by account:
 * the response cache (roster, fronts, groups, history, journals) and the
 * offline mutation queue (pending front switches / removals).
 *
 * Called when a session is established (sign-in) and when it is torn down
 * (sign-out), so one account can never see another's cached data, and queued
 * mutations from a prior session can never replay against new credentials.
 * Tokens/prefs are cleared separately by [PreferencesRepository.clearTokens].
 */
@Singleton
class AccountDataWiper @Inject constructor(
    private val cache: LocalCache,
    private val pendingOps: PendingOperationsDao,
) {
    /**
     * Best-effort, but each step is attempted independently. A single
     * runCatching around all three meant a throw from [LocalCache.clearAll]
     * skipped both queue deletes, leaving the previous account's queued front
     * switches to replay against the new account's credentials: exactly the
     * thing this class exists to prevent, silently swallowed.
     */
    suspend fun wipe() {
        step("cache") { cache.clearAll() }
        step("pending switches") { pendingOps.deleteAllSwitches() }
        step("pending removals") { pendingOps.deleteAllRemovals() }
    }

    private suspend fun step(what: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onFailure { Log.w("AccountDataWiper", "failed to wipe $what", it) }
    }
}
