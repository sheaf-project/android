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
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.NotificationChannelRead
import systems.lupine.sheaf.data.model.ReminderRead
import systems.lupine.sheaf.data.model.ReminderWrite
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

/**
 * Holds editor state for a create-or-edit form. Treats both flows the
 * same: if [reminderId] is null we POST on submit, otherwise PATCH.
 */
data class ReminderEditorState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** Filled after successful save; navigates back. */
    val saved: Boolean = false,

    // Pickers data
    val channels: List<NotificationChannelRead> = emptyList(),
    val members: List<MemberRead> = emptyList(),

    // Form fields
    val name: String = "",
    val title: String = "",
    val body: String = "",
    val enabled: Boolean = true,
    val channelId: String? = null,

    val triggerType: String = "automated",  // "automated" | "repeated"

    // Automated
    val triggerMemberId: String? = null,
    val triggerEvent: String = "start",  // "start" | "stop" | "any"
    val delaySeconds: Int = 600,

    // Repeated
    val scheduleKind: String = "daily",  // "daily" | "weekly" | "monthly"
    val scheduleTime: String = "09:00",
    val scheduleDowMask: Int = 0b1111111,  // every day by default
    val scheduleDom: Int = 1,
    val scheduleTz: String = java.util.TimeZone.getDefault().id,
)

@HiltViewModel
class ReminderEditorViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ReminderEditorState(isLoading = true))
    val state: StateFlow<ReminderEditorState> = _state.asStateFlow()

    private var reminderId: String? = null

    /** Call once with the optional id of an existing reminder to edit. */
    fun load(existing: String?) {
        reminderId = existing
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val channels = api.listOwnedChannels()
                val members = api.listMembers()
                val reminder = existing?.let { api.getReminder(it) }
                Triple(channels, members, reminder)
            }
                .onSuccess { (channels, members, reminder) ->
                    _state.update { base ->
                        val seeded = if (reminder != null) base.fromReminder(reminder) else base
                        seeded.copy(
                            isLoading = false,
                            channels = channels,
                            members = members,
                            // Pre-pick first active/pending channel as default for create.
                            channelId = seeded.channelId ?: channels.firstOrNull()?.id,
                            triggerMemberId = seeded.triggerMemberId ?: members.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load reminder"))
                    }
                }
        }
    }

    fun update(transform: ReminderEditorState.() -> ReminderEditorState) {
        _state.update(transform)
    }

    fun submit() {
        val s = _state.value
        if (!s.isValid()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val body = s.toPayload()
            runCatching {
                val id = reminderId
                if (id == null) api.createReminder(body)
                else api.updateReminder(id, body)
            }
                .onSuccess { _state.update { it.copy(isSubmitting = false, saved = true) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = e.toUserMessage("Couldn't save reminder"),
                        )
                    }
                }
        }
    }
}

private fun ReminderEditorState.fromReminder(r: ReminderRead): ReminderEditorState = copy(
    name = r.name,
    title = r.title,
    body = r.body.orEmpty(),
    enabled = r.enabled,
    channelId = r.channelId,
    triggerType = r.triggerType,
    triggerMemberId = r.triggerMemberId,
    triggerEvent = r.triggerEvent ?: "start",
    delaySeconds = r.delaySeconds ?: 600,
    scheduleKind = r.scheduleKind ?: "daily",
    scheduleTime = r.scheduleTime ?: "09:00",
    scheduleDowMask = r.scheduleDowMask ?: 0b1111111,
    scheduleDom = r.scheduleDom ?: 1,
    scheduleTz = r.scheduleTz ?: java.util.TimeZone.getDefault().id,
)

private fun ReminderEditorState.isValid(): Boolean {
    if (name.isBlank() || title.isBlank() || channelId.isNullOrBlank()) return false
    return when (triggerType) {
        "automated" -> !triggerMemberId.isNullOrBlank() && triggerEvent.isNotBlank()
        "repeated" -> scheduleKind.isNotBlank() && scheduleTime.isNotBlank()
        else -> false
    }
}

private fun ReminderEditorState.toPayload(): ReminderWrite {
    val common = ReminderWrite(
        name = name.trim(),
        title = title.trim(),
        body = body.trim().takeIf { it.isNotEmpty() },
        enabled = enabled,
        channelId = channelId,
        triggerType = triggerType,
    )
    return when (triggerType) {
        "automated" -> common.copy(
            triggerMemberId = triggerMemberId,
            triggerEvent = triggerEvent,
            delaySeconds = delaySeconds,
            // Clear scheduled fields explicitly
            scheduleKind = null,
            scheduleTime = null,
            scheduleDowMask = null,
            scheduleDom = null,
            scheduleTz = null,
            cronExpression = null,
        )
        "repeated" -> common.copy(
            scheduleKind = scheduleKind,
            scheduleTime = scheduleTime,
            scheduleDowMask = if (scheduleKind == "weekly") scheduleDowMask else null,
            scheduleDom = if (scheduleKind == "monthly") scheduleDom else null,
            scheduleTz = scheduleTz,
            // Clear automated fields
            triggerMemberId = null,
            triggerEvent = null,
            delaySeconds = null,
        )
        else -> common
    }
}
