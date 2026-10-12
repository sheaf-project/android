package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.SystemUpdate
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

/**
 * The two account-wide front preferences, as the server holds them.
 *
 * Null means not loaded yet, which the rows render as disabled rather than as
 * off: a switch that shows a confident "off" before the answer arrives is a
 * switch somebody turns on twice.
 */
data class FrontPreferences(
    val replaceFrontsDefault: Boolean? = null,
    val coalesceContiguousFronts: Boolean? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FrontingSettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val cache: LocalCache,
    private val api: SheafApiService,
) : ViewModel() {

    /** null = follow the account default. */
    val quickSwitchReplace: StateFlow<Boolean?> = prefs.quickSwitchReplace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _account = MutableStateFlow(FrontPreferences())
    val account: StateFlow<FrontPreferences> = _account.asStateFlow()

    /**
     * What "follow the account default" currently means, for the row that
     * offers it. Reads from the same state the account section edits, so
     * changing the default updates that row's wording immediately instead of
     * leaving it describing the old value.
     */
    val accountDefault: StateFlow<Boolean?> = _account
        .map { it.replaceFrontsDefault }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    private fun load() {
        viewModelScope.launch {
            // Cache first so the switches are right on a cold open, then the
            // server, which is authoritative and may have been changed from
            // another client.
            cache.getSystem()?.let { cached ->
                _account.update {
                    it.copy(
                        replaceFrontsDefault = cached.replaceFrontsDefault,
                        coalesceContiguousFronts = cached.coalesceContiguousFronts,
                    )
                }
            }
            runCatching { api.getOwnSystem() }
                .onSuccess { system ->
                    runCatching { cache.saveSystem(system) }
                    _account.update {
                        it.copy(
                            replaceFrontsDefault = system.replaceFrontsDefault,
                            coalesceContiguousFronts = system.coalesceContiguousFronts,
                        )
                    }
                }
        }
    }

    fun setReplaceFrontsDefault(value: Boolean) {
        save(SystemUpdate(replaceFrontsDefault = value)) { it.copy(replaceFrontsDefault = value) }
    }

    fun setCoalesceContiguousFronts(value: Boolean) {
        save(SystemUpdate(coalesceContiguousFronts = value)) {
            it.copy(coalesceContiguousFronts = value)
        }
    }

    /**
     * Apply immediately, the way the web card does, and put the switch back if
     * the server refuses. An optimistic flip with no rollback would leave the
     * screen claiming a setting that is not in force.
     */
    private fun save(update: SystemUpdate, optimistic: (FrontPreferences) -> FrontPreferences) {
        val before = _account.value
        _account.update { optimistic(it).copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching { api.updateOwnSystem(update) }
                .onSuccess { system ->
                    // Home reads the default from the cached system to seed the
                    // quick-switch checkbox, so write through or it keeps the
                    // old answer until something else refreshes it.
                    runCatching { cache.saveSystem(system) }
                    _account.update {
                        it.copy(
                            replaceFrontsDefault = system.replaceFrontsDefault,
                            coalesceContiguousFronts = system.coalesceContiguousFronts,
                            isSaving = false,
                        )
                    }
                }
                .onFailure { e ->
                    _account.value = before.copy(
                        isSaving = false,
                        error = e.toUserMessage("Couldn't save that setting"),
                    )
                }
        }
    }

    fun clearError() { _account.update { it.copy(error = null) } }

    fun setQuickSwitchReplace(value: Boolean?) {
        viewModelScope.launch { prefs.saveQuickSwitchReplace(value) }
    }
}
