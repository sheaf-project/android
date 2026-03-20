package systems.lupine.sheaf.ui.importsp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SPImportResult
import systems.lupine.sheaf.data.model.SPPreviewSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    // Holds the file bytes across the preview → import flow so we don't re-read the URI.
    private var fileBytes: ByteArray? = null
    private var cachedFileName: String? = null

    fun pickFile(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isPreviewing = true, error = null, preview = null, result = null) }
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val name = resolveFileName(uri) ?: "export.json"
                bytes to name
            }.onSuccess { (bytes, name) ->
                fileBytes = bytes
                cachedFileName = name
                _state.update { it.copy(fileName = name) }
                preview(bytes, name)
            }.onFailure { e ->
                _state.update { it.copy(isPreviewing = false, error = "Could not read file: ${e.message}") }
            }
        }
    }

    private suspend fun preview(bytes: ByteArray, name: String) {
        runCatching { api.previewSimplyPluralImport(bytes.toPart(name)) }
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
            .onFailure { e -> _state.update { it.copy(isPreviewing = false, error = e.message) } }
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
        val bytes = fileBytes ?: return
        val name = cachedFileName ?: "export.json"
        val opts = _state.value.options
        val memberIdsParam = _state.value.preview?.members
            ?.map { it.id }
            ?.let { all ->
                val selected = opts.selectedMemberIds
                if (selected == null || selected.containsAll(all)) null
                else selected.joinToString(",")
            }

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching {
                api.runSimplyPluralImport(
                    systemProfile = opts.systemProfile,
                    customFronts = opts.customFronts,
                    customFields = opts.customFields,
                    groups = opts.groups,
                    frontHistory = opts.frontHistory,
                    memberIds = memberIdsParam,
                    file = bytes.toPart(name),
                )
            }
                .onSuccess { result -> _state.update { it.copy(isImporting = false, result = result) } }
                .onFailure { e -> _state.update { it.copy(isImporting = false, error = e.message) } }
        }
    }

    fun reset() {
        fileBytes = null
        cachedFileName = null
        _state.value = ImportUiState()
    }

    private fun ByteArray.toPart(name: String): MultipartBody.Part {
        val body = toRequestBody("application/octet-stream".toMediaType())
        return MultipartBody.Part.createFormData("file", name, body)
    }

    private fun resolveFileName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(col)
        }
    }.getOrNull()
}
