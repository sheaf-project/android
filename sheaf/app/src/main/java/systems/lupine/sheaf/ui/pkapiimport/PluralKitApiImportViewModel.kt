package systems.lupine.sheaf.ui.pkapiimport

import systems.lupine.sheaf.ui.importcommon.jsonQuote
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobSource
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.data.model.PKApiPreviewBody
import systems.lupine.sheaf.data.model.PKImportResult
import systems.lupine.sheaf.data.model.PKPreviewSummary
import systems.lupine.sheaf.util.toUserMessage
import java.util.UUID
import javax.inject.Inject

data class PKApiImportOptions(
    val systemProfile: Boolean = true,
    val selectedMemberIds: Set<String>? = null,
    val groups: Boolean = true,
    val frontHistory: Boolean = false,
)

data class PKApiImportUiState(
    val token: String = "",
    val isPreviewing: Boolean = false,
    val preview: PKPreviewSummary? = null,
    val options: PKApiImportOptions = PKApiImportOptions(),
    val isImporting: Boolean = false,
    val result: PKImportResult? = null,
    val error: String? = null,
)

/**
 * Live-API PluralKit import. User enters a PK token; preview hits the
 * PK API server-side (token is request-scoped, never persisted in
 * preview). On submit, the token rides along to /v1/imports/api, where
 * the backend encrypts it at rest while the job runs and wipes it on
 * finalize.
 *
 * The token lives in viewmodel state for the duration of this screen
 * only — not in DataStore, not anywhere persistent. If the user
 * navigates away, the token goes with the viewmodel.
 */
@HiltViewModel
class PluralKitApiImportViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(PKApiImportUiState())
    val state: StateFlow<PKApiImportUiState> = _state.asStateFlow()

    fun updateToken(token: String) {
        _state.update { it.copy(token = token, error = null) }
    }

    fun runPreview() {
        val token = _state.value.token.trim()
        if (token.isEmpty()) {
            _state.update { it.copy(error = "Enter your PluralKit token first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            runCatching { api.previewPluralKitApiImport(PKApiPreviewBody(token)) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            isPreviewing = false,
                            preview = summary,
                            options = PKApiImportOptions(
                                systemProfile = summary.systemName != null,
                                groups = summary.groupCount > 0,
                                frontHistory = false,
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isPreviewing = false,
                            error = e.toUserMessage("Couldn't reach PluralKit. Check your token and try again."),
                        )
                    }
                }
        }
    }

    fun updateOptions(update: PKApiImportOptions.() -> PKApiImportOptions) {
        _state.update { it.copy(options = it.options.update()) }
    }

    fun toggleMember(id: String) {
        val preview = _state.value.preview ?: return
        val current = _state.value.options.selectedMemberIds
            ?: preview.members.map { it.id }.toSet()
        val updated = if (id in current) current - id else current + id
        _state.update { it.copy(options = it.options.copy(selectedMemberIds = updated)) }
    }

    // Stable across retries of the same import attempt. If the job was created
    // but polling then failed, the retry must not spawn a second import: reusing
    // the key lets the server return the existing job instead of creating another.
    // Reset once the job reaches a terminal state, so the next run is a new import.
    private var idempotencyKey: String? = null

    private fun nextIdempotencyKey(): String =
        idempotencyKey ?: UUID.randomUUID().toString().also { idempotencyKey = it }

    fun runImport() {
        val token = _state.value.token.trim().takeIf { it.isNotEmpty() } ?: return
        val opts = _state.value.options
        val narrowedMemberIds = _state.value.preview?.members
            ?.map { it.id }
            ?.let { all ->
                val selected = opts.selectedMemberIds
                if (selected == null || selected.containsAll(all)) null else selected.toList()
            }

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching {
                val body = buildApiImportBodyJson(
                    token = token,
                    idempotencyKey = nextIdempotencyKey(),
                    options = buildPkApiOptionsJson(opts, narrowedMemberIds),
                )
                val job = api.createApiImport(body.toRequestBody("application/json".toMediaType()))
                pollUntilTerminal(job)
            }
                .onSuccess { final -> handleTerminal(final) }
                .onFailure { e ->
                    _state.update { it.copy(isImporting = false, error = e.toUserMessage("Import failed — please try again")) }
                }
        }
    }

    private suspend fun pollUntilTerminal(initial: ImportJobRead): ImportJobRead {
        var current = initial
        while (current.status !in ImportJobStatus.terminal) {
            delay(POLL_INTERVAL_MS)
            current = api.getImportJob(current.id)
        }
        return current
    }

    private fun handleTerminal(job: ImportJobRead) {
        idempotencyKey = null
        when (job.status) {
            ImportJobStatus.COMPLETE -> {
                val counts = job.counts
                val warnings = job.events
                    .filter { it.level == "warning" }
                    .map { e -> e.recordRef?.let { "$it: ${e.message}" } ?: e.message }
                val result = PKImportResult(
                    membersImported = counts["members_imported"] ?: 0,
                    groupsImported = counts["groups_imported"] ?: 0,
                    frontsImported = counts["fronts_imported"] ?: 0,
                    warnings = warnings,
                )
                _state.update { it.copy(isImporting = false, result = result) }
            }
            else -> _state.update {
                it.copy(
                    isImporting = false,
                    error = job.lastError ?: "Import didn't complete (status: ${job.status})",
                )
            }
        }
    }

    fun reset() {
        _state.value = PKApiImportUiState()
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1500
    }
}

/** PK API options JSON. Same shape as PK file options; kept separate to
 *  avoid a cross-package dependency between the two importer modules. */
internal fun buildPkApiOptionsJson(opts: PKApiImportOptions, memberIds: List<String>?): String {
    val parts = mutableListOf<String>()
    parts += "\"system_profile\":${opts.systemProfile}"
    parts += "\"groups\":${opts.groups}"
    parts += "\"front_history\":${opts.frontHistory}"
    if (memberIds != null) {
        val ids = memberIds.joinToString(",") { jsonQuote(it) }
        parts += "\"member_ids\":[$ids]"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}

/**
 * Hand-build the credential-API submit body. The options field is
 * embedded as a raw JSON object (already-encoded). Mirrors the
 * server-side `ImportApiCreateRequest` shape: `source`,
 * `idempotency_key`, `pk_token`, `options`.
 */
internal fun buildApiImportBodyJson(
    token: String,
    idempotencyKey: String,
    options: String,
): String {
    // Token only needs minimal escaping — PK tokens are URL-safe base64-ish
    // strings that wouldn't normally contain quotes or backslashes, but
    // we still escape defensively against a pasted token with whitespace
    // or odd characters.
    return """{"source":"${ImportJobSource.PLURALKIT_API}",""" +
        """"idempotency_key":"$idempotencyKey",""" +
        """"pk_token":${jsonQuote(token)},""" +
        """"options":$options}"""
}
