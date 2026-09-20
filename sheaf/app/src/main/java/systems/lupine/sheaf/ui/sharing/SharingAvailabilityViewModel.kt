package systems.lupine.sheaf.ui.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import javax.inject.Inject

/**
 * Whether the Sharing entry belongs in the drawer at all.
 *
 * Two conditions, matching web's sidebar: the instance serves a public surface,
 * or it does not but this account still has grants on file. The second half is
 * the load-bearing one. Grants outlive the setting - turning the instance
 * switch off suppresses them, it does not revoke them - and Sharing is the only
 * place to revoke one. Hiding the entry there would leave somebody with a
 * published profile, no way to reach the button that unpublishes it, and no say
 * in whether it comes back when an operator flips the switch again.
 *
 * The grant probe only runs when the switch is off, so an instance that never
 * had sharing costs one field on a call the app already makes and nothing else.
 */
@HiltViewModel
class SharingAvailabilityViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * Re-check after a sign-in or sign-out. Signed out, the entry goes away
     * rather than lingering from the previous account.
     */
    fun refresh(loggedIn: Boolean) {
        if (!loggedIn) {
            _visible.value = false
            return
        }
        viewModelScope.launch {
            val enabled = runCatching { api.getMe() }.getOrNull()?.publicProfilesEnabled == true
            _visible.value = enabled || hasDormantGrants()
        }
    }

    /**
     * Any grant still on file while the surface is off. A failure - including
     * the 404 an older server answers with - reads as "no", so the entry does
     * not appear for a server that has no sharing to manage.
     */
    private suspend fun hasDormantGrants(): Boolean =
        runCatching { api.listShareGrants() }
            .getOrDefault(emptyList())
            // Revoked is the one status that means "nothing to manage". A
            // grant that is pending, active or expired is still on file and
            // still revocable, which is exactly why the entry has to appear.
            .any { it.status != "revoked" && it.revokedAt == null }
}
