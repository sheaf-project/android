package systems.lupine.sheaf.ui.prismimport

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
import systems.lupine.sheaf.data.model.PrismPreviewSummary
import systems.lupine.sheaf.ui.importcommon.ImportResult
import systems.lupine.sheaf.ui.importcommon.terminalResult
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

data class PrismImportOptions(
    val systemProfile: Boolean = true,
    val selectedMemberIds: Set<String>? = null, // null = all
    val memberAvatars: Boolean = true,
    val memberGroups: Boolean = true,
    val customFields: Boolean = true,
    val frontSessions: Boolean = true,
    val notes: Boolean = true,
    val polls: Boolean = true,
    val conversations: Boolean = true,
    val memberBoardPosts: Boolean = true,
    val mediaAttachments: Boolean = true,
)

data class PrismImportUiState(
    val fileName: String? = null,
    val passphrase: String = "",
    val isPreviewing: Boolean = false,
    val preview: PrismPreviewSummary? = null,
    val options: PrismImportOptions = PrismImportOptions(),
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null,
) {
    val hasFile: Boolean get() = fileName != null
}

@HiltViewModel
class PrismImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PrismImportUiState())
    val state: StateFlow<PrismImportUiState> = _state.asStateFlow()

    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        idempotencyKey = null
        fileUri = uri
        val name = resolveFileName(uri) ?: "export.prism"
        cachedFileName = name
        _state.update { it.copy(fileName = name, error = null) }
    }

    fun updatePassphrase(value: String) {
        _state.update { it.copy(passphrase = value, error = null) }
    }

    fun runPreview() {
        val uri = fileUri
        if (uri == null) {
            _state.update { it.copy(error = "Choose a .prism file first.") }
            return
        }
        val passphrase = _state.value.passphrase
        if (passphrase.isEmpty()) {
            _state.update { it.copy(error = "Enter the decryption passphrase.") }
            return
        }
        val name = cachedFileName ?: "export.prism"
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            runCatching { api.previewPrismImport(filePart(uri, name), passphrase.toFormPart()) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            isPreviewing = false,
                            preview = summary,
                            options = PrismImportOptions(
                                systemProfile = summary.systemName != null,
                                selectedMemberIds = null,
                                memberGroups = summary.groupCount > 0,
                                customFields = summary.customFieldCount > 0,
                                frontSessions = summary.frontSessionCount > 0,
                                notes = summary.noteCount > 0,
                                polls = summary.pollCount > 0,
                                conversations = summary.conversationCount > 0,
                                memberBoardPosts = summary.memberBoardPostCount > 0,
                                mediaAttachments = summary.mediaAttachmentCount > 0,
                            ),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(isPreviewing = false, error = e.toUserMessage("Couldn't decrypt the export — check the passphrase and try again")) } }
        }
    }

    fun updateOptions(update: PrismImportOptions.() -> PrismImportOptions) {
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
        val name = cachedFileName ?: "export.prism"
        val passphrase = _state.value.passphrase.takeIf { it.isNotEmpty() } ?: return
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
                    source = ImportJobSource.PRISM_FILE.toFormPart(),
                    idempotencyKey = nextIdempotencyKey().toFormPart(),
                    options = buildOptionsJson(opts, narrowedMemberIds).toJsonPart(),
                    credential = passphrase.toFormPart(),
                )
                pollUntilTerminal(job)
            }
                .onSuccess { final ->
                    // Drop the passphrase from memory once the server owns the
                    // encrypted copy on the job row.
                    _state.update { it.copy(passphrase = "") }
                    handleTerminal(final)
                }
                .onFailure { e -> _state.update { it.copy(isImporting = false, error = e.toUserMessage("Import failed — please try again")) } }
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
        _state.value = PrismImportUiState()
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
 * Hand-build the options JSON. The backend uses `extra="forbid"`, so the
 * field names here must match the Prism importer's options model exactly.
 * `conflict_strategy` defaults to "skip" (mirrors the web client).
 */
private fun buildOptionsJson(opts: PrismImportOptions, memberIds: List<String>?): String {
    val parts = mutableListOf<String>()
    parts += "\"system_profile\":${opts.systemProfile}"
    parts += "\"conflict_strategy\":\"skip\""
    parts += "\"member_avatars\":${opts.memberAvatars}"
    parts += "\"member_groups\":${opts.memberGroups}"
    parts += "\"custom_fields\":${opts.customFields}"
    parts += "\"front_sessions\":${opts.frontSessions}"
    parts += "\"notes\":${opts.notes}"
    parts += "\"polls\":${opts.polls}"
    parts += "\"conversations\":${opts.conversations}"
    parts += "\"member_board_posts\":${opts.memberBoardPosts}"
    parts += "\"media_attachments\":${opts.mediaAttachments}"
    if (memberIds != null) {
        val ids = memberIds.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        parts += "\"member_ids\":[$ids]"
    } else {
        parts += "\"member_ids\":null"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
