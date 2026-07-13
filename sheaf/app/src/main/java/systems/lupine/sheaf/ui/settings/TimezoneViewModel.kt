package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SystemTimezoneBody
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.ui.components.resolveDisplayZoneId
import systems.lupine.sheaf.util.toUserMessage
import java.time.ZoneId
import javax.inject.Inject

data class TimezoneUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TimezoneViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val moshi: Moshi,
) : ViewModel() {

    private val _state = MutableStateFlow(TimezoneUiState())
    val state: StateFlow<TimezoneUiState> = _state.asStateFlow()

    /** Synced account default. null = automatic. */
    val accountTimezone: StateFlow<String?> = prefs.accountTimezone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** This device's override: null = follow account, "auto" = device clock, else a zone. */
    val deviceOverride: StateFlow<String?> = prefs.timezoneOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The zone timestamps actually render in, for a "showing times in X" hint. */
    val effectiveZone: StateFlow<ZoneId> =
        combine(prefs.accountTimezone, prefs.timezoneOverride) { account, override ->
            resolveDisplayZoneId(account, override)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneId.systemDefault())

    /**
     * Set the account-wide timezone (null = automatic). Sends the value through
     * a serialize-nulls body so "automatic" reaches the server as an explicit
     * null (the shared adapter would drop it). Updates the local cache on
     * success so the app-wide display zone reflects it immediately.
     */
    fun setAccountTimezone(tz: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val json = moshi.adapter(SystemTimezoneBody::class.java)
                    .serializeNulls()
                    .toJson(SystemTimezoneBody(tz))
                api.updateOwnSystemTimezone(json.toRequestBody("application/json".toMediaType()))
            }
                .onSuccess { system ->
                    prefs.saveAccountTimezone(system.timezone)
                    _state.update { it.copy(isSaving = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    /** Set (or clear, with null) this device's local override. */
    fun setDeviceOverride(value: String?) {
        viewModelScope.launch { prefs.saveTimezoneOverride(value) }
    }
}
