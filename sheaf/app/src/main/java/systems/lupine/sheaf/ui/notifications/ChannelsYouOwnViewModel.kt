package systems.lupine.sheaf.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.GroupRuleSpec
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.MemberRuleSpec
import systems.lupine.sheaf.data.model.NotificationChannelCreate
import systems.lupine.sheaf.data.model.NotificationChannelCreateResponse
import systems.lupine.sheaf.data.model.NotificationChannelRead
import systems.lupine.sheaf.data.model.NotificationChannelUpdate
import systems.lupine.sheaf.data.model.QuietHoursSpec
import systems.lupine.sheaf.data.model.WatchTokenCreate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ChannelsUiState(
    val isLoading: Boolean = false,
    val channels: List<NotificationChannelRead> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ChannelsYouOwnViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelsUiState(isLoading = true))
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.listOwnedChannels() }
                .onSuccess { rows ->
                    _state.update { it.copy(isLoading = false, channels = rows) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load channels"))
                    }
                }
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            runCatching { api.deleteChannel(channelId) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete channel")) }
                }
        }
    }

    fun toggleEnabled(channel: NotificationChannelRead) {
        viewModelScope.launch {
            val call = if (channel.destinationState.equals("disabled", ignoreCase = true)) {
                suspend { api.enableChannel(channel.id) }
            } else {
                suspend { api.disableChannel(channel.id) }
            }
            runCatching { call() }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't update channel")) }
                }
        }
    }
}

/**
 * Snapshot of the user's pending edits to a channel. Mirrors the web app's
 * draft pattern: each field is either null (untouched) or a concrete value
 * the user has set. Save flattens this into a NotificationChannelUpdate
 * for the PATCH; null fields stay out of the wire JSON via Moshi's omit-
 * null default and the backend treats them as "don't touch".
 *
 * The few list-typed fields (group_rules / member_rules / quiet_hours)
 * carry "absent" via a separate boolean rather than a sentinel — list-of-
 * empty isn't the same as "don't touch".
 */
data class ChannelEditDraft(
    val name: String? = null,
    val baseAllMembers: Boolean? = null,
    val baseIncludePrivate: Boolean? = null,
    val triggerOnStart: Boolean? = null,
    val triggerOnStop: Boolean? = null,
    val triggerOnCofrontChange: Boolean? = null,
    val cofrontRedaction: String? = null,
    val payloadSensitivity: String? = null,
    val debounceSeconds: Int? = null,
    val aggregationWindowSeconds: Int? = null,
    val quietHoursSet: Boolean = false,
    val quietHours: QuietHoursSpec? = null,
    val groupRulesSet: Boolean = false,
    val groupRules: List<GroupRuleSpec> = emptyList(),
    val memberRulesSet: Boolean = false,
    val memberRules: List<MemberRuleSpec> = emptyList(),
)

data class ChannelDetailUiState(
    val isLoading: Boolean = false,
    val channel: NotificationChannelRead? = null,
    // Sidecar pickers for L2/L3 rule editing. Loaded once alongside the
    // channel and cached for the lifetime of the screen — both endpoints
    // are small and the UI re-uses them across multiple dropdowns.
    val members: List<MemberRead> = emptyList(),
    val groups: List<GroupRead> = emptyList(),
    val draft: ChannelEditDraft = ChannelEditDraft(),
    val isSaving: Boolean = false,
    val reissuedActivationUrl: String? = null,
    val reissuedExpiresAt: String? = null,
    val isReissuing: Boolean = false,
    val duplicateResponse: NotificationChannelCreateResponse? = null,
    val testResult: TestResult? = null,
    val error: String? = null,
)

data class TestResult(val ok: Boolean, val message: String)

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChannelDetailUiState(isLoading = true))
    val state: StateFlow<ChannelDetailUiState> = _state.asStateFlow()

    fun load(channelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // Channel list is small; just filter from the same endpoint
            // rather than wiring a single-channel GET. Saves one route on
            // the client side and stays consistent with whatever the
            // list view is showing. Pickers fetched in parallel so the
            // screen renders fully in a single network roundtrip-set.
            runCatching {
                coroutineScope {
                    val channels = async { api.listOwnedChannels() }
                    val members = async { runCatching { api.listMembers() }.getOrDefault(emptyList()) }
                    val groups = async { runCatching { api.listGroups() }.getOrDefault(emptyList()) }
                    Triple(channels.await(), members.await(), groups.await())
                }
            }
                .onSuccess { (rows, members, groups) ->
                    val match = rows.firstOrNull { it.id == channelId }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            channel = match,
                            members = members,
                            groups = groups,
                            // Discard any in-flight draft when we re-fetch
                            // so the UI mirrors the freshly authoritative
                            // server state (matches web's updated_at-keyed
                            // reset on save).
                            draft = ChannelEditDraft(),
                            error = if (match == null) "Channel not found" else null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, error = e.toUserMessage("Couldn't load channel"))
                    }
                }
        }
    }

    fun updateDraft(transform: (ChannelEditDraft) -> ChannelEditDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    fun discardDraft() {
        _state.update { it.copy(draft = ChannelEditDraft()) }
    }

    fun save() {
        val channel = _state.value.channel ?: return
        val draft = _state.value.draft
        val body = NotificationChannelUpdate(
            name = draft.name,
            baseAllMembers = draft.baseAllMembers,
            baseIncludePrivate = draft.baseIncludePrivate,
            triggerOnStart = draft.triggerOnStart,
            triggerOnStop = draft.triggerOnStop,
            triggerOnCofrontChange = draft.triggerOnCofrontChange,
            cofrontRedaction = draft.cofrontRedaction,
            payloadSensitivity = draft.payloadSensitivity,
            debounceSeconds = draft.debounceSeconds,
            aggregationWindowSeconds = draft.aggregationWindowSeconds,
            // quietHours / groupRules / memberRules only sent when the
            // user actively toggled them — otherwise we pass null and
            // Moshi omits them from the wire JSON.
            quietHours = if (draft.quietHoursSet) draft.quietHours else null,
            groupRules = if (draft.groupRulesSet) draft.groupRules else null,
            memberRules = if (draft.memberRulesSet) draft.memberRules else null,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { api.updateChannel(channel.id, body) }
                .onSuccess { fresh ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            channel = fresh,
                            draft = ChannelEditDraft(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSaving = false, error = e.toUserMessage("Couldn't save changes"))
                    }
                }
        }
    }

    fun reissueActivation(channelId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isReissuing = true, error = null) }
            runCatching { api.reissueChannelActivation(channelId) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isReissuing = false,
                            reissuedActivationUrl = resp.activationUrl,
                            reissuedExpiresAt = resp.activationExpiresAt,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isReissuing = false,
                            error = e.toUserMessage("Couldn't reissue activation"),
                        )
                    }
                }
        }
    }

    fun toggleEnabled() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            val call = if (channel.destinationState.equals("disabled", ignoreCase = true)) {
                suspend { api.enableChannel(channel.id) }
            } else {
                suspend { api.disableChannel(channel.id) }
            }
            runCatching { call() }
                .onSuccess { load(channel.id) }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't update channel")) }
                }
        }
    }

    fun sendTest() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            _state.update { it.copy(testResult = null, error = null) }
            runCatching { api.sendTestChannel(channel.id) }
                .onSuccess { resp ->
                    val msg = if (resp.delivered) {
                        "Test delivered"
                    } else {
                        resp.error?.takeIf { it.isNotBlank() } ?: "Delivery reported failure"
                    }
                    _state.update {
                        it.copy(testResult = TestResult(ok = resp.delivered, message = msg))
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(testResult = TestResult(ok = false, message = e.toUserMessage("Couldn't send test")))
                    }
                }
        }
    }

    fun dismissTestResult() {
        _state.update { it.copy(testResult = null) }
    }

    fun duplicate() {
        val channel = _state.value.channel ?: return
        viewModelScope.launch {
            runCatching { api.duplicateChannel(channel.id) }
                .onSuccess { resp ->
                    _state.update { it.copy(duplicateResponse = resp) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't duplicate channel")) }
                }
        }
    }

    fun consumeDuplicateResponse(): NotificationChannelCreateResponse? {
        val resp = _state.value.duplicateResponse
        _state.update { it.copy(duplicateResponse = null) }
        return resp
    }

    fun delete(channelId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { api.deleteChannel(channelId) }
                .onSuccess { onDeleted() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.toUserMessage("Couldn't delete channel")) }
                }
        }
    }

    fun dismissReissuedUrl() {
        _state.update { it.copy(reissuedActivationUrl = null, reissuedExpiresAt = null) }
    }
}

/**
 * Returns the channel as the UI should display it: server values overlaid
 * with any pending draft edits. Keeps the UI simple — every renderer just
 * reads from this merged snapshot rather than juggling two sources.
 */
fun NotificationChannelRead.merged(draft: ChannelEditDraft): NotificationChannelRead = copy(
    name = draft.name ?: name,
    baseAllMembers = draft.baseAllMembers ?: baseAllMembers,
    baseIncludePrivate = draft.baseIncludePrivate ?: baseIncludePrivate,
    triggerOnStart = draft.triggerOnStart ?: triggerOnStart,
    triggerOnStop = draft.triggerOnStop ?: triggerOnStop,
    triggerOnCofrontChange = draft.triggerOnCofrontChange ?: triggerOnCofrontChange,
    cofrontRedaction = draft.cofrontRedaction ?: cofrontRedaction,
    payloadSensitivity = draft.payloadSensitivity ?: payloadSensitivity,
    debounceSeconds = draft.debounceSeconds ?: debounceSeconds,
    aggregationWindowSeconds = draft.aggregationWindowSeconds ?: aggregationWindowSeconds,
    quietHours = if (draft.quietHoursSet) draft.quietHours else quietHours,
    groupRules = if (draft.groupRulesSet) draft.groupRules else groupRules,
    memberRules = if (draft.memberRulesSet) draft.memberRules else memberRules,
)

/**
 * Has the user actually edited anything? Used to gate the Save button so
 * we don't fire an empty PATCH.
 */
fun ChannelEditDraft.isDirty(): Boolean =
    name != null ||
        baseAllMembers != null ||
        baseIncludePrivate != null ||
        triggerOnStart != null ||
        triggerOnStop != null ||
        triggerOnCofrontChange != null ||
        cofrontRedaction != null ||
        payloadSensitivity != null ||
        debounceSeconds != null ||
        aggregationWindowSeconds != null ||
        quietHoursSet ||
        groupRulesSet ||
        memberRulesSet

data class CreateChannelUiState(
    val isSubmitting: Boolean = false,
    val createdChannelName: String? = null,
    val activationUrl: String? = null,
    val activationExpiresAt: String? = null,
    val error: String? = null,
)

@HiltViewModel
class CreateChannelViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateChannelUiState())
    val state: StateFlow<CreateChannelUiState> = _state.asStateFlow()

    fun create(
        name: String,
        recipientLabel: String?,
        destinationType: String,
        triggerOnStart: Boolean,
        triggerOnStop: Boolean,
        triggerOnCofrontChange: Boolean,
    ) {
        viewModelScope.launch {
            _state.update { CreateChannelUiState(isSubmitting = true) }
            runCatching {
                val system = api.getOwnSystem()
                val token = api.createWatchToken(
                    system.id,
                    WatchTokenCreate(label = recipientLabel?.takeIf { it.isNotBlank() }),
                )
                api.createChannel(
                    token.id,
                    NotificationChannelCreate(
                        name = name,
                        destinationType = destinationType,
                        triggerOnStart = triggerOnStart,
                        triggerOnStop = triggerOnStop,
                        triggerOnCofrontChange = triggerOnCofrontChange,
                    ),
                )
            }
                .onSuccess { resp ->
                    _state.update {
                        CreateChannelUiState(
                            createdChannelName = resp.channel.name,
                            activationUrl = resp.activationUrl,
                            activationExpiresAt = resp.activationExpiresAt,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        CreateChannelUiState(error = e.toUserMessage("Couldn't create channel"))
                    }
                }
        }
    }

    fun reset() { _state.value = CreateChannelUiState() }
}
