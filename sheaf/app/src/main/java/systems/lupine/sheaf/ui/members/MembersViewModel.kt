package systems.lupine.sheaf.ui.members

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import systems.lupine.sheaf.util.toUserMessage
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject

// ── List ──────────────────────────────────────────────────────────────────────

data class MembersUiState(
    val members: List<MemberRead> = emptyList(),
    val currentFronts: List<FrontRead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(MembersUiState(isLoading = true))
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.members.isEmpty(), error = null) }
            runCatching {
                val members = api.listMembers()
                val fronts = api.getCurrentFronts()
                _state.update { it.copy(members = members, currentFronts = fronts, isLoading = false) }
            }.onFailure { e ->
                _state.update { s -> s.copy(isLoading = false, error = if (s.members.isEmpty()) e.toUserMessage() else s.error) }
            }
        }
    }

    fun addToFront(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching {
                val activeFront = _state.value.currentFronts.firstOrNull()
                if (activeFront != null) {
                    api.updateFront(activeFront.id, FrontUpdate(memberIds = activeFront.memberIds + memberId))
                } else {
                    api.createFront(FrontCreate(memberIds = listOf(memberId), startedAt = Instant.now().toString()))
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
                return@launch
            }
            _state.update { it.copy(currentFronts = api.runCatching { getCurrentFronts() }.getOrElse { _state.value.currentFronts }) }
        }
    }

    fun removeFromFront(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching {
                _state.value.currentFronts.filter { memberId in it.memberIds }.forEach { front ->
                    if (front.memberIds.size == 1) {
                        api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                    } else {
                        api.updateFront(front.id, FrontUpdate(memberIds = front.memberIds - memberId))
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
                return@launch
            }
            _state.update { it.copy(currentFronts = api.runCatching { getCurrentFronts() }.getOrElse { _state.value.currentFronts }) }
        }
    }

    fun switchSoleFronter(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching {
                _state.value.currentFronts.forEach { front ->
                    api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                }
                api.createFront(FrontCreate(memberIds = listOf(memberId), startedAt = Instant.now().toString()))
            }.onFailure { e ->
                _state.update { it.copy(error = e.toUserMessage()) }
                return@launch
            }
            _state.update { it.copy(currentFronts = api.runCatching { getCurrentFronts() }.getOrElse { _state.value.currentFronts }) }
        }
    }

    fun deleteMember(memberId: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching { api.deleteMember(memberId) }
                .onSuccess { _state.update { it.copy(members = it.members.filter { m -> m.id != memberId }) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }
}

// ── Detail / create / edit ────────────────────────────────────────────────────

data class MemberFormState(
    val name: String = "",
    val displayName: String = "",
    val pronouns: String = "",
    val description: String = "",
    val color: String = "#7F77DD",
    val birthday: String = "",
    val privacy: String = "private",
    val avatarUrl: String? = null,
)

data class MemberDetailUiState(
    val member: MemberRead? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // "new" means create mode; any UUID means edit mode
    private val rawId: String? = savedStateHandle["memberId"]
    val isNewMember: Boolean = rawId == null || rawId == "new"
    private val memberId: String? = if (isNewMember) null else rawId

    private val _state = MutableStateFlow(MemberDetailUiState(isLoading = !isNewMember))
    val state: StateFlow<MemberDetailUiState> = _state.asStateFlow()

    private val _form = MutableStateFlow(MemberFormState())
    val form: StateFlow<MemberFormState> = _form.asStateFlow()

    init {
        if (!isNewMember && memberId != null) loadMember()
    }

    private fun loadMember() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { api.getMember(memberId!!) }
                .onSuccess { m ->
                    _state.update { it.copy(member = m, isLoading = false) }
                    _form.value = MemberFormState(
                        name        = m.name,
                        displayName = m.displayName ?: "",
                        pronouns    = m.pronouns ?: "",
                        description = m.description ?: "",
                        color       = m.color ?: "#7F77DD",
                        birthday    = m.birthday ?: "",
                        privacy     = m.privacy,
                        avatarUrl   = m.avatarUrl,
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun updateForm(update: MemberFormState.() -> MemberFormState) {
        _form.update(update)
    }

    fun save() {
        val f = _form.value
        if (f.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                if (isNewMember) {
                    api.createMember(MemberCreate(
                        name        = f.name.trim(),
                        displayName = f.displayName.takeIf { it.isNotBlank() },
                        pronouns    = f.pronouns.takeIf { it.isNotBlank() },
                        description = f.description.takeIf { it.isNotBlank() },
                        avatarUrl   = f.avatarUrl,
                        color       = f.color.takeIf { it.isNotBlank() },
                        birthday    = f.birthday.takeIf { it.isNotBlank() },
                        privacy     = f.privacy,
                    ))
                } else {
                    val update = MemberUpdate(
                        name        = f.name.trim(),
                        displayName = f.displayName.takeIf { it.isNotBlank() },
                        pronouns    = f.pronouns.takeIf { it.isNotBlank() },
                        description = f.description.takeIf { it.isNotBlank() },
                        avatarUrl   = f.avatarUrl,
                        color       = f.color.takeIf { it.isNotBlank() },
                        birthday    = f.birthday.takeIf { it.isNotBlank() },
                        privacy     = f.privacy,
                    )
                    val body = moshi.adapter(MemberUpdate::class.java).serializeNulls()
                        .toJson(update)
                        .toRequestBody("application/json".toMediaTypeOrNull()!!)
                    api.patchMemberRaw(memberId!!, body)
                }
            }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.toUserMessage()) } }
        }
    }

    fun delete() {
        if (memberId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, error = null) }
            runCatching { api.deleteMember(memberId) }
                .onSuccess { _state.update { it.copy(isDeleting = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(isDeleting = false, error = e.toUserMessage()) } }
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

    fun removeAvatar() {
        _form.update { it.copy(avatarUrl = null) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}

// ── Profile ───────────────────────────────────────────────────────────────────

data class MemberProfileUiState(
    val member: MemberRead? = null,
    val currentFronts: List<FrontRead> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class MemberProfileViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val memberId: String = checkNotNull(savedStateHandle["memberId"])

    private val _state = MutableStateFlow(MemberProfileUiState(isLoading = true))
    val state: StateFlow<MemberProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            // Keep existing data visible on refresh; only show spinner on first load
            if (_state.value.member == null) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            runCatching {
                val member = api.getMember(memberId)
                val fronts = api.getCurrentFronts()
                _state.update { it.copy(member = member, currentFronts = fronts, isLoading = false) }
            }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }

    fun addToFront() {
        viewModelScope.launch {
            runCatching {
                val active = _state.value.currentFronts.firstOrNull()
                if (active != null) {
                    api.updateFront(active.id, FrontUpdate(memberIds = active.memberIds + memberId))
                } else {
                    api.createFront(FrontCreate(memberIds = listOf(memberId), startedAt = Instant.now().toString()))
                }
            }.onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) }
                return@launch
            }
            _state.update { it.copy(currentFronts = api.runCatching { getCurrentFronts() }.getOrElse { _state.value.currentFronts }) }
        }
    }

    fun removeFromFront() {
        viewModelScope.launch {
            runCatching {
                _state.value.currentFronts.filter { memberId in it.memberIds }.forEach { front ->
                    if (front.memberIds.size == 1) {
                        api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                    } else {
                        api.updateFront(front.id, FrontUpdate(memberIds = front.memberIds - memberId))
                    }
                }
            }.onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) }
                return@launch
            }
            _state.update { it.copy(currentFronts = api.runCatching { getCurrentFronts() }.getOrElse { _state.value.currentFronts }) }
        }
    }

    fun switchSoleFronter() {
        viewModelScope.launch {
            runCatching {
                _state.value.currentFronts.forEach { front ->
                    api.updateFront(front.id, FrontUpdate(endedAt = Instant.now().toString()))
                }
                api.createFront(FrontCreate(memberIds = listOf(memberId), startedAt = Instant.now().toString()))
            }.onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) }
                return@launch
            }
            _state.update { it.copy(currentFronts = api.runCatching { getCurrentFronts() }.getOrElse { _state.value.currentFronts }) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching { api.deleteMember(memberId) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }
}
