package systems.lupine.sheaf.ui.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A magic-link activation code captured from a deep link, plus the
 * instance the link was minted for (when the link carried one). The
 * instance lets the redemption flow detect a link aimed at a different
 * Sheaf server than this device is signed into, rather than redeeming
 * against the wrong one and getting an opaque 404.
 */
data class PendingRedemption(
    val code: String,
    /** Instance base URL from the link's `instance=` param, or null. */
    val instanceUrl: String?,
)

/**
 * In-memory parking spot for a [PendingRedemption] arriving via deep link
 * before the user is logged in (or while the nav graph is mid-flight).
 * SheafApp observes this and routes to the redemption screen as soon
 * as both isLoggedIn and a code are available.
 *
 * Not persisted: if the app is killed mid-flow the user can re-click
 * the magic link. DataStore would be overkill for that.
 */
@Singleton
class PendingRedemptionHolder @Inject constructor() {
    private val _pending = MutableStateFlow<PendingRedemption?>(null)
    val pending: StateFlow<PendingRedemption?> = _pending.asStateFlow()

    fun set(activationCode: String, instanceUrl: String?) {
        _pending.value = PendingRedemption(activationCode, instanceUrl)
    }

    fun clear() { _pending.value = null }
}
