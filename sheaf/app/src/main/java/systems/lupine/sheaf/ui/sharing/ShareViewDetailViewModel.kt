package systems.lupine.sheaf.ui.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.CustomFieldRead
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.PREVIEW_GENERIC
import systems.lupine.sheaf.data.model.PREVIEW_SYSTEM_DETAILS
import systems.lupine.sheaf.data.model.SharePreview
import systems.lupine.sheaf.data.model.ShareViewFieldAdd
import systems.lupine.sheaf.data.model.ShareViewGroupAdd
import systems.lupine.sheaf.data.model.ShareViewGroupAddResult
import systems.lupine.sheaf.data.model.ShareViewMemberAdd
import systems.lupine.sheaf.data.model.ShareViewRead
import systems.lupine.sheaf.data.model.ShareViewUpdate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

/** The flags that widen what a view serves, and so carry a pending twin. */
enum class ExposureFlag(val label: String, val supporting: String) {
    INCLUDE_MEMBERS("Show the roster", "The list of members in this view"),
    INCLUDE_ALL_PUBLIC_MEMBERS(
        "Show everyone set to Public",
        "Follows each member's privacy instead of the list below, so someone you set to Public later appears without editing the view",
    ),
    INCLUDE_BIO("Show bios", "Each member's description"),
    INCLUDE_FRONTING("Show who is fronting", "Live front state for members in this view"),
    FRONTING_SHOW_COUNT("Count hidden fronters", "Adds \"and N others not shown\""),
    INCLUDE_RELATIONSHIPS("Show relationships", "Edges between members this view publishes"),
    INCLUDE_GROUPS("Show groups", "Public groups, listing only members already shown"),
}

/** The two link preview cards, set independently. */
enum class PreviewCard { PROFILE, MEMBER }

/** An action held back because the server wants credentials for it. */
sealed interface ViewRaise {
    data class Flag(val flag: ExposureFlag, val value: Boolean) : ViewRaise
    data class Preview(val card: PreviewCard, val on: Boolean) : ViewRaise
    data class AddMember(val memberId: String) : ViewRaise
    data class AddField(val fieldId: String) : ViewRaise
    data class AddGroup(val groupId: String) : ViewRaise
}

data class ShareViewDetailUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val view: ShareViewRead? = null,
    val allMembers: List<MemberRead> = emptyList(),
    val allFields: List<CustomFieldRead> = emptyList(),
    val allGroups: List<GroupRead> = emptyList(),
    val publicProfilesEnabled: Boolean = false,
    val totpEnabled: Boolean = false,
    val authTier: String = "none",
    val stepUpArmed: Boolean = false,
    val graceDays: Int = 0,
    val busy: Boolean = false,
    val actionError: String? = null,
    val stepUp: ViewRaise? = null,
    val stepUpError: String? = null,
    val groupAddResult: ShareViewGroupAddResult? = null,
    val preview: SharePreview? = null,
    val previewError: String? = null,
    val previewLoading: Boolean = false,
    val deleted: Boolean = false,
) {
    val stepUpMeaningful: Boolean get() = stepUpArmed && authTier != "none"
}

@HiltViewModel
class ShareViewDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val viewId: String = checkNotNull(savedStateHandle["viewId"])

    private val _state = MutableStateFlow(ShareViewDetailUiState())
    val state: StateFlow<ShareViewDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                coroutineScope {
                    val viewD = async { api.getShareView(viewId) }
                    val membersD = async { runCatching { api.listMembers() }.getOrDefault(emptyList()) }
                    val fieldsD = async { runCatching { api.listFields() }.getOrDefault(emptyList()) }
                    val groupsD = async { runCatching { api.listGroups() }.getOrDefault(emptyList()) }
                    val safetyD = async { runCatching { api.getSystemSafety() }.getOrNull() }
                    val meD = async { runCatching { api.getMe() }.getOrNull() }
                    Loaded(
                        viewD.await(), membersD.await(), fieldsD.await(),
                        groupsD.await(), safetyD.await(), meD.await(),
                    )
                }
            }
                .onSuccess { l ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            view = l.view,
                            allMembers = l.members,
                            allFields = l.fields,
                            allGroups = l.groups,
                            publicProfilesEnabled = l.me?.publicProfilesEnabled ?: false,
                            totpEnabled = l.me?.totpEnabled == true,
                            authTier = l.safety?.settings?.authTier ?: "none",
                            stepUpArmed = l.safety?.settings?.appliesToProfileVisibility ?: false,
                            graceDays = l.safety?.settings?.gracePeriodDays ?: 0,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, loadError = e.toUserMessage("Failed to load view")) }
                }
        }
    }

    private data class Loaded(
        val view: ShareViewRead,
        val members: List<MemberRead>,
        val fields: List<CustomFieldRead>,
        val groups: List<GroupRead>,
        val safety: systems.lupine.sheaf.data.model.SystemSafetyResponse?,
        val me: systems.lupine.sheaf.data.model.UserRead?,
    )

    /**
     * A flag change only exposes when it turns ON and the view is already
     * shared. Turning one off is going dark, which is never slowed down.
     */
    fun setFlag(flag: ExposureFlag, value: Boolean) {
        val view = _state.value.view ?: return
        val exposing = value && view.isShared
        if (exposing && _state.value.stepUpMeaningful) {
            _state.update { it.copy(stepUp = ViewRaise.Flag(flag, value), stepUpError = null) }
            return
        }
        submitUpdate(ViewRaise.Flag(flag, value), null, null)
    }

    /**
     * A rich card is a raise like any flag: it reaches everyone in a chat
     * without them opening anything, and the chat service keeps a copy. So it
     * steps up and stages the same way, and turning it off never waits.
     */
    fun setPreviewCard(card: PreviewCard, on: Boolean) {
        val view = _state.value.view ?: return
        val r = ViewRaise.Preview(card, on)
        if (on && view.isShared && _state.value.stepUpMeaningful) {
            _state.update { it.copy(stepUp = r, stepUpError = null) }
            return
        }
        submitUpdate(r, null, null)
    }

    /**
     * Permalinks skip the gate on purpose, in both directions: they expose
     * nobody the roster does not already expose, only a stable address for
     * them. The instance switch still applies to turning it on.
     */
    fun setMemberPermalinks(value: Boolean) = act("Failed to update this view") {
        val updated = api.updateShareView(viewId, ShareViewUpdate(memberPermalinks = value))
        _state.update { it.copy(view = updated) }
    }

    fun rename(name: String) = act("Failed to rename") {
        val updated = api.updateShareView(viewId, ShareViewUpdate(name = name))
        _state.update { it.copy(view = updated) }
    }

    fun addMember(memberId: String) = raise(ViewRaise.AddMember(memberId))
    fun addField(fieldId: String) = raise(ViewRaise.AddField(fieldId))
    fun addGroup(groupId: String) = raise(ViewRaise.AddGroup(groupId))

    private fun raise(r: ViewRaise) {
        val view = _state.value.view ?: return
        // Adding to a view nobody can reach yet exposes nothing.
        if (view.isShared && _state.value.stepUpMeaningful) {
            _state.update { it.copy(stepUp = r, stepUpError = null) }
            return
        }
        submit(r, null, null)
    }

    fun confirmStepUp(password: String?, totpCode: String?) {
        val pending = _state.value.stepUp ?: return
        when (pending) {
            is ViewRaise.Flag, is ViewRaise.Preview -> submitUpdate(pending, password, totpCode)
            else -> submit(pending, password, totpCode)
        }
    }

    fun dismissStepUp() { _state.update { it.copy(stepUp = null, stepUpError = null) } }

    private fun submitUpdate(r: ViewRaise, password: String?, totpCode: String?) {
        val body = when (r) {
            is ViewRaise.Flag -> when (r.flag) {
                ExposureFlag.INCLUDE_MEMBERS -> ShareViewUpdate(includeMembers = r.value)
                ExposureFlag.INCLUDE_ALL_PUBLIC_MEMBERS -> ShareViewUpdate(includeAllPublicMembers = r.value)
                ExposureFlag.INCLUDE_BIO -> ShareViewUpdate(includeBio = r.value)
                ExposureFlag.INCLUDE_FRONTING -> ShareViewUpdate(includeFronting = r.value)
                ExposureFlag.FRONTING_SHOW_COUNT -> ShareViewUpdate(frontingShowCount = r.value)
                ExposureFlag.INCLUDE_RELATIONSHIPS -> ShareViewUpdate(includeRelationships = r.value)
                ExposureFlag.INCLUDE_GROUPS -> ShareViewUpdate(includeGroups = r.value)
            }
            is ViewRaise.Preview -> {
                val mode = if (r.on) PREVIEW_SYSTEM_DETAILS else PREVIEW_GENERIC
                when (r.card) {
                    PreviewCard.PROFILE -> ShareViewUpdate(linkPreviewMode = mode)
                    PreviewCard.MEMBER -> ShareViewUpdate(memberLinkPreviewMode = mode)
                }
            }
            else -> error("Only flag and preview changes go through submitUpdate")
        }.copy(password = password?.ifBlank { null }, totpCode = totpCode?.ifBlank { null })

        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null, stepUpError = null) }
            runCatching { api.updateShareView(viewId, body) }
                .onSuccess { updated ->
                    _state.update { it.copy(busy = false, stepUp = null, view = updated) }
                }
                .onFailure { e ->
                    handleRaiseFailure(e, r, "Failed to update this view")
                }
        }
    }

    private fun submit(r: ViewRaise, password: String?, totpCode: String?) {
        val pw = password?.ifBlank { null }
        val code = totpCode?.ifBlank { null }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null, stepUpError = null) }
            runCatching {
                when (r) {
                    is ViewRaise.AddMember ->
                        api.addShareViewMember(viewId, ShareViewMemberAdd(r.memberId, pw, code))
                    is ViewRaise.AddField ->
                        api.addShareViewField(viewId, ShareViewFieldAdd(r.fieldId, pw, code))
                    is ViewRaise.AddGroup -> {
                        val result = api.addShareViewGroup(viewId, ShareViewGroupAdd(r.groupId, pw, code))
                        _state.update { it.copy(groupAddResult = result) }
                        api.getShareView(viewId)
                    }
                    is ViewRaise.Flag, is ViewRaise.Preview -> error("Flag and preview changes go through submitUpdate")
                }
            }
                .onSuccess { updated ->
                    _state.update { it.copy(busy = false, stepUp = null, view = updated) }
                }
                .onFailure { e -> handleRaiseFailure(e, r, "Failed to add to this view") }
        }
    }

    // The local gate above mirrors the server's predicate, and a mirror can
    // drift. When it does, this is what keeps the bounce from being a dead end
    // with nowhere to type a password.
    private fun handleRaiseFailure(e: Throwable, r: ViewRaise, fallback: String) {
        when (val err = e.toShareError(fallback)) {
            is ShareError.StepUpRequired ->
                _state.update { it.copy(busy = false, stepUp = r, stepUpError = null) }
            is ShareError.BadCredentials ->
                _state.update { it.copy(busy = false, stepUp = r, stepUpError = err.message) }
            else ->
                _state.update { it.copy(busy = false, stepUp = null, actionError = err.message(fallback)) }
        }
    }

    // Removals are immediate and ungated: nothing slows down going dark.

    fun removeMember(memberId: String) = act("Failed to remove member") {
        api.removeShareViewMember(viewId, memberId)
        _state.update { it.copy(view = api.getShareView(viewId)) }
    }

    fun removeField(fieldId: String) = act("Failed to remove field") {
        api.removeShareViewField(viewId, fieldId)
        _state.update { it.copy(view = api.getShareView(viewId)) }
    }

    fun removeGroup(groupId: String, removeMembers: Boolean) = act("Failed to remove group") {
        api.removeShareViewGroup(viewId, groupId, removeMembers)
        _state.update { it.copy(view = api.getShareView(viewId)) }
    }

    fun deleteView() = act("Failed to delete view") {
        api.deleteShareView(viewId)
        _state.update { it.copy(deleted = true) }
    }

    /**
     * Preview runs the same projection functions the anonymous router uses, so
     * it cannot describe a page the public surface would not serve. That is
     * why it is a server call rather than something assembled here.
     */
    fun loadPreview() {
        viewModelScope.launch {
            _state.update { it.copy(previewLoading = true, previewError = null) }
            runCatching { api.previewShareView(viewId) }
                .onSuccess { p -> _state.update { it.copy(previewLoading = false, preview = p) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(previewLoading = false, previewError = e.toUserMessage("Failed to load preview"))
                    }
                }
        }
    }

    fun clearPreview() { _state.update { it.copy(preview = null, previewError = null) } }
    fun clearGroupAddResult() { _state.update { it.copy(groupAddResult = null) } }
    fun clearActionError() { _state.update { it.copy(actionError = null) } }

    private fun act(fallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null) }
            runCatching { block() }
                .onSuccess { _state.update { it.copy(busy = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(busy = false, actionError = e.toShareError(fallback).message(fallback))
                    }
                }
        }
    }
}
