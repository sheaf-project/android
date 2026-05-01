package systems.lupine.sheaf.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockState {
    // Initial state while we wait for the first emission of the app-lock pref
    // from DataStore. Renders as a blank background to avoid flashing either
    // the lock screen or app content before we know which to show.
    data object Loading : LockState
    data object Locked : LockState
    data object Unlocked : LockState
}

/**
 * Holds the runtime app-lock state. The lock pref controls whether the lock is
 * armed; the in-memory `hasAuthenticated` flag flips false on launch and every
 * time the app goes to background, forcing a re-auth.
 */
@Singleton
class AppLockManager @Inject constructor(
    prefs: PreferencesRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hasAuthenticated = MutableStateFlow(false)

    val lockState: StateFlow<LockState> =
        combine(prefs.appLockEnabled, hasAuthenticated) { enabled, authed ->
            when {
                !enabled -> LockState.Unlocked
                authed -> LockState.Unlocked
                else -> LockState.Locked
            }
        }.stateIn(scope, SharingStarted.Eagerly, LockState.Loading)

    /** Call once from Application.onCreate to start observing process lifecycle. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun unlock() {
        hasAuthenticated.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        // Process moved to background — require re-auth on return.
        hasAuthenticated.value = false
    }
}
