package systems.lupine.sheaf.ui.journals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.deleteJournalOrQueue
import systems.lupine.sheaf.data.db.LocalCache
import systems.lupine.sheaf.data.model.ContentRevisionRead
import systems.lupine.sheaf.data.model.JournalEntryCreate
import systems.lupine.sheaf.data.model.JournalEntryDeletePending
import systems.lupine.sheaf.data.model.JournalEntryRead
import systems.lupine.sheaf.data.model.JournalEntryReadWithCount
import systems.lupine.sheaf.data.model.JournalEntryUpdate
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.PinRevisionRequest
import systems.lupine.sheaf.data.model.RestoreRevisionRequest
import systems.lupine.sheaf.data.model.UnpinRevisionRequest
import systems.lupine.sheaf.data.network.NetworkMonitor
import systems.lupine.sheaf.ui.components.RevisionSafety
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

// ── List ──────────────────────────────────────────────────────────────────────

enum class JournalFilter { ALL, SYSTEM_ONLY }

data class JournalsUiState(
    val entries: List<JournalEntryRead> = emptyList(),
    val members: Map<String, MemberRead> = emptyMap(),
    val filter: JournalFilter = JournalFilter.ALL,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
)

@HiltViewModel
class JournalsViewModel @Inject constructor(
    private val api: SheafApiService,
    private val cache: LocalCache,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(JournalsUiState(isLoading = true))
    val state: StateFlow<JournalsUiState> = _state.asStateFlow()

    init {
        loadMembers()
        load()
    }

    fun setFilter(filter: JournalFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, entries = emptyList(), nextCursor = null) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.entries.isEmpty(), error = null) }
            val online = networkMonitor.isOnline.first()
            if (!online) {
                val cached = cache.getJournals()
                _state.update {
                    it.copy(
                        entries = cached.orEmpty(),
                        isLoading = false,
                    )
                }
                return@launch
            }
            val filter = _state.value.filter
            runCatching {
                api.listJournals(
                    systemOnly = if (filter == JournalFilter.SYSTEM_ONLY) true else null,
                )
            }
                .onSuccess { resp ->
                    if (filter == JournalFilter.ALL) cache.saveJournals(resp.items)
                    _state.update {
                        it.copy(
                            entries = resp.items,
                            nextCursor = resp.nextCursor,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    val cached = if (filter == JournalFilter.ALL) cache.getJournals() else null
                    if (cached != null) {
                        _state.update { it.copy(entries = cached, isLoading = false) }
                    } else {
                        _state.update { s ->
                            s.copy(
                                isLoading = false,
                                error = if (s.entries.isEmpty()) e.toUserMessage() else s.error,
                            )
                        }
                    }
                }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val filter = _state.value.filter
            runCatching {
                api.listJournals(
                    systemOnly = if (filter == JournalFilter.SYSTEM_ONLY) true else null,
                    before = cursor,
                )
            }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            entries = it.entries + resp.items,
                            nextCursor = resp.nextCursor,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoadingMore = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            runCatching { api.listMembers() }
                .onSuccess { members ->
                    _state.update { it.copy(members = members.associateBy { m -> m.id }) }
                }
        }
    }
}

// ── Detail ────────────────────────────────────────────────────────────────────

data class JournalFormState(
    val title: String = "",
    val body: String = "",
    val memberId: String? = null,
)

data class JournalDetailUiState(
    val entry: JournalEntryReadWithCount? = null,
    val members: List<MemberRead> = emptyList(),
    val revisions: List<ContentRevisionRead> = emptyList(),
    val showRevisions: Boolean = false,
    val pendingDelete: JournalEntryDeletePending? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isRestoring: Boolean = false,
    val isEditing: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val revisionSafety: RevisionSafety = RevisionSafety(),
    val pendingRevisionId: String? = null,
    val pinError: String? = null,
    val unpinQueued: Boolean = false,
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rawId: String? = savedStateHandle["journalId"]
    val isNewEntry: Boolean = rawId == null || rawId == "new"
    private val initialMemberId: String? = savedStateHandle["memberId"]
    private val entryId: String? = if (isNewEntry) null else rawId

    private val _state = MutableStateFlow(
        JournalDetailUiState(
            isLoading = !isNewEntry,
            isEditing = isNewEntry,
        )
    )
    val state: StateFlow<JournalDetailUiState> = _state.asStateFlow()

    private val _form = MutableStateFlow(JournalFormState(memberId = initialMemberId))
    val form: StateFlow<JournalFormState> = _form.asStateFlow()

    init {
        loadMembers()
        if (!isNewEntry && entryId != null) load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching { api.getJournal(entryId!!) }
                .onSuccess { entry ->
                    _state.update { it.copy(entry = entry, isLoading = false) }
                    _form.value = JournalFormState(
                        title = entry.title.orEmpty(),
                        body = entry.body,
                        memberId = entry.memberId,
                    )
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            runCatching { api.listMembers() }
                .onSuccess { members -> _state.update { it.copy(members = members) } }
        }
    }

    fun updateForm(update: JournalFormState.() -> JournalFormState) { _form.update(update) }

    fun startEditing() { _state.update { it.copy(isEditing = true) } }

    fun cancelEditing() {
        if (isNewEntry) {
            _state.update { it.copy(saved = true) }
            return
        }
        val entry = _state.value.entry
        if (entry != null) {
            _form.value = JournalFormState(
                title = entry.title.orEmpty(),
                body = entry.body,
                memberId = entry.memberId,
            )
        }
        _state.update { it.copy(isEditing = false, error = null) }
    }

    fun save() {
        val f = _form.value
        if (f.body.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                if (isNewEntry) {
                    api.createJournal(
                        JournalEntryCreate(
                            body = f.body.trim(),
                            title = f.title.trim().takeIf { it.isNotBlank() },
                            memberId = f.memberId,
                        )
                    )
                } else {
                    api.updateJournal(
                        entryId!!,
                        JournalEntryUpdate(
                            title = f.title.trim().takeIf { it.isNotBlank() } ?: "",
                            body = f.body.trim(),
                        ),
                    )
                }
            }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
                }
        }
    }

    fun delete(password: String? = null, totpCode: String? = null) {
        if (entryId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, error = null) }
            runCatching { api.deleteJournalOrQueue(entryId, password, totpCode) }
                .onSuccess { pending ->
                    if (pending != null) {
                        _state.update {
                            it.copy(isDeleting = false, pendingDelete = pending, deleted = true)
                        }
                    } else {
                        _state.update { it.copy(isDeleting = false, deleted = true) }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isDeleting = false, error = e.toUserMessage()) }
                }
        }
    }

    fun toggleRevisions() {
        val showing = _state.value.showRevisions
        if (showing) {
            _state.update { it.copy(showRevisions = false) }
            return
        }
        if (entryId == null) return
        viewModelScope.launch {
            _state.update { it.copy(showRevisions = true) }
            runCatching { api.listJournalRevisions(entryId) }
                .onSuccess { revs -> _state.update { it.copy(revisions = revs) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage()) } }
        }
    }

    fun restoreRevision(revisionId: String) {
        if (entryId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isRestoring = true, error = null) }
            runCatching { api.restoreJournalRevision(entryId, RestoreRevisionRequest(revisionId)) }
                .onSuccess {
                    _state.update { it.copy(isRestoring = false, showRevisions = false) }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(isRestoring = false, error = e.toUserMessage()) }
                }
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
        if (entryId == null) return
        _state.update { it.copy(pendingRevisionId = revisionId, pinError = null) }
        viewModelScope.launch {
            runCatching { api.pinJournalRevision(entryId, PinRevisionRequest(revisionId)) }
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
        if (entryId == null) return
        _state.update { it.copy(pendingRevisionId = revisionId, pinError = null, unpinQueued = false) }
        viewModelScope.launch {
            runCatching {
                api.unpinJournalRevision(
                    entryId,
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
                    if (resp.pendingActionId != null) {
                        runCatching { api.listJournalRevisions(entryId) }
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
