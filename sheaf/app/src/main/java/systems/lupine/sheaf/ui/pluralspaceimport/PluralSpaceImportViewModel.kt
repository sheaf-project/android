package systems.lupine.sheaf.ui.pluralspaceimport

import systems.lupine.sheaf.ui.importcommon.jsonQuote
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
import systems.lupine.sheaf.data.model.PluralSpacePreviewSummary
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

data class PluralSpaceImportOptions(
    val systemProfile: Boolean = true,
    val selectedMemberIds: Set<String>? = null, // null = all
    val customFronts: Boolean = true,
    val memberAvatars: Boolean = true,
    val rolesAsTags: Boolean = true,
    val groups: Boolean = true,
    val customFields: Boolean = true,
    val fronts: Boolean = true,
    val journalEntries: Boolean = true,
    val chatMessages: Boolean = true,
    val polls: Boolean = true,
)

data class PluralSpaceImportUiState(
    val fileName: String? = null,
    val isPreviewing: Boolean = false,
    val preview: PluralSpacePreviewSummary? = null,
    val options: PluralSpaceImportOptions = PluralSpaceImportOptions(),
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null,
)

@HiltViewModel
class PluralSpaceImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PluralSpaceImportUiState())
    val state: StateFlow<PluralSpaceImportUiState> = _state.asStateFlow()

    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        idempotencyKey = null
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            fileUri = uri
            val name = resolveFileName(uri) ?: "export.zip"
            cachedFileName = name
            _state.update { it.copy(fileName = name) }
            preview(uri, name)
        }
    }

    private suspend fun preview(uri: Uri, name: String) {
        runCatching { api.previewPluralSpaceImport(filePart(uri, name)) }
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        preview = summary,
                        options = PluralSpaceImportOptions(
                            systemProfile = summary.systemName != null,
                            selectedMemberIds = null,
                            customFronts = summary.customFrontCount > 0,
                            groups = summary.groupCount > 0,
                            customFields = summary.customFieldCount > 0,
                            fronts = summary.frontCount > 0,
                            journalEntries = summary.journalEntryCount > 0,
                            chatMessages = summary.chatMessageCount > 0,
                            polls = summary.pollCount > 0,
                        ),
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(isPreviewing = false, error = e.toUserMessage("Preview failed — check the file and try again")) } }
    }

    fun updateOptions(update: PluralSpaceImportOptions.() -> PluralSpaceImportOptions) {
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
        val name = cachedFileName ?: "export.zip"
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
                    source = ImportJobSource.PLURALSPACE_FILE.toFormPart(),
                    idempotencyKey = nextIdempotencyKey().toFormPart(),
                    options = buildOptionsJson(opts, narrowedMemberIds).toJsonPart(),
                )
                pollUntilTerminal(job)
            }
                .onSuccess { final -> handleTerminal(final) }
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
        _state.value = PluralSpaceImportUiState()
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
 * field names here must match the PluralSpace importer's options model
 * exactly. `conflict_strategy` defaults to "skip" (mirrors the web client).
 */
private fun buildOptionsJson(opts: PluralSpaceImportOptions, memberIds: List<String>?): String {
    val parts = mutableListOf<String>()
    parts += "\"system_profile\":${opts.systemProfile}"
    parts += "\"conflict_strategy\":\"skip\""
    parts += "\"custom_fronts\":${opts.customFronts}"
    parts += "\"member_avatars\":${opts.memberAvatars}"
    parts += "\"roles_as_tags\":${opts.rolesAsTags}"
    parts += "\"groups\":${opts.groups}"
    parts += "\"custom_fields\":${opts.customFields}"
    parts += "\"fronts\":${opts.fronts}"
    parts += "\"journal_entries\":${opts.journalEntries}"
    parts += "\"chat_messages\":${opts.chatMessages}"
    parts += "\"polls\":${opts.polls}"
    if (memberIds != null) {
        val ids = memberIds.joinToString(",") { jsonQuote(it) }
        parts += "\"member_ids\":[$ids]"
    } else {
        parts += "\"member_ids\":null"
    }
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
