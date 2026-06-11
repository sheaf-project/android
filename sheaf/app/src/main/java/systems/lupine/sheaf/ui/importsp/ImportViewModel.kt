package systems.lupine.sheaf.ui.importsp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.streamingFilePart
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobSource
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.data.model.SPImportResult
import systems.lupine.sheaf.data.model.SPPreviewSummary
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject

data class ImportOptions(
    val systemProfile: Boolean = true,
    val selectedMemberIds: Set<String>? = null, // null = all
    val customFronts: Boolean = true,
    val customFields: Boolean = true,
    val groups: Boolean = true,
    val frontHistory: Boolean = true,
)

data class ImportUiState(
    val fileName: String? = null,
    val isPreviewing: Boolean = false,
    val preview: SPPreviewSummary? = null,
    val options: ImportOptions = ImportOptions(),
    val isImporting: Boolean = false,
    val result: SPImportResult? = null,
    val error: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    // Holds the picked file URI across the preview -> import flow; the upload
    // streams from it rather than buffering the whole file in memory.
    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
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
        runCatching { api.previewSimplyPluralImport(filePart(uri, name)) }
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        preview = summary,
                        options = ImportOptions(
                            systemProfile = summary.systemName != null,
                            selectedMemberIds = null,
                            customFronts = summary.customFrontCount > 0,
                            customFields = summary.customFieldCount > 0,
                            groups = summary.groupCount > 0,
                            frontHistory = summary.frontHistoryCount > 0,
                        ),
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(isPreviewing = false, error = e.toUserMessage("Preview failed — check the file and try again")) } }
    }

    fun updateOptions(update: ImportOptions.() -> ImportOptions) {
        _state.update { it.copy(options = it.options.update()) }
    }

    fun toggleMember(id: String) {
        val preview = _state.value.preview ?: return
        val current = _state.value.options.selectedMemberIds
            ?: preview.members.map { it.id }.toSet()
        val updated = if (id in current) current - id else current + id
        _state.update { it.copy(options = it.options.copy(selectedMemberIds = updated)) }
    }

    fun runImport() {
        val uri = fileUri ?: return
        val name = cachedFileName ?: "export.json"
        val opts = _state.value.options
        // member_ids: null = import every member, otherwise only the
        // explicitly-selected subset. We only narrow when the selection
        // diverges from "all of preview.members".
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
                    source = ImportJobSource.SIMPLYPLURAL_FILE.toFormPart(),
                    idempotencyKey = UUID.randomUUID().toString().toFormPart(),
                    options = buildSpOptionsJson(opts, narrowedMemberIds).toJsonPart(),
                )
                pollUntilTerminal(job)
            }
                .onSuccess { final -> handleTerminal(final) }
                .onFailure { e -> _state.update { it.copy(isImporting = false, error = e.toUserMessage("Import failed — please try again")) } }
        }
    }

    /**
     * Re-poll [getImportJob] every [POLL_INTERVAL_MS] until the job
     * status lands in [ImportJobStatus.terminal]. The viewModelScope
     * cancels this loop when the screen tears down.
     */
    private suspend fun pollUntilTerminal(initial: ImportJobRead): ImportJobRead {
        var current = initial
        while (current.status !in ImportJobStatus.terminal) {
            delay(POLL_INTERVAL_MS)
            current = api.getImportJob(current.id)
        }
        return current
    }

    private fun handleTerminal(job: ImportJobRead) {
        when (job.status) {
            ImportJobStatus.COMPLETE -> {
                val counts = job.counts
                val warnings = job.events
                    .filter { it.level == "warning" }
                    .map { e -> e.recordRef?.let { "$it: ${e.message}" } ?: e.message }
                val result = SPImportResult(
                    membersImported = counts["members_imported"] ?: 0,
                    customFrontsImported = counts["custom_fronts_imported"] ?: 0,
                    frontsImported = counts["fronts_imported"] ?: 0,
                    groupsImported = counts["groups_imported"] ?: 0,
                    customFieldsImported = counts["custom_fields_imported"] ?: 0,
                    notesSkipped = counts["notes_skipped"] ?: 0,
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
        _state.value = ImportUiState()
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
 * Hand-build the options JSON. Tiny enough that pulling a Moshi
 * adapter for it would be over-engineering, and the backend uses
 * `extra="forbid"` so any typo'd field 422s — easier to read the
 * literal field names here than chase a serializer indirection.
 */
private fun buildSpOptionsJson(opts: ImportOptions, memberIds: List<String>?): String {
    val parts = mutableListOf<String>()
    parts += "\"system_profile\":${opts.systemProfile}"
    parts += "\"custom_fronts\":${opts.customFronts}"
    parts += "\"custom_fields\":${opts.customFields}"
    parts += "\"groups\":${opts.groups}"
    parts += "\"front_history\":${opts.frontHistory}"
    if (memberIds != null) {
        val ids = memberIds.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        parts += "\"member_ids\":[$ids]"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
