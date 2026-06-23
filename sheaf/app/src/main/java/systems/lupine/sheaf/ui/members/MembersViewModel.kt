package systems.lupine.sheaf.ui.members

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.deleteMemberOrQueue
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.*
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.ui.components.RevisionSafety
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
    val deleteSafety: MemberDeleteSafety = MemberDeleteSafety(),
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val deleteCompleted: Boolean = false,
    // Archive: set to the member id when the server asked for step-up auth
    // (the system's archive safety category is on); drives the prompt.
    val archiveAuthFor: String? = null,
    val archiveError: String? = null,
    val isArchiving: Boolean = false,
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val api: SheafApiService,
    private val cache: LocalCache,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(MembersUiState(isLoading = true))
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.members.isEmpty(), error = null) }
            val online = networkMonitor.isOnline.first()
            if (online) {
                runCatching {
                    val members = api.listMembers()
                    val fronts = api.getCurrentFronts()
                    cache.saveMembers(members)
                    _state.update { it.copy(members = members, currentFronts = fronts, isLoading = false) }
                }.onFailure { e ->
                    val cached = cache.getMembers()
                    if (cached != null) {
                        _state.update { it.copy(members = cached, isLoading = false) }
                    } else {
                        _state.update { s -> s.copy(isLoading = false, error = if (s.members.isEmpty()) e.toUserMessage() else s.error) }
                    }
                }
            } else {
                val cached = cache.getMembers()
                if (cached != null) {
                    _state.update { it.copy(members = cached, isLoading = false) }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
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

    fun loadDeleteSafety() {
        viewModelScope.launch {
            runCatching {
                val safety = api.getSystemSafety()
                val user = runCatching { api.getMe() }.getOrNull()
                MemberDeleteSafety(
                    authTier = safety.settings.authTier,
                    totpEnabled = user?.totpEnabled == true,
                    appliesToMembers = safety.settings.appliesToMembers,
                    gracePeriodDays = safety.settings.gracePeriodDays,
                )
            }.onSuccess { s -> _state.update { it.copy(deleteSafety = s) } }
        }
    }

    /**
     * Archive a member. Tries with no credentials first; if the system's
     * archive safety category is on the server answers 4xx, and we surface a
     * step-up prompt ([archiveAuthFor]) and retry with the supplied password
     * (+ TOTP). Archiving is reversible, so no extra confirm beyond that.
     */
    fun archiveMember(memberId: String, password: String? = null, totpCode: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isArchiving = true, archiveError = null) }
            runCatching {
                api.archiveMember(
                    memberId,
                    MemberArchiveBody(password?.ifBlank { null }, totpCode?.ifBlank { null }),
                )
            }
                .onSuccess {
                    _state.update { it.copy(isArchiving = false, archiveAuthFor = null) }
                    load()
                }
                .onFailure { e ->
                    if (e is retrofit2.HttpException && e.code() in listOf(400, 403)) {
                        _state.update {
                            it.copy(
                                isArchiving = false,
                                archiveAuthFor = memberId,
                                archiveError = if (password != null) "Incorrect password or authenticator code" else null,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isArchiving = false,
                                archiveAuthFor = null,
                                error = e.toUserMessage("Couldn't archive member"),
                            )
                        }
                    }
                }
        }
    }

    fun unarchiveMember(memberId: String) {
        viewModelScope.launch {
            runCatching { api.unarchiveMember(memberId) }
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't unarchive member")) } }
        }
    }

    fun cancelArchiveAuth() { _state.update { it.copy(archiveAuthFor = null, archiveError = null) } }

    fun deleteMember(memberId: String, password: String? = null, totpCode: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, deleteError = null, deleteCompleted = false) }
            runCatching { api.deleteMemberOrQueue(memberId, password, totpCode) }
                .onSuccess { queued ->
                    if (queued != null) {
                        // Member stays on the server until grace expires; refresh from source.
                        _state.update { it.copy(isDeleting = false, deleteCompleted = true) }
                        load()
                    } else {
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                deleteCompleted = true,
                                members = it.members.filter { m -> m.id != memberId },
                            )
                        }
                    }
                }
                .onFailure { e ->
                    val msg = if (e is retrofit2.HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to delete member")
                    _state.update { it.copy(isDeleting = false, deleteError = msg) }
                }
        }
    }

    fun clearDeleteError() { _state.update { it.copy(deleteError = null) } }
    fun clearDeleteCompleted() { _state.update { it.copy(deleteCompleted = false) } }
}

// ── Bio revision diff helpers ─────────────────────────────────────────────────

enum class BioDiffOp { Equal, Added, Removed }

data class BioDiffLine(val op: BioDiffOp, val text: String)

/**
 * Line-level LCS diff between two text bodies. Mirrors the red/green line
 * diff the web client renders for bio revisions.
 */
fun diffBioLines(oldBody: String, newBody: String): List<BioDiffLine> {
    val old = oldBody.split('\n')
    val new = newBody.split('\n')
    val n = old.size
    val m = new.size
    val lcs = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1
            else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
    }
    val out = ArrayList<BioDiffLine>(n + m)
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            old[i] == new[j] -> { out += BioDiffLine(BioDiffOp.Equal, old[i]); i++; j++ }
            lcs[i + 1][j] >= lcs[i][j + 1] -> { out += BioDiffLine(BioDiffOp.Removed, old[i]); i++ }
            else -> { out += BioDiffLine(BioDiffOp.Added, new[j]); j++ }
        }
    }
    while (i < n) { out += BioDiffLine(BioDiffOp.Removed, old[i]); i++ }
    while (j < m) { out += BioDiffLine(BioDiffOp.Added, new[j]); j++ }
    return out
}

// ── Detail / create / edit ────────────────────────────────────────────────────

data class MemberFormState(
    val name: String = "",
    val displayName: String = "",
    val pronouns: String = "",
    val description: String = "",
    val note: String = "",
    val color: String = "#7F77DD",
    val birthday: String = "",
    val privacy: String = "private",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
)

data class MemberDetailUiState(
    val member: MemberRead? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val deleteSafety: MemberDeleteSafety = MemberDeleteSafety(),
    val deleteError: String? = null,
    val deleteQueued: Boolean = false,
    val isArchiving: Boolean = false,
    // True when the server demanded step-up auth to archive (the system's
    // archive safety category is on); drives the prompt on the edit screen.
    val archiveNeedsAuth: Boolean = false,
    val archiveError: String? = null,
    /** Definitions for every custom field on the system. Loaded alongside
     *  the member so the form can render type-appropriate editors. Order
     *  follows the user's pick in Settings → Custom Fields. */
    val customFields: List<systems.lupine.sheaf.data.model.CustomFieldRead> = emptyList(),
    /** Per-field current value, keyed by field id. Server side the value
     *  is type-erased (Any?) — Boolean, String, Number, or List<String>
     *  depending on the field's type. Edits live here until save. */
    val customFieldValues: Map<String, Any?> = emptyMap(),
    /** Snapshot of customFieldValues at load time so save can diff and
     *  only PUT what actually changed. Avoids re-sending all values on
     *  every save (server tolerates it, but a no-op write still rotates
     *  audit history and encryption ciphertexts). */
    val customFieldValuesBaseline: Map<String, Any?> = emptyMap(),
)

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context,
    val markdownImages: systems.lupine.sheaf.ui.components.MarkdownImageDelegate,
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

    // Snapshot of the form as last loaded (or the empty initial for a new
    // member). The edit screen compares against this to know whether there
    // are unsaved changes before letting the user navigate away.
    private val _baselineForm = MutableStateFlow(MemberFormState())
    val baselineForm: StateFlow<MemberFormState> = _baselineForm.asStateFlow()

    init {
        markdownImages.loadUser(viewModelScope)
        // Field definitions are needed for both create and edit modes —
        // a new member can have field values set before its first save
        // by composing the values into the form, then we replay them
        // after createMember mints an id. (Create-flow value-stash is a
        // small follow-up; for now we load definitions so the form can
        // at least show the field rows.)
        loadCustomFieldDefinitions()
        if (!isNewMember && memberId != null) loadMember()
    }

    private fun loadCustomFieldDefinitions() {
        viewModelScope.launch {
            runCatching { api.listFields() }
                .onSuccess { defs ->
                    _state.update { it.copy(customFields = defs) }
                }
        }
    }

    private fun loadMember() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { api.getMember(memberId!!) }
                .onSuccess { m ->
                    _state.update { it.copy(member = m, isLoading = false) }
                    val loaded = MemberFormState(
                        name        = m.name,
                        displayName = m.displayName ?: "",
                        pronouns    = m.pronouns ?: "",
                        description = m.description ?: "",
                        note        = m.note ?: "",
                        color       = m.color ?: "#7F77DD",
                        birthday    = m.birthday ?: "",
                        privacy     = m.privacy,
                        avatarUrl   = m.avatarUrl,
                        bannerUrl   = m.bannerUrl,
                    )
                    _form.value = loaded
                    _baselineForm.value = loaded
                    // Pull current field values for this member after the
                    // base member load so the editor has something to
                    // populate against. Best-effort: if it fails the
                    // editor still renders, just with empty values.
                    runCatching { api.getMemberFieldValues(memberId!!) }
                        .onSuccess { values ->
                            val byId = values.associate { it.fieldId to it.value }
                            _state.update {
                                it.copy(
                                    customFieldValues = byId,
                                    customFieldValuesBaseline = byId,
                                )
                            }
                        }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    /** Stage a new value for the named field. Save flushes the diff to
     *  the server alongside the member's name/avatar/etc. */
    fun setCustomFieldValue(fieldId: String, value: Any?) {
        _state.update {
            it.copy(customFieldValues = it.customFieldValues + (fieldId to value))
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
                val savedMember = if (isNewMember) {
                    api.createMember(MemberCreate(
                        name        = f.name.trim(),
                        displayName = f.displayName.takeIf { it.isNotBlank() },
                        pronouns    = f.pronouns.takeIf { it.isNotBlank() },
                        description = f.description.takeIf { it.isNotBlank() },
                        avatarUrl   = f.avatarUrl,
                        bannerUrl   = f.bannerUrl,
                        color       = f.color.takeIf { it.isNotBlank() },
                        birthday    = f.birthday.takeIf { it.isNotBlank() },
                        privacy     = f.privacy,
                        note        = f.note.takeIf { it.isNotBlank() },
                    ))
                } else {
                    val update = MemberUpdate(
                        name        = f.name.trim(),
                        displayName = f.displayName.takeIf { it.isNotBlank() },
                        pronouns    = f.pronouns.takeIf { it.isNotBlank() },
                        description = f.description.takeIf { it.isNotBlank() },
                        avatarUrl   = f.avatarUrl,
                        bannerUrl   = f.bannerUrl,
                        color       = f.color.takeIf { it.isNotBlank() },
                        birthday    = f.birthday.takeIf { it.isNotBlank() },
                        privacy     = f.privacy,
                        // Empty string clears the column server-side; this lets
                        // a user wipe a note that was previously set.
                        note        = f.note,
                    )
                    val body = moshi.adapter(MemberUpdate::class.java).serializeNulls()
                        .toJson(update)
                        .toRequestBody("application/json".toMediaTypeOrNull()!!)
                    api.patchMemberRaw(memberId!!, body)
                }
                // Flush custom field values for the member. Diff against
                // the load-time baseline so an unchanged field doesn't
                // re-rotate its server-side ciphertext or audit row.
                // For new members, baseline is empty so every staged
                // value goes through.
                val cur = _state.value
                val targetMemberId = memberId ?: savedMember.id
                val diff = cur.customFieldValues.entries
                    .filter { (id, v) -> cur.customFieldValuesBaseline[id] != v }
                    .map { (id, v) ->
                        systems.lupine.sheaf.data.model.CustomFieldValueSet(
                            fieldId = id,
                            value = v,
                        )
                    }
                if (diff.isNotEmpty()) {
                    api.setMemberFieldValues(targetMemberId, diff)
                }
            }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = e.toUserMessage()) } }
        }
    }

    fun loadDeleteSafety() {
        viewModelScope.launch {
            runCatching {
                val safety = api.getSystemSafety()
                val user = runCatching { api.getMe() }.getOrNull()
                MemberDeleteSafety(
                    authTier = safety.settings.authTier,
                    totpEnabled = user?.totpEnabled == true,
                    appliesToMembers = safety.settings.appliesToMembers,
                    gracePeriodDays = safety.settings.gracePeriodDays,
                )
            }.onSuccess { s -> _state.update { it.copy(deleteSafety = s) } }
        }
    }

    /**
     * Archive this member (reversible soft-hide). Tries without credentials;
     * if the system's archive safety category is on the server answers 4xx
     * and we surface a step-up prompt and retry. Updates the member in place
     * so the button flips to Unarchive.
     */
    fun archiveMember(password: String? = null, totpCode: String? = null) {
        val id = memberId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isArchiving = true, archiveError = null) }
            runCatching {
                api.archiveMember(id, MemberArchiveBody(password?.ifBlank { null }, totpCode?.ifBlank { null }))
            }
                .onSuccess { m -> _state.update { it.copy(isArchiving = false, archiveNeedsAuth = false, member = m) } }
                .onFailure { e ->
                    if (e is retrofit2.HttpException && e.code() in listOf(400, 403)) {
                        _state.update {
                            it.copy(
                                isArchiving = false,
                                archiveNeedsAuth = true,
                                archiveError = if (password != null) "Incorrect password or authenticator code" else null,
                            )
                        }
                    } else {
                        _state.update { it.copy(isArchiving = false, archiveNeedsAuth = false, error = e.toUserMessage("Couldn't archive member")) }
                    }
                }
        }
    }

    fun unarchiveMember() {
        val id = memberId ?: return
        viewModelScope.launch {
            runCatching { api.unarchiveMember(id) }
                .onSuccess { m -> _state.update { it.copy(member = m) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't unarchive member")) } }
        }
    }

    fun cancelArchiveAuth() { _state.update { it.copy(archiveNeedsAuth = false, archiveError = null) } }

    fun delete(password: String? = null, totpCode: String? = null) {
        if (memberId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, deleteError = null, deleteQueued = false) }
            runCatching { api.deleteMemberOrQueue(memberId, password, totpCode) }
                .onSuccess { queued ->
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            deleted = true,
                            deleteQueued = queued != null,
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is retrofit2.HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to delete member")
                    _state.update { it.copy(isDeleting = false, deleteError = msg) }
                }
        }
    }

    fun clearDeleteError() { _state.update { it.copy(deleteError = null) } }

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
        _form.update { it.copy(avatarUrl = null) }
    }

    /**
     * Upload a pre-cropped banner (PNG bytes from [BannerCropDialog]).
     * Banners are wide (3:1) header images on the member profile; the
     * upload reuses the same files endpoint as avatars, tagged
     * purpose=banner so the server stores it in the banners prefix.
     */
    fun uploadBannerBytes(bytes: ByteArray, fileName: String = "banner.png") {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingBanner = true, error = null) }
            runCatching {
                val requestBody = bytes.toRequestBody("image/png".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                api.uploadFile(part, purpose = "banner")
            }
                .onSuccess { response ->
                    _form.update { it.copy(bannerUrl = response.url) }
                    _state.update { it.copy(isUploadingBanner = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isUploadingBanner = false, error = "Failed to upload banner: ${e.toUserMessage()}") }
                }
        }
    }

    fun removeBanner() {
        _form.update { it.copy(bannerUrl = null) }
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
    val deleteSafety: MemberDeleteSafety = MemberDeleteSafety(),
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val deleteQueued: Boolean = false,
    val revisions: List<ContentRevisionRead> = emptyList(),
    val showRevisions: Boolean = false,
    val isLoadingRevisions: Boolean = false,
    val isRestoring: Boolean = false,
    val revisionSafety: RevisionSafety = RevisionSafety(),
    val pendingRevisionId: String? = null,
    val pinError: String? = null,
    val unpinQueued: Boolean = false,
    /** System-level field definitions, fetched once. */
    val customFields: List<systems.lupine.sheaf.data.model.CustomFieldRead> = emptyList(),
    /** This member's current values, keyed by field id. Fields not in
     *  the map are unset (display as em-dash). Fields the viewer isn't
     *  allowed to see are absent because the server omitted them. */
    val customFieldValues: Map<String, Any?> = emptyMap(),
)

@HiltViewModel
class MemberProfileViewModel @Inject constructor(
    private val api: SheafApiService,
    private val cache: LocalCache,
    private val networkMonitor: NetworkMonitor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val memberId: String = checkNotNull(savedStateHandle["memberId"])

    private val _state = MutableStateFlow(MemberProfileUiState(isLoading = true))
    val state: StateFlow<MemberProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (_state.value.member == null) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            val online = networkMonitor.isOnline.first()
            if (online) {
                runCatching {
                    val member = api.getMember(memberId)
                    val fronts = api.getCurrentFronts()
                    // Custom fields are best-effort — a viewer with no
                    // visibility into any of this member's fields gets
                    // an empty list and we render nothing. Failure to
                    // load either doesn't block the rest of the profile.
                    val defs = runCatching { api.listFields() }.getOrDefault(emptyList())
                    val vals = runCatching { api.getMemberFieldValues(memberId) }
                        .getOrDefault(emptyList())
                        .associate { it.fieldId to it.value }
                    _state.update {
                        it.copy(
                            member = member,
                            currentFronts = fronts,
                            customFields = defs,
                            customFieldValues = vals,
                            isLoading = false,
                        )
                    }
                }.onFailure { e ->
                    val cached = cache.getMember(memberId)
                    val fronts = cache.getFronts() ?: emptyList()
                    if (cached != null) {
                        _state.update { it.copy(member = cached, currentFronts = fronts, isLoading = false) }
                    } else {
                        _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                    }
                }
            } else {
                val cached = cache.getMember(memberId)
                val fronts = cache.getFronts() ?: emptyList()
                if (cached != null) {
                    _state.update { it.copy(member = cached, currentFronts = fronts, isLoading = false) }
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            }
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

    fun loadDeleteSafety() {
        viewModelScope.launch {
            runCatching {
                val safety = api.getSystemSafety()
                val user = runCatching { api.getMe() }.getOrNull()
                MemberDeleteSafety(
                    authTier = safety.settings.authTier,
                    totpEnabled = user?.totpEnabled == true,
                    appliesToMembers = safety.settings.appliesToMembers,
                    gracePeriodDays = safety.settings.gracePeriodDays,
                )
            }.onSuccess { s -> _state.update { it.copy(deleteSafety = s) } }
        }
    }

    fun delete(password: String? = null, totpCode: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, deleteError = null, deleteQueued = false) }
            runCatching { api.deleteMemberOrQueue(memberId, password, totpCode) }
                .onSuccess { queued ->
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            deleted = true,
                            deleteQueued = queued != null,
                        )
                    }
                }
                .onFailure { e ->
                    val msg = if (e is retrofit2.HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to delete member")
                    _state.update { it.copy(isDeleting = false, deleteError = msg) }
                }
        }
    }

    fun clearDeleteError() { _state.update { it.copy(deleteError = null) } }

    fun toggleRevisions() {
        val showing = _state.value.showRevisions
        if (showing) {
            _state.update { it.copy(showRevisions = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(showRevisions = true, isLoadingRevisions = true) }
            runCatching { api.listMemberBioRevisions(memberId) }
                .onSuccess { revs -> _state.update { it.copy(revisions = revs, isLoadingRevisions = false) } }
                .onFailure { e -> _state.update { it.copy(isLoadingRevisions = false, error = e.toUserMessage()) } }
        }
    }

    fun restoreRevision(revisionId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isRestoring = true) }
            runCatching { api.restoreMemberBioRevision(memberId, RestoreRevisionRequest(revisionId)) }
                .onSuccess { restored ->
                    _state.update {
                        it.copy(
                            isRestoring = false,
                            showRevisions = false,
                            member = restored,
                        )
                    }
                    // Re-fetch revisions list so the captured "before" snapshot appears.
                    runCatching { api.listMemberBioRevisions(memberId) }
                        .onSuccess { revs -> _state.update { it.copy(revisions = revs) } }
                }
                .onFailure { e -> _state.update { it.copy(isRestoring = false, error = e.toUserMessage()) } }
        }
    }

    fun loadRevisionSafety() {
        viewModelScope.launch {
            runCatching {
                val safety = api.getSystemSafety()
                val user = runCatching { api.getMe() }.getOrNull()
                RevisionSafety(
                    authTier = safety.settings.authTier,
                    totpEnabled = user?.totpEnabled == true,
                    appliesToRevisions = safety.settings.appliesToRevisions,
                    gracePeriodDays = safety.settings.gracePeriodDays,
                )
            }.onSuccess { s -> _state.update { it.copy(revisionSafety = s) } }
        }
    }

    fun pinRevision(revisionId: String) {
        _state.update { it.copy(pendingRevisionId = revisionId, pinError = null) }
        viewModelScope.launch {
            runCatching { api.pinMemberBioRevision(memberId, PinRevisionRequest(revisionId)) }
                .onSuccess { updated ->
                    _state.update { st ->
                        st.copy(
                            pendingRevisionId = null,
                            revisions = st.revisions.map { if (it.id == updated.id) updated else it },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            pendingRevisionId = null,
                            pinError = e.toUserMessage("Failed to pin revision"),
                        )
                    }
                }
        }
    }

    fun unpinRevision(revisionId: String, password: String? = null, totpCode: String? = null) {
        _state.update { it.copy(pendingRevisionId = revisionId, pinError = null, unpinQueued = false) }
        viewModelScope.launch {
            runCatching {
                api.unpinMemberBioRevision(
                    memberId,
                    UnpinRevisionRequest(revisionId, password?.ifBlank { null }, totpCode?.ifBlank { null }),
                )
            }
                .onSuccess { resp ->
                    val updated = resp.revision
                    _state.update { st ->
                        val nextRevisions = if (updated != null) {
                            st.revisions.map { if (it.id == updated.id) updated else it }
                        } else st.revisions
                        st.copy(
                            pendingRevisionId = null,
                            revisions = nextRevisions,
                            unpinQueued = resp.pendingActionId != null,
                        )
                    }
                    // Refresh from source so pending unpins reflect server-side state.
                    if (resp.pendingActionId != null) {
                        runCatching { api.listMemberBioRevisions(memberId) }
                            .onSuccess { revs -> _state.update { it.copy(revisions = revs) } }
                    }
                }
                .onFailure { e ->
                    val msg = if (e is retrofit2.HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else
                        e.toUserMessage("Failed to unpin revision")
                    _state.update { it.copy(pendingRevisionId = null, pinError = msg) }
                }
        }
    }

    fun clearPinError() { _state.update { it.copy(pinError = null) } }
    fun clearUnpinQueued() { _state.update { it.copy(unpinQueued = false) } }
}
