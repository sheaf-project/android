package systems.lupine.sheaf.ui.tbimport

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
import systems.lupine.sheaf.data.model.TBImportResult
import systems.lupine.sheaf.data.model.TBPreviewSummary
import systems.lupine.sheaf.util.toUserMessage
import java.util.UUID
import javax.inject.Inject

data class TBImportOptions(
    val selectedMemberIds: Set<String>? = null,
    val groups: Boolean = true,
)

data class TBImportUiState(
    val fileName: String? = null,
    val isPreviewing: Boolean = false,
    val preview: TBPreviewSummary? = null,
    val options: TBImportOptions = TBImportOptions(),
    val isImporting: Boolean = false,
    val result: TBImportResult? = null,
    val error: String? = null,
)

/**
 * Tupperbox file import. Smaller surface than PK/SP/Sheaf — TB exports
 * carry just tuppers + groups, no system metadata, no fronting history.
 */
@HiltViewModel
class TupperboxImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TBImportUiState())
    val state: StateFlow<TBImportUiState> = _state.asStateFlow()

    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        idempotencyKey = null
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            fileUri = uri
            val name = resolveFileName(uri) ?: "tuppers.json"
            cachedFileName = name
            _state.update { it.copy(fileName = name) }
            preview(uri, name)
        }
    }

    private suspend fun preview(uri: Uri, name: String) {
        runCatching { api.previewTupperboxImport(filePart(uri, name)) }
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        preview = summary,
                        options = TBImportOptions(
                            selectedMemberIds = null,
                            groups = summary.groupCount > 0,
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

    fun updateOptions(update: TBImportOptions.() -> TBImportOptions) {
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
        val name = cachedFileName ?: "tuppers.json"
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
                    source = ImportJobSource.TUPPERBOX_FILE.toFormPart(),
                    idempotencyKey = nextIdempotencyKey().toFormPart(),
                    options = buildTbOptionsJson(opts, narrowedMemberIds).toJsonPart(),
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
                val result = TBImportResult(
                    membersImported = counts["members_imported"] ?: 0,
                    groupsImported = counts["groups_imported"] ?: 0,
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
        _state.value = TBImportUiState()
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

internal fun buildTbOptionsJson(opts: TBImportOptions, memberIds: List<String>?): String {
    val parts = mutableListOf<String>()
    parts += "\"groups\":${opts.groups}"
    if (memberIds != null) {
        val ids = memberIds.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        parts += "\"member_ids\":[$ids]"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
