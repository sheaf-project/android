package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.model.ServerVersion
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.repository.ServerInfoRepository
import javax.inject.Inject

/**
 * What About needs beyond `BuildConfig`: which instance this is and what it is
 * running.
 *
 * The version is whatever the shared repository already holds, so opening
 * About does not re-ask on every visit; [refresh] exists for the case where it
 * could not be reached the first time.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val serverInfo: ServerInfoRepository,
    prefs: PreferencesRepository,
) : ViewModel() {

    val serverVersion: StateFlow<ServerVersion?> = serverInfo.version

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { serverInfo.ensureLoaded() }
    }
}
