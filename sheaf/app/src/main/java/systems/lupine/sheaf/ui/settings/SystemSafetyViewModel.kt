package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.PendingActionRead
import systems.lupine.sheaf.data.model.SafetyChangeRequestRead
import systems.lupine.sheaf.data.model.SystemSafetySettings
import systems.lupine.sheaf.data.model.SystemSafetyUpdate
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class SystemSafetyUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val settings: SystemSafetySettings? = null,
    val draft: SystemSafetySettings? = null,
    val pendingActions: List<PendingActionRead> = emptyList(),
    val pendingChanges: List<SafetyChangeRequestRead> = emptyList(),
    val totpEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val needsReauth: Boolean = false,
    val lastApplied: List<String> = emptyList(),
    val lastDeferred: List<String> = emptyList(),
    val cancellingActionIds: Set<String> = emptySet(),
    val cancellingChangeIds: Set<String> = emptySet(),
    val cancelError: String? = null,
) {
    val isDirty: Boolean get() = settings != null && draft != null && settings != draft
}

@HiltViewModel
class SystemSafetyViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemSafetyUiState())
    val state: StateFlow<SystemSafetyUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val safety = api.getSystemSafety()
                val user: UserRead? = runCatching { api.getMe() }.getOrNull()
                safety to user
            }
                .onSuccess { (resp, user) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            settings = resp.settings,
                            draft = resp.settings,
                            pendingActions = resp.pendingActions,
                            pendingChanges = resp.pendingChanges,
                            totpEnabled = user?.totpEnabled == true,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, loadError = e.toUserMessage("Failed to load safety settings")) }
                }
        }
    }

    fun updateDraft(transform: SystemSafetySettings.() -> SystemSafetySettings) {
        _state.update { st ->
            val draft = st.draft ?: return@update st
            st.copy(draft = draft.transform(), saveError = null)
        }
    }

    fun revertDraft() {
        _state.update { it.copy(draft = it.settings, saveError = null, needsReauth = false) }
    }

    /** True if the diff between current settings and draft requires re-auth (any loosening). */
    fun draftRequiresReauth(): Boolean {
        val current = _state.value.settings ?: return false
        val draft = _state.value.draft ?: return false
        return isLoosening(current, draft)
    }

    fun save(password: String?, totpCode: String?) {
        val current = _state.value.settings ?: return
        val draft = _state.value.draft ?: return
        if (current == draft) return
        _state.update { it.copy(isSaving = true, saveError = null, needsReauth = false) }
        val body = buildUpdate(current, draft, password, totpCode)
        viewModelScope.launch {
            runCatching { api.updateSystemSafety(body) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            settings = resp.settings,
                            draft = resp.settings,
                            pendingChanges = if (resp.pendingChange != null) it.pendingChanges + resp.pendingChange else it.pendingChanges,
                            lastApplied = resp.applied,
                            lastDeferred = resp.deferred,
                        )
                    }
                }
                .onFailure { e ->
                    val msg = when {
                        e is HttpException && e.code() in listOf(400, 401) -> "Incorrect password or authenticator code"
                        else -> e.toUserMessage("Failed to update safety settings")
                    }
                    _state.update { it.copy(isSaving = false, saveError = msg) }
                }
        }
    }

    fun requestReauth() {
        _state.update { it.copy(needsReauth = true, saveError = null) }
    }

    fun dismissReauth() {
        _state.update { it.copy(needsReauth = false, saveError = null) }
    }

    fun cancelPendingAction(id: String) {
        _state.update { it.copy(cancellingActionIds = it.cancellingActionIds + id, cancelError = null) }
        viewModelScope.launch {
            runCatching { api.cancelPendingAction(id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            pendingActions = it.pendingActions.filter { p -> p.id != id },
                            cancellingActionIds = it.cancellingActionIds - id,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            cancellingActionIds = it.cancellingActionIds - id,
                            cancelError = e.toUserMessage("Failed to cancel action"),
                        )
                    }
                }
        }
    }

    fun cancelPendingChange(id: String) {
        _state.update { it.copy(cancellingChangeIds = it.cancellingChangeIds + id, cancelError = null) }
        viewModelScope.launch {
            runCatching { api.cancelPendingSafetyChange(id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            pendingChanges = it.pendingChanges.filter { c -> c.id != id },
                            cancellingChangeIds = it.cancellingChangeIds - id,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            cancellingChangeIds = it.cancellingChangeIds - id,
                            cancelError = e.toUserMessage("Failed to cancel change"),
                        )
                    }
                }
        }
    }

    fun clearLoadError() { _state.update { it.copy(loadError = null) } }
    fun clearSaveError() { _state.update { it.copy(saveError = null) } }
    fun clearCancelError() { _state.update { it.copy(cancelError = null) } }
    fun clearLastUpdate() { _state.update { it.copy(lastApplied = emptyList(), lastDeferred = emptyList()) } }

    private fun buildUpdate(
        current: SystemSafetySettings,
        draft: SystemSafetySettings,
        password: String?,
        totpCode: String?,
    ): SystemSafetyUpdate = SystemSafetyUpdate(
        gracePeriodDays = draft.gracePeriodDays.takeIf { it != current.gracePeriodDays },
        authTier = draft.authTier.takeIf { it != current.authTier },
        appliesToMembers = draft.appliesToMembers.takeIf { it != current.appliesToMembers },
        appliesToGroups = draft.appliesToGroups.takeIf { it != current.appliesToGroups },
        appliesToTags = draft.appliesToTags.takeIf { it != current.appliesToTags },
        appliesToFields = draft.appliesToFields.takeIf { it != current.appliesToFields },
        appliesToFronts = draft.appliesToFronts.takeIf { it != current.appliesToFronts },
        appliesToJournals = draft.appliesToJournals.takeIf { it != current.appliesToJournals },
        appliesToImages = draft.appliesToImages.takeIf { it != current.appliesToImages },
        appliesToRevisions = draft.appliesToRevisions.takeIf { it != current.appliesToRevisions },
        autoPinFirstRevision = draft.autoPinFirstRevision.takeIf { it != current.autoPinFirstRevision },
        password = password?.ifBlank { null },
        totpCode = totpCode?.ifBlank { null },
    )

    companion object {
        // Mirrors sheaf/services/system_safety.py: PASSWORD and TOTP are equivalent strength.
        private fun authTierStrength(level: String): Int = when (level) {
            "none" -> 0
            "password", "totp" -> 1
            "both" -> 2
            else -> 0
        }

        fun isLoosening(current: SystemSafetySettings, draft: SystemSafetySettings): Boolean {
            if (draft.gracePeriodDays < current.gracePeriodDays) return true
            if (authTierStrength(draft.authTier) < authTierStrength(current.authTier)) return true
            if (current.appliesToMembers && !draft.appliesToMembers) return true
            if (current.appliesToGroups && !draft.appliesToGroups) return true
            if (current.appliesToTags && !draft.appliesToTags) return true
            if (current.appliesToFields && !draft.appliesToFields) return true
            if (current.appliesToFronts && !draft.appliesToFronts) return true
            if (current.appliesToJournals && !draft.appliesToJournals) return true
            if (current.appliesToImages && !draft.appliesToImages) return true
            if (current.appliesToRevisions && !draft.appliesToRevisions) return true
            if (current.autoPinFirstRevision && !draft.autoPinFirstRevision) return true
            return false
        }
    }
}
