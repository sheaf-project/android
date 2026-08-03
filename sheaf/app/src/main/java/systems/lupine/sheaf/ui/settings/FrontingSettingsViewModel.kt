package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.repository.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class FrontingSettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val cache: LocalCache,
) : ViewModel() {

    /** null = follow the account default. */
    val quickSwitchReplace: StateFlow<Boolean?> = prefs.quickSwitchReplace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Read for display only, so the "follow the account" option can say what
    // that currently means. Comes from the cached system rather than its own
    // request; null just means we haven't cached one yet and the row falls
    // back to generic wording.
    private val _accountDefault = MutableStateFlow<Boolean?>(null)
    val accountDefault: StateFlow<Boolean?> = _accountDefault.asStateFlow()

    init {
        viewModelScope.launch {
            _accountDefault.value = cache.getSystem()?.replaceFrontsDefault
        }
    }

    fun setQuickSwitchReplace(value: Boolean?) {
        viewModelScope.launch { prefs.saveQuickSwitchReplace(value) }
    }
}
