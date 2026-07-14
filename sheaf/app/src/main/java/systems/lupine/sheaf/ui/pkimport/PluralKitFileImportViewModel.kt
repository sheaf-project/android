package systems.lupine.sheaf.ui.pkimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.streamingFilePart
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobSource
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.data.model.PKImportResult
import systems.lupine.sheaf.data.model.PKPreviewSummary
import systems.lupine.sheaf.util.toUserMessage
import java.util.UUID
import javax.inject.Inject

data class PKFileImportOptions(
    val systemProfile: Boolean = true,
    val selectedMemberIds: Set<String>? = null, // null = all
    val groups: Boolean = true,
    val frontHistory: Boolean = false, // backend default — switch logs can be huge
)

data class PKFileImportUiState(
    val fileName: String? = null,
    val isPreviewing: Boolean = false,
    val preview: PKPreviewSummary? = null,
    val options: PKFileImportOptions = PKFileImportOptions(),
    val isImporting: Boolean = false,
    val result: PKImportResult? = null,
    val error: String? = null,
)

/**
 * PluralKit file import. Same async-submit-then-poll shape as the SP and
 * Sheaf importers; see [systems.lupine.sheaf.ui.importsp.ImportViewModel]
 * for the poll-loop rationale.
 */
@HiltViewModel
class PluralKitFileImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PKFileImportUiState())
    val state: StateFlow<PKFileImportUiState> = _state.asStateFlow()

    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        idempotencyKey = null
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            fileUri = uri
            val name = resolveFileName(uri) ?: "export.json"
            cachedFileName = name
            _state.update { it.copy(fileName = name) }
            preview(uri, name)
        }
    }

    private suspend fun preview(uri: Uri, name: String) {
        runCatching { api.previewPluralKitFileImport(filePart(uri, name)) }
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        preview = summary,
                        options = PKFileImportOptions(
                            systemProfile = summary.systemName != null,
                            selectedMemberIds = null,
                            groups = summary.groupCount > 0,
                            frontHistory = false,  // user opts in explicitly
                        ),
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        error = e.toUserMessage("Preview failed — check the file and try again"),
                    )
                }
            }
    }

    fun updateOptions(update: PKFileImportOptions.() -> PKFileImportOptions) {
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
    // Reset when a new file is picked or the job reaches a terminal state.
    private var idempotencyKey: String? = null

    private fun nextIdempotencyKey(): String =
        idempotencyKey ?: UUID.randomUUID().toString().also { idempotencyKey = it }

    fun runImport() {
        val uri = fileUri ?: return
        val name = cachedFileName ?: "export.json"
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
                val job = api.createFileImport(
                    file = filePart(uri, name),
                    source = ImportJobSource.PLURALKIT_FILE.toFormPart(),
                    idempotencyKey = nextIdempotencyKey().toFormPart(),
                    options = buildPkOptionsJson(opts, narrowedMemberIds).toJsonPart(),
                )
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
        fileUri = null
        cachedFileName = null
        _state.value = PKFileImportUiState()
    }

    private fun filePart(uri: Uri, name: String) =
        streamingFilePart(context.contentResolver, uri, name)

    private fun String.toFormPart(): RequestBody =
        toRequestBody("text/plain".toMediaType())

    private fun String.toJsonPart(): RequestBody =
        toRequestBody("application/json".toMediaType())

    private fun resolveFileName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(col)
        }
    }.getOrNull()

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1500
    }
}

/**
 * Hand-built options JSON for both PK paths (file + API). Backend uses
 * extra=forbid on PKImportOptions; spelling fields out at the call site
 * keeps the contract visible.
 */
internal fun buildPkOptionsJson(opts: PKFileImportOptions, memberIds: List<String>?): String {
    val parts = mutableListOf<String>()
    parts += "\"system_profile\":${opts.systemProfile}"
    parts += "\"groups\":${opts.groups}"
    parts += "\"front_history\":${opts.frontHistory}"
    if (memberIds != null) {
        val ids = memberIds.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        parts += "\"member_ids\":[$ids]"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
