package systems.lupine.sheaf.ui.sheafimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.SheafImportResult
import systems.lupine.sheaf.data.model.SheafPreviewSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
                _state.update { it.copy(isPreviewing = false, error = "Could not read file: ${e.message}") }
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
            .onFailure { e -> _state.update { it.copy(isPreviewing = false, error = e.message) } }
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
                api.runSheafImport(
                    systemProfile = opts.systemProfile,
                    fronts = opts.fronts,
                    groups = opts.groups,
                    tags = opts.tags,
                    customFields = opts.customFields,
                    memberIds = null,
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
        _state.value = SheafImportUiState()
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
