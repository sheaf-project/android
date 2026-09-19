package systems.lupine.sheaf.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SystemUpdate
import systems.lupine.sheaf.ui.sharing.ShareError
import systems.lupine.sheaf.ui.sharing.loadRaiseGate
import systems.lupine.sheaf.ui.sharing.message
import systems.lupine.sheaf.ui.sharing.toShareError
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class SystemEditForm(
    val name: String = "",
    val description: String = "",
    val note: String = "",
    val tag: String = "",
    val avatarUrl: String = "",
    val color: String = "",
    val privacy: String = "private",
    val showMemberCreatedDate: Boolean = false,
)

data class SystemEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    // The master Public switch is a ceiling like any other, so it takes the
    // same gate as a member or a relationship edge.
    val raiseGate: systems.lupine.sheaf.ui.sharing.RaiseGate =
        systems.lupine.sheaf.ui.sharing.RaiseGate(),
    val saveNeedsStepUp: Boolean = false,
    val stepUpError: String? = null,
    val pendingPrivacy: String? = null,
    val privacyActivatesAt: String? = null,
)

@HiltViewModel
class SystemEditViewModel @Inject constructor(
    private val api: SheafApiService,
    private val cache: systems.lupine.sheaf.data.db.LocalCache,
    @ApplicationContext private val context: Context,
    val markdownImages: systems.lupine.sheaf.ui.components.MarkdownImageDelegate,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemEditUiState())
    val state: StateFlow<SystemEditUiState> = _state.asStateFlow()

    private val _form = MutableStateFlow(SystemEditForm())
    val form: StateFlow<SystemEditForm> = _form.asStateFlow()

    /** The form as loaded, so the screen can tell whether leaving loses work. */
    private val _baselineForm = MutableStateFlow(SystemEditForm())
    val baselineForm: StateFlow<SystemEditForm> = _baselineForm.asStateFlow()

    init {
        markdownImages.loadUser(viewModelScope)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getOwnSystem() }
                .onSuccess { system ->
                    _form.value = SystemEditForm(
                        name = system.name,
                        description = system.description ?: "",
                        note = system.note ?: "",
                        tag = system.tag ?: "",
                        avatarUrl = system.avatarUrl ?: "",
                        color = system.color ?: "",
                        privacy = system.privacy,
                        showMemberCreatedDate = system.showMemberCreatedDate,
                    )
                    _baselineForm.value = _form.value
                    baselinePrivacy = system.privacy
                    _state.update {
                        it.copy(
                            isLoading = false,
                            pendingPrivacy = system.pendingPrivacy,
                            privacyActivatesAt = system.privacyActivatesAt,
                        )
                    }
                    _state.update { it.copy(raiseGate = api.loadRaiseGate()) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun updateForm(update: SystemEditForm.() -> SystemEditForm) {
        _form.update { it.update() }
    }

    private var baselinePrivacy: String? = null

    fun save() {
        if (systems.lupine.sheaf.ui.sharing.isRaiseToPublic(baselinePrivacy, _form.value.privacy) &&
            _state.value.raiseGate.stepUpNeeded
        ) {
            _state.update { it.copy(saveNeedsStepUp = true, stepUpError = null) }
            return
        }
        save(null, null)
    }

    fun dismissStepUp() { _state.update { it.copy(saveNeedsStepUp = false, stepUpError = null) } }

    fun save(password: String?, totpCode: String?) {
        val f = _form.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, stepUpError = null) }
            runCatching {
                api.updateOwnSystem(SystemUpdate(
                    name = f.name.takeIf { it.isNotBlank() },
                    description = f.description.takeIf { it.isNotBlank() },
                    // Send empty string as-is so the backend's "empty clears"
                    // contract lets a user wipe a note that was previously set.
                    note = f.note,
                    tag = f.tag.takeIf { it.isNotBlank() },
                    avatarUrl = f.avatarUrl.takeIf { it.isNotBlank() },
                    color = f.color.takeIf { it.isNotBlank() },
                    privacy = f.privacy,
                    showMemberCreatedDate = f.showMemberCreatedDate,
                    password = password?.ifBlank { null },
                    totpCode = totpCode?.ifBlank { null },
                ))
            }
                .onSuccess { updated ->
                    // Write through so display preferences read from the cached
                    // system (the member profile's created-date row) reflect the
                    // change straight away, without waiting for a Home refresh.
                    runCatching { cache.saveSystem(updated) }
                    baselinePrivacy = updated.privacy
                    _state.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            saveNeedsStepUp = false,
                            pendingPrivacy = updated.pendingPrivacy,
                            privacyActivatesAt = updated.privacyActivatesAt,
                        )
                    }
                }
                .onFailure { e ->
                    when (val err = e.toShareError("Failed to save")) {
                        is ShareError.StepUpRequired -> _state.update {
                            it.copy(isSaving = false, saveNeedsStepUp = true, stepUpError = null)
                        }
                        is ShareError.BadCredentials -> _state.update {
                            it.copy(isSaving = false, saveNeedsStepUp = true, stepUpError = err.message)
                        }
                        else -> _state.update {
                            it.copy(
                                isSaving = false,
                                saveNeedsStepUp = false,
                                error = err.message("Failed to save"),
                            )
                        }
                    }
                }
        }
    }

    fun uploadAndSetAvatar(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, error = null) }
            runCatching {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val ext = mimeType.substringAfter("/").let { if (it == "jpeg") "jpg" else it }
                val part = MultipartBody.Part.createFormData("file", "avatar.$ext", requestBody)
                api.uploadFile(part)
            }
                .onSuccess { response ->
                    _form.update { it.copy(avatarUrl = response.url) }
                    _state.update { it.copy(isUploadingAvatar = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploadingAvatar = false, error = "Failed to upload avatar: ${e.toUserMessage()}") }
                }
        }
    }

    /**
     * Upload a pre-cropped avatar (PNG bytes from [AvatarCropDialog]).
     * Used by the picker-then-crop flow so the user frames their avatar
     * before it's sent rather than relying on the display layer to
     * square-crop whatever raw image they picked. PNG so a zoomed-out
     * crop keeps its transparent letterbox; the server re-encodes anyway.
     */
    fun uploadAvatarBytes(bytes: ByteArray, fileName: String = "avatar.png") {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingAvatar = true, error = null) }
            runCatching {
                val requestBody = bytes.toRequestBody("image/png".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                api.uploadFile(part, purpose = "avatar")
            }
                .onSuccess { response ->
                    _form.update { it.copy(avatarUrl = response.url) }
                    _state.update { it.copy(isUploadingAvatar = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploadingAvatar = false, error = "Failed to upload avatar: ${e.toUserMessage()}") }
                }
        }
    }

    fun removeAvatar() {
        _form.update { it.copy(avatarUrl = "") }
    }
}
