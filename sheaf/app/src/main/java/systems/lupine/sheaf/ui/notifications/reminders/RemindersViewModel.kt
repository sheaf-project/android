package systems.lupine.sheaf.ui.notifications.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ReminderRead
import systems.lupine.sheaf.data.model.ReminderWrite
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class RemindersUiState(
    val isLoading: Boolean = false,
    val reminders: List<ReminderRead> = emptyList(),
    /** Map channel id -> name for inline display in the list. */
    val channelNames: Map<String, String> = emptyMap(),
    /** Map member id -> name, for the trigger-member subtitle of automated reminders. */
    val memberNames: Map<String, String> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(RemindersUiState(isLoading = true))
    val state: StateFlow<RemindersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val reminders = api.listReminders()
                val channels = runCatching { api.listOwnedChannels() }.getOrDefault(emptyList())
                val members = runCatching { api.listMembers() }.getOrDefault(emptyList())
                Triple(reminders, channels, members)
            }
                .onSuccess { (reminders, channels, members) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            reminders = reminders,
                            channelNames = channels.associate { c -> c.id to c.name },
                            memberNames = members.associate { m -> m.id to m.name },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load reminders"))
                    }
                }
        }
    }

    fun toggleEnabled(reminder: ReminderRead) {
        viewModelScope.launch {
            runCatching {
                api.updateReminder(reminder.id, ReminderWrite(enabled = !reminder.enabled))
            }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't update reminder")) }
                }
        }
    }

    fun delete(reminderId: String) {
        viewModelScope.launch {
            runCatching { api.deleteReminder(reminderId) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete reminder")) }
                }
        }
    }
}
