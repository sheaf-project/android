package systems.lupine.sheaf.ui.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory parking spot for an activation code arriving via deep link
 * before the user is logged in (or while the nav graph is mid-flight).
 * SheafApp observes this and routes to the redemption screen as soon
 * as both isLoggedIn and a code are available.
 *
 * Not persisted: if the app is killed mid-flow the user can re-click
 * the magic link. DataStore would be overkill for that.
 */
@Singleton
class PendingRedemptionHolder @Inject constructor() {
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    fun set(activationCode: String) { _code.value = activationCode }
    fun clear() { _code.value = null }
}
