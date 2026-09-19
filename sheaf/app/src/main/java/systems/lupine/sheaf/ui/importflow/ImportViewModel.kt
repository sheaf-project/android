package systems.lupine.sheaf.ui.importflow

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
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
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.streamingFilePart
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.ui.importcommon.ImportResult
import systems.lupine.sheaf.ui.importcommon.terminalResult
import systems.lupine.sheaf.util.toUserMessage
import java.util.UUID
import javax.inject.Inject

data class ImportUiState(
    val fileName: String? = null,
    /** Prism's passphrase or PluralKit's token; screen-lifetime only. */
    val credential: String = "",
    val isPreviewing: Boolean = false,
    val preview: ImportPreview? = null,
    /** Category key to checked, seeded from [ImportPreview.defaults]. */
    val selected: Map<String, Boolean> = emptyMap(),
    /** null = every member in the preview. */
    val selectedMemberIds: Set<String>? = null,
    val isImporting: Boolean = false,
    val result: ImportResult? = null,
    val error: String? = null,
)

/**
 * The one importer. Which source it's running is a nav argument; everything
 * that varies between sources lives in the [ImportSource] descriptor, so this
 * only knows the shape they share: supply an input, preview it, choose what to
 * bring across, submit, poll until the job lands, report what happened.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val source: ImportSource = importSourceById(savedStateHandle.get<String>("source"))
        ?: importSources.first()

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    // Holds the picked file URI across the preview -> import flow; the upload
    // streams from it rather than buffering the whole file in memory.
    private var fileUri: Uri? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        idempotencyKey = null
        fileUri = uri
        val name = resolveFileName(uri) ?: source.defaultFileName
        cachedFileName = name
        _state.update { it.copy(fileName = name, error = null) }
        // A plain file source has everything it needs the moment one is picked;
        // an encrypted one still needs the passphrase, so it previews on demand.
        if (source.input is ImportInput.File) runPreview()
    }

    fun updateCredential(value: String) {
        _state.update { it.copy(credential = value, error = null) }
    }

    fun runPreview() {
        val needsFile = source.input !is ImportInput.Token
        if (needsFile && fileUri == null) {
            _state.update { it.copy(error = "Choose a file first.") }
            return
        }
        val credential = _state.value.credential.trim()
        if (source.input !is ImportInput.File && credential.isEmpty()) {
            _state.update { it.copy(error = "Enter your ${credentialNoun()} first.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            val request = PreviewRequest(
                file = fileUri?.let { filePart(it, cachedFileName ?: source.defaultFileName) },
                credential = credential.takeIf { it.isNotEmpty() },
            )
            runCatching { source.preview(api, request) }
                .onSuccess { preview ->
                    _state.update {
                        it.copy(
                            isPreviewing = false,
                            preview = preview,
                            selected = preview.defaults(),
                            selectedMemberIds = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isPreviewing = false, error = e.toUserMessage(source.previewError)) }
                }
        }
    }

    fun setCategory(key: String, checked: Boolean) {
        _state.update { it.copy(selected = it.selected + (key to checked)) }
    }

    fun setAllMembers(checked: Boolean) {
        val preview = _state.value.preview ?: return
        val ids = if (checked) preview.members.map { it.id }.toSet() else emptySet()
        _state.update { it.copy(selectedMemberIds = ids) }
    }

    fun toggleMember(id: String) {
        val preview = _state.value.preview ?: return
        val current = _state.value.selectedMemberIds ?: preview.members.map { it.id }.toSet()
        _state.update {
            it.copy(selectedMemberIds = if (id in current) current - id else current + id)
        }
    }

    // Stable across retries of the same import attempt. If the job was created
    // but polling then failed, the retry must not spawn a second import: reusing
    // the key lets the server return the existing job instead of creating another.
    // Reset when a new file is picked or the job reaches a terminal state.
    private var idempotencyKey: String? = null

    private fun nextIdempotencyKey(): String =
        idempotencyKey ?: UUID.randomUUID().toString().also { idempotencyKey = it }

    fun runImport() {
        val current = _state.value
        val preview = current.preview ?: return
        val credential = current.credential.trim()
        if (source.credentialApi && credential.isEmpty()) return

        val uri = fileUri
        if (!source.credentialApi && uri == null) return

        val options = buildOptionsJson(source, current.selected, narrowedMemberIds(preview))
        val name = cachedFileName ?: source.defaultFileName

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching {
                val job = if (source.credentialApi) {
                    val body = buildApiImportBodyJson(credential, nextIdempotencyKey(), options)
                    api.createApiImport(body.toJsonPart())
                } else {
                    api.createFileImport(
                        file = filePart(uri!!, name),
                        source = source.submitSource(preview).toFormPart(),
                        idempotencyKey = nextIdempotencyKey().toFormPart(),
                        options = options.toJsonPart(),
                        credential = credential.takeIf { it.isNotEmpty() }?.toFormPart(),
                    )
                }
                pollUntilTerminal(job)
            }
                .onSuccess { final ->
                    // The server owns the encrypted copy on the job row now, so
                    // drop our plaintext one.
                    _state.update { it.copy(credential = "") }
                    handleTerminal(final)
                }
                .onFailure { e ->
                    _state.update { it.copy(isImporting = false, error = e.toUserMessage("Import failed — please try again")) }
                }
        }
    }

    /**
     * member_ids: null imports every member, otherwise only the explicitly
     * selected subset. Narrow only when the selection diverges from all of
     * [ImportPreview.members].
     */
    private fun narrowedMemberIds(preview: ImportPreview): List<String>? {
        if (preview.members.isEmpty()) return null
        val all = preview.members.map { it.id }
        val selected = _state.value.selectedMemberIds
        return if (selected == null || selected.containsAll(all)) null else selected.toList()
    }

    /**
     * Re-poll [SheafApiService.getImportJob] every [POLL_INTERVAL_MS] until the
     * job status lands in [ImportJobStatus.terminal]. The viewModelScope
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
        idempotencyKey = null
        val outcome = job.terminalResult()
        if (outcome != null) {
            _state.update { it.copy(isImporting = false, result = outcome) }
        } else {
            _state.update {
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
        idempotencyKey = null
        _state.value = ImportUiState()
    }

    private fun credentialNoun(): String = when (source.input) {
        is ImportInput.EncryptedFile -> "passphrase"
        else -> "token"
    }

    private fun filePart(uri: Uri, name: String) =
        streamingFilePart(context.contentResolver, uri, name)

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
