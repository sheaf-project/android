package systems.lupine.sheaf.ui.sheafimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobSource
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.data.model.SheafImportResult
import systems.lupine.sheaf.data.model.SheafPreviewSummary
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject

data class SheafImportOptions(
    val systemProfile: Boolean = true,
    val fronts: Boolean = true,
    val groups: Boolean = true,
    val tags: Boolean = true,
    val customFields: Boolean = true,
)

data class SheafImportUiState(
    val fileName: String? = null,
    val isPreviewing: Boolean = false,
    val preview: SheafPreviewSummary? = null,
    val options: SheafImportOptions = SheafImportOptions(),
    val isImporting: Boolean = false,
    val result: SheafImportResult? = null,
    val error: String? = null,
)

@HiltViewModel
class SheafImportViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SheafImportUiState())
    val state: StateFlow<SheafImportUiState> = _state.asStateFlow()

    private var fileBytes: ByteArray? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val name = resolveFileName(uri) ?: "sheaf_export.json"
                bytes to name
            }.onSuccess { (bytes, name) ->
                fileBytes = bytes
                cachedFileName = name
                _state.update { it.copy(fileName = name) }
                preview(bytes, name)
            }.onFailure { e ->
                _state.update { it.copy(isPreviewing = false, error = "Could not read the file") }
            }
        }
    }

    private suspend fun preview(bytes: ByteArray, name: String) {
        runCatching { api.previewSheafImport(bytes.toPart(name)) }
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        isPreviewing = false,
                        preview = summary,
                        options = SheafImportOptions(
                            systemProfile = true,
                            fronts = summary.frontCount > 0,
                            groups = summary.groupCount > 0,
                            tags = summary.tagCount > 0,
                            customFields = summary.customFieldCount > 0,
                        ),
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(isPreviewing = false, error = e.toUserMessage("Preview failed — check the file and try again")) } }
    }

    fun updateOptions(update: SheafImportOptions.() -> SheafImportOptions) {
        _state.update { it.copy(options = it.options.update()) }
    }

    fun runImport() {
        val bytes = fileBytes ?: return
        val name = cachedFileName ?: "sheaf_export.json"
        val opts = _state.value.options
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching {
                val job = api.createFileImport(
                    file = bytes.toPart(name),
                    source = ImportJobSource.SHEAF_FILE.toFormPart(),
                    idempotencyKey = UUID.randomUUID().toString().toFormPart(),
                    options = buildSheafOptionsJson(opts).toJsonPart(),
                )
                pollUntilTerminal(job)
            }
                .onSuccess { final -> handleTerminal(final) }
                .onFailure { e -> _state.update { it.copy(isImporting = false, error = e.toUserMessage("Import failed — please try again")) } }
        }
    }

    /** See [ImportViewModel.pollUntilTerminal] for the polling-loop rationale. */
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
                val result = SheafImportResult(
                    membersImported = counts["members_imported"] ?: 0,
                    frontsImported = counts["fronts_imported"] ?: 0,
                    groupsImported = counts["groups_imported"] ?: 0,
                    tagsImported = counts["tags_imported"] ?: 0,
                    customFieldsImported = counts["custom_fields_imported"] ?: 0,
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
        fileBytes = null
        cachedFileName = null
        _state.value = SheafImportUiState()
    }

    private fun ByteArray.toPart(name: String): MultipartBody.Part {
        val body = toRequestBody("application/octet-stream".toMediaType())
        return MultipartBody.Part.createFormData("file", name, body)
    }

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
 * Hand-built options JSON — the backend uses extra="forbid" so any
 * typo'd field 422s; spelling it out keeps the field names visible
 * at the call site rather than buried in a serializer.
 *
 * Only the toggles the existing Sheaf import screen exposes are
 * surfaced. Other SheafImportOptions slots (journals, messages, polls,
 * reminders, notifications) fall through to their backend defaults
 * (currently True) — future UI work can add them as explicit toggles.
 */
private fun buildSheafOptionsJson(opts: SheafImportOptions): String {
    val parts = listOf(
        "\"system_profile\":${opts.systemProfile}",
        "\"fronts\":${opts.fronts}",
        "\"groups\":${opts.groups}",
        "\"tags\":${opts.tags}",
        "\"custom_fields\":${opts.customFields}",
    )
    return parts.joinToString(",", prefix = "{", postfix = "}")
}
