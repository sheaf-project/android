package systems.lupine.sheaf.ui.retention

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
import systems.lupine.sheaf.data.model.RetentionResponse
import systems.lupine.sheaf.data.model.RetentionUpdate
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class RetentionUiState(
    val isLoading: Boolean = false,
    val data: RetentionResponse? = null,
    val totpEnabled: Boolean = false,
    // Step-up auth tier (none / password / totp / both) sourced from System
    // Safety's auth_tier; same gate as other destructive changes.
    val authTier: String = "none",
    val gracePeriodDays: Int = 0,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveError: String? = null,
    val resultMessage: String? = null,
    // Pending update awaiting step-up confirmation. Non-null means the user
    // tried to lower a cap; we hold the proposed update until they enter
    // re-auth in the dialog.
    val pendingLooseningUpdate: RetentionUpdate? = null,
    val isCancellingTrim: Boolean = false,
)

@HiltViewModel
class RetentionViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(RetentionUiState(isLoading = true))
    val state: StateFlow<RetentionUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val data = api.getRetention()
                val safety = runCatching { api.getSystemSafety() }.getOrNull()
                val user = runCatching { api.getMe() }.getOrNull()
                Triple(data, safety, user)
            }
                .onSuccess { (data, safety, user) ->
                    _state.update {
                        it.copy(
                            data = data,
                            authTier = safety?.settings?.authTier ?: "none",
                            gracePeriodDays = safety?.settings?.gracePeriodDays ?: 0,
                            totpEnabled = user?.totpEnabled == true,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Failed to load retention settings")) }
                }
        }
    }

    // Caller passes the desired override values (null = clear override). If
    // either field is being *lowered* (more restrictive on retention, which the
    // backend treats as the loosening / destructive direction), we stash the
    // proposed update and prompt for step-up auth before sending.
    fun proposeUpdate(maxRevisions: Int?, maxRevisionDays: Int?) {
        val current = _state.value.data ?: return
        val newRev = maxRevisions ?: current.tierMaxRevisions
        val newDays = maxRevisionDays ?: current.tierMaxDays
        val curRev = current.overrideRevisions ?: current.tierMaxRevisions
        val curDays = current.overrideDays ?: current.tierMaxDays
        val isLowering = newRev < curRev || newDays < curDays

        val update = RetentionUpdate(maxRevisions = maxRevisions, maxRevisionDays = maxRevisionDays)
        if (isLowering && _state.value.authTier != "none") {
            _state.update { it.copy(pendingLooseningUpdate = update, saveError = null) }
        } else {
            submit(update)
        }
    }

    fun cancelPendingUpdate() { _state.update { it.copy(pendingLooseningUpdate = null) } }

    fun confirmLoosening(password: String?, totpCode: String?) {
        val update = _state.value.pendingLooseningUpdate ?: return
        submit(update.copy(password = password, totpCode = totpCode))
    }

    private fun submit(update: RetentionUpdate) {
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching { api.updateRetention(update) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            data = resp,
                            isSaving = false,
                            pendingLooseningUpdate = null,
                            resultMessage = "Retention updated.",
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else e.toUserMessage("Failed to update retention")
                    _state.update { it.copy(isSaving = false, saveError = msg) }
                }
        }
    }

    fun cancelTrimNotice() {
        val noticeId = _state.value.data?.trimNotice?.id ?: return
        _state.update { it.copy(isCancellingTrim = true) }
        viewModelScope.launch {
            runCatching { api.cancelTrimNotice(noticeId) }
                .onSuccess {
                    val data = _state.value.data
                    _state.update {
                        it.copy(
                            isCancellingTrim = false,
                            data = data?.copy(trimNotice = null),
                            resultMessage = "Trim notice cancelled.",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isCancellingTrim = false, error = e.toUserMessage("Failed to cancel trim notice")) }
                }
        }
    }

    fun clearResult() { _state.update { it.copy(resultMessage = null) } }
    fun clearError() { _state.update { it.copy(error = null, saveError = null) } }
}
