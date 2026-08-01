package systems.lupine.sheaf.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject

/**
 * The user's bottom-bar pins. Read by the app shell to build the bar, and by
 * the edit screen to change it.
 *
 * The flow emits resolved destinations rather than raw routes so every consumer
 * gets the same fallback behaviour (see [resolvePins]); the initial value is the
 * defaults, so the bar renders its final shape on the first frame rather than
 * flickering from empty once DataStore reports back.
 */
@HiltViewModel
class NavPinsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
) : ViewModel() {

    val pins: StateFlow<List<DrawerDest>> = prefs.navPins
        .map { resolvePins(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = resolvePins(null),
        )

    fun setPins(routes: List<String>) {
        viewModelScope.launch { prefs.saveNavPins(routes) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { prefs.clearNavPins() }
    }
}
