package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ClientSettingsBody
import systems.lupine.sheaf.data.model.ServerVersion
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.repository.ServerInfoRepository
import systems.lupine.sheaf.util.TechnicalErrors
import javax.inject.Inject

// Same blob SettingsViewModel and HomeViewModel write to; the client id is the
// platform, so every Android device on the account shares one set.
private const val CLIENT_ID = "android"

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
    private val prefs: PreferencesRepository,
    private val api: SheafApiService,
) : ViewModel() {

    val serverVersion: StateFlow<ServerVersion?> = serverInfo.version

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val showTechnicalErrors: StateFlow<Boolean> = prefs.showTechnicalErrors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { serverInfo.ensureLoaded() }
    }

    /**
     * Apply the choice here, then tell the account.
     *
     * Local first and unconditionally: this is the one preference whose whole
     * job is to explain a failing request, so it cannot depend on a request
     * succeeding. The PATCH is best-effort and carries only its own nested
     * group, so a failure leaves the device's setting in force and nothing
     * else in the blob disturbed.
     */
    fun setShowTechnicalErrors(enabled: Boolean) {
        viewModelScope.launch {
            prefs.saveShowTechnicalErrors(enabled)
            runCatching {
                api.patchClientSettings(
                    CLIENT_ID,
                    ClientSettingsBody(TechnicalErrors.toClientSettings(enabled)),
                )
            }
        }
    }
}
