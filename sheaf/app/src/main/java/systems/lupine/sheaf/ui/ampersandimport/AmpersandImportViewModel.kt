package systems.lupine.sheaf.ui.ampersandimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.streamingFilePart
import systems.lupine.sheaf.data.model.AmpersandPreviewSummary
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobSource
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.ui.importcommon.ImportResult
import systems.lupine.sheaf.ui.importcommon.terminalResult
import systems.lupine.sheaf.util.toUserMessage
import java.util.UUID
import javax.inject.Inject

// All boolean toggles default on, mirroring the server's AmpersandImportOptions
// defaults. There is no member selection: the preview reports counts only, not a
// member list, so every real member is imported (member_ids stays null).
data class AmpersandImportOptions(
    val customFronts: Boolean = true,
    val customFields: Boolean = true,
    val tags: Boolean = true,
    val groups: Boolean = true,
    val frontHistory: Boolean = true,
    val journals: Boolean = true,
    val notes: Boolean = true,
    val boardMessages: Boolean = true,
    val reminders: Boolean = true,
    val images: Boolean = true,
)

data class AmpersandImportUiState(
    val fileName: String? = null,
    val isPreviewing: Boolean = false,
    val preview: AmpersandPreviewSummary? = null,
    val options: AmpersandImportOptions = AmpersandImportOptions(),
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null,
)

@HiltViewModel
class AmpersandImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AmpersandImportUiState())
    val state: StateFlow<AmpersandImportUiState> = _state.asStateFlow()

    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        idempotencyKey = null
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            fileUri = uri
            val name = resolveFileName(uri) ?: "ampersand.json"
            cachedFileName = name
            _state.update { it.copy(fileName = name) }
            preview(uri, name)
        }
    }

    private suspend fun preview(uri: Uri, name: String) {
        runCatching { api.previewAmpersandImport(filePart(uri, name)) }
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        preview = summary,
                        // Default a toggle on only when the export actually has
                        // that kind of content.
                        options = AmpersandImportOptions(
                            customFronts = summary.customFrontCount > 0,
                            customFields = summary.customFieldCount > 0,
                            tags = summary.tagCount > 0,
                            groups = summary.systemCount > 0,
                            frontHistory = summary.frontHistoryCount > 0,
                            journals = summary.journalCount > 0,
                            notes = summary.noteCount > 0,
                            boardMessages = summary.boardMessageCount > 0 || summary.pollCount > 0,
                            reminders = summary.reminderCount > 0,
                            images = summary.assetCount > 0,
                        ),
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isPreviewing = false, error = e.toUserMessage("Preview failed - check the file and try again"))
                }
            }
    }

    fun updateOptions(update: AmpersandImportOptions.() -> AmpersandImportOptions) {
        _state.update { it.copy(options = it.options.update()) }
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
        val name = cachedFileName ?: "ampersand.json"
        val opts = _state.value.options

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching {
                val job = api.createFileImport(
                    file = filePart(uri, name),
                    source = ImportJobSource.AMPERSAND_FILE.toFormPart(),
                    idempotencyKey = nextIdempotencyKey().toFormPart(),
                    options = buildOptionsJson(opts).toJsonPart(),
                )
                pollUntilTerminal(job)
            }
                .onSuccess { final -> handleTerminal(final) }
                .onFailure { e -> _state.update { it.copy(isImporting = false, error = e.toUserMessage("Import failed - please try again")) } }
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
        when (val outcome = job.terminalResult()) {
            is ImportResult -> _state.update { it.copy(isImporting = false, result = outcome) }
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
        _state.value = AmpersandImportUiState()
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
 * Hand-build the options JSON. The backend uses `extra="forbid"`, so the field
 * names here must match AmpersandImportOptions exactly. `conflict_strategy`
 * defaults to "skip" (mirrors the web client) and `member_ids` is always null
 * (import all real members).
 */
private fun buildOptionsJson(opts: AmpersandImportOptions): String {
    val parts = mutableListOf<String>()
    parts += "\"conflict_strategy\":\"skip\""
    parts += "\"member_ids\":null"
    parts += "\"custom_fronts\":${opts.customFronts}"
    parts += "\"custom_fields\":${opts.customFields}"
    parts += "\"tags\":${opts.tags}"
    parts += "\"groups\":${opts.groups}"
    parts += "\"front_history\":${opts.frontHistory}"
    parts += "\"journals\":${opts.journals}"
    parts += "\"notes\":${opts.notes}"
    parts += "\"board_messages\":${opts.boardMessages}"
    parts += "\"reminders\":${opts.reminders}"
    parts += "\"images\":${opts.images}"
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
