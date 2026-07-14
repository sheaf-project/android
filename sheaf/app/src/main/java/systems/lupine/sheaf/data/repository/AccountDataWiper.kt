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
    suspend fun wipe() {
        runCatching {
            cache.clearAll()
            pendingOps.deleteAllSwitches()
            pendingOps.deleteAllRemovals()
        }.onFailure { Log.w("AccountDataWiper", "failed to wipe account data", it) }
    }
}
