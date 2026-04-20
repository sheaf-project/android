package systems.lupine.sheaf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ClientSettingsBody
import systems.lupine.sheaf.data.model.DeleteAccountRequest
import systems.lupine.sheaf.data.model.DeleteConfirmationUpdate
import systems.lupine.sheaf.data.model.FileRead
import systems.lupine.sheaf.data.model.SystemRead
import systems.lupine.sheaf.data.model.TOTPDisable
import systems.lupine.sheaf.data.model.TOTPSetupResponse
import systems.lupine.sheaf.data.model.TOTPVerify
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.notification.FrontNotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

enum class TotpStep { LOADING, SECRET, VERIFY, RECOVERY_CODES, DONE }

data class SettingsUiState(
    val user: UserRead? = null,
    val system: SystemRead? = null,
    val memberCount: Int = 0,
    val groupCount: Int = 0,
    val frontingCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val exportJson: String? = null,
    // TOTP
    val totpStep: TotpStep = TotpStep.LOADING,
    val totpSetupResponse: TOTPSetupResponse? = null,
    val totpError: String? = null,
    val totpIsVerifying: Boolean = false,
    val totpIsDisabling: Boolean = false,
    val totpCopiedSecret: Boolean = false,
    val totpCopiedCodes: Boolean = false,
    // Account deletion
    val isDeletingAccount: Boolean = false,
    val accountDeletionRequested: Boolean = false,
    val deletionError: String? = null,
    val isCancellingDeletion: Boolean = false,
    val cancelDeletionError: String? = null,
    // Delete confirmation level
    val isUpdatingDeleteConfirmation: Boolean = false,
    val deleteConfirmationError: String? = null,
    // Email verification
    val isResendingVerification: Boolean = false,
    val verificationEmailSent: Boolean = false,
    // Orphaned files
    val isCheckingFiles: Boolean = false,
    val orphanedFiles: List<FileRead>? = null,
    val isDeletingOrphans: Boolean = false,
    val fileError: String? = null,
)

private const val CLIENT_ID = "android"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: SheafApiService,
    private val prefs: PreferencesRepository,
    private val notificationHelper: FrontNotificationHelper,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(isLoading = true))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val baseUrl: StateFlow<String> = prefs.baseUrl
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val themeMode: StateFlow<String> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val frontNotificationEnabled: StateFlow<Boolean> = prefs.frontNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val user = api.getMe()
                val system = api.getOwnSystem()
                user to system
            }.onSuccess { (user, system) ->
                _state.update { it.copy(user = user, system = system, isLoading = false) }
                runCatching { api.getClientSettings(CLIENT_ID) }
                    .onSuccess { resp ->
                        val theme = resp.settings["theme"] as? String
                        if (theme != null) prefs.saveTheme(theme)
                    }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                return@launch
            }
            val memberCount = runCatching { api.listMembers().size }.getOrDefault(0)
            val groupCount = runCatching { api.listGroups().size }.getOrDefault(0)
            val frontingCount = runCatching {
                api.getCurrentFronts().flatMap { it.memberIds }.toSet().size
            }.getOrDefault(0)
            _state.update { it.copy(memberCount = memberCount, groupCount = groupCount, frontingCount = frontingCount) }
        }
    }

    fun saveBaseUrl(url: String) {
        viewModelScope.launch { prefs.saveBaseUrl(url) }
    }

    fun saveTheme(mode: String) {
        viewModelScope.launch {
            prefs.saveTheme(mode)
            runCatching { api.saveClientSettings(CLIENT_ID, ClientSettingsBody(mapOf("theme" to mode))) }
        }
    }

    fun toggleFrontNotification(enabled: Boolean) {
        viewModelScope.launch {
            prefs.saveFrontNotification(enabled)
            if (!enabled) notificationHelper.cancel()
        }
    }

    fun exportData() {
        viewModelScope.launch {
            runCatching { api.exportAll() }
                .onSuccess { data ->
                    _state.update { it.copy(exportJson = data.string()) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    // ── TOTP ──────────────────────────────────────────────────────────────────

    fun startTotpSetup() {
        _state.update { it.copy(totpStep = TotpStep.LOADING, totpError = null, totpSetupResponse = null) }
        viewModelScope.launch {
            runCatching { api.setupTotp() }
                .onSuccess { resp ->
                    _state.update { it.copy(totpSetupResponse = resp, totpStep = TotpStep.SECRET) }
                }
                .onFailure { e ->
                    _state.update { it.copy(totpError = e.toUserMessage(), totpStep = TotpStep.SECRET) }
                }
        }
    }

    fun advanceTotpToVerify() {
        _state.update { it.copy(totpStep = TotpStep.VERIFY, totpError = null) }
    }

    fun verifyTotp(code: String) {
        _state.update { it.copy(totpIsVerifying = true, totpError = null) }
        viewModelScope.launch {
            runCatching { api.verifyTotp(TOTPVerify(code)) }
                .onSuccess {
                    _state.update { it.copy(totpIsVerifying = false, totpStep = TotpStep.RECOVERY_CODES) }
                }
                .onFailure {
                    _state.update { it.copy(totpIsVerifying = false, totpError = "Incorrect code — please try again") }
                }
        }
    }

    fun advanceTotpToDone() {
        _state.update { it.copy(totpStep = TotpStep.DONE) }
        // Reload user to update totpEnabled status
        viewModelScope.launch {
            runCatching { api.getMe() }.onSuccess { user ->
                _state.update { it.copy(user = user) }
            }
        }
    }

    fun disableTotp(password: String, totpCode: String) {
        val email = _state.value.user?.email ?: return
        _state.update { it.copy(totpIsDisabling = true, totpError = null) }
        viewModelScope.launch {
            runCatching { api.disableTotp(TOTPDisable(email, password, totpCode)) }
                .onSuccess {
                    runCatching { api.getMe() }.onSuccess { user ->
                        _state.update { it.copy(user = user, totpIsDisabling = false) }
                    }.onFailure {
                        _state.update { it.copy(totpIsDisabling = false) }
                    }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() == 400)
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to disable 2FA")
                    _state.update { it.copy(totpIsDisabling = false, totpError = msg) }
                }
        }
    }

    fun resetTotpSetup() {
        _state.update { it.copy(
            totpStep = TotpStep.LOADING,
            totpSetupResponse = null,
            totpError = null,
            totpIsVerifying = false,
            totpCopiedSecret = false,
            totpCopiedCodes = false,
        ) }
    }

    // ── Account deletion ──────────────────────────────────────────────────────

    fun requestAccountDeletion(password: String, totpCode: String?) {
        _state.update { it.copy(isDeletingAccount = true, deletionError = null) }
        viewModelScope.launch {
            runCatching { api.deleteAccount(DeleteAccountRequest(password, totpCode?.ifBlank { null })) }
                .onSuccess {
                    _state.update { it.copy(isDeletingAccount = false, accountDeletionRequested = true) }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to request account deletion")
                    _state.update { it.copy(isDeletingAccount = false, deletionError = msg) }
                }
        }
    }

    fun cancelAccountDeletion() {
        _state.update { it.copy(isCancellingDeletion = true, cancelDeletionError = null) }
        viewModelScope.launch {
            runCatching { api.cancelAccountDeletion() }
                .onSuccess { load() }
                .onFailure { e ->
                    val msg = when {
                        e is HttpException && e.code() == 409 -> "No pending deletion to cancel"
                        e is HttpException && e.code() == 429 -> "Too many requests — please wait a moment and try again"
                        else -> e.toUserMessage("Failed to cancel account deletion")
                    }
                    _state.update { it.copy(isCancellingDeletion = false, cancelDeletionError = msg) }
                }
        }
    }

    // ── Delete confirmation level ─────────────────────────────────────────────

    fun updateDeleteConfirmation(level: String, password: String, totpCode: String?) {
        _state.update { it.copy(isUpdatingDeleteConfirmation = true, deleteConfirmationError = null) }
        viewModelScope.launch {
            runCatching {
                api.updateDeleteConfirmation(DeleteConfirmationUpdate(level, password, totpCode?.ifBlank { null }))
            }
                .onSuccess { system ->
                    _state.update { it.copy(isUpdatingDeleteConfirmation = false, system = system) }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to update deletion confirmation")
                    _state.update { it.copy(isUpdatingDeleteConfirmation = false, deleteConfirmationError = msg) }
                }
        }
    }

    // ── Email verification ────────────────────────────────────────────────────

    fun resendVerificationEmail() {
        _state.update { it.copy(isResendingVerification = true) }
        viewModelScope.launch {
            runCatching { api.resendVerification() }
                .onSuccess { _state.update { it.copy(isResendingVerification = false, verificationEmailSent = true) } }
                .onFailure { e -> _state.update { it.copy(isResendingVerification = false, error = e.toUserMessage("Failed to resend verification email")) } }
        }
    }

    fun clearVerificationEmailSent() { _state.update { it.copy(verificationEmailSent = false) } }

    fun clearExport() { _state.update { it.copy(exportJson = null) } }
    fun clearError()  { _state.update { it.copy(error = null) } }
    fun clearTotpError() { _state.update { it.copy(totpError = null) } }
    fun clearDeletionError() { _state.update { it.copy(deletionError = null) } }
    fun clearDeleteConfirmationError() { _state.update { it.copy(deleteConfirmationError = null) } }
    fun clearAccountDeletionRequested() { _state.update { it.copy(accountDeletionRequested = false) } }

    // ── Orphaned files ────────────────────────────────────────────────────────

    fun checkOrphanedFiles() {
        _state.update { it.copy(isCheckingFiles = true, fileError = null, orphanedFiles = null) }
        viewModelScope.launch {
            runCatching {
                val files = api.listFiles()
                val members = api.listMembers()
                val system = api.getOwnSystem()
                val usedUrls = buildSet {
                    system.avatarUrl?.let { add(it) }
                    members.forEach { m -> m.avatarUrl?.let { add(it) } }
                }
                files.filter { it.url !in usedUrls }
            }.onSuccess { orphans ->
                _state.update { it.copy(isCheckingFiles = false, orphanedFiles = orphans) }
            }.onFailure { e ->
                _state.update { it.copy(isCheckingFiles = false, fileError = e.toUserMessage()) }
            }
        }
    }

    fun deleteOrphanedFiles() {
        val orphans = _state.value.orphanedFiles ?: return
        if (orphans.isEmpty()) return
        _state.update { it.copy(isDeletingOrphans = true, fileError = null) }
        viewModelScope.launch {
            runCatching {
                orphans.forEach { api.deleteFile(it.id) }
            }.onSuccess {
                _state.update { it.copy(isDeletingOrphans = false, orphanedFiles = emptyList()) }
            }.onFailure { e ->
                _state.update { it.copy(isDeletingOrphans = false, fileError = e.toUserMessage()) }
            }
        }
    }

    fun clearOrphanedFiles() { _state.update { it.copy(orphanedFiles = null) } }
    fun clearFileError() { _state.update { it.copy(fileError = null) } }
}
