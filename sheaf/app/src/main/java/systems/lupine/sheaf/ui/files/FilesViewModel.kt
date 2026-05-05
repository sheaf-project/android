package systems.lupine.sheaf.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.api.deleteFileOrQueue
import systems.lupine.sheaf.data.model.FileRead
import systems.lupine.sheaf.data.model.FileUsage
import systems.lupine.sheaf.ui.settings.OrphanFilesDeleteSafety
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class FilesUiState(
    val files: List<FileRead> = emptyList(),
    val usage: FileUsage? = null,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteSafety: OrphanFilesDeleteSafety = OrphanFilesDeleteSafety(),
    val pendingDelete: FileRead? = null,
    val deleteError: String? = null,
    val resultMessage: String? = null,
    val error: String? = null,
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState(isLoading = true))
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val files = api.listFiles().sortedByDescending { it.createdAt }
                val usage = runCatching { api.getFileUsage() }.getOrNull()
                val safety = runCatching {
                    val s = api.getSystemSafety()
                    val u = runCatching { api.getMe() }.getOrNull()
                    OrphanFilesDeleteSafety(
                        authTier = s.settings.authTier,
                        totpEnabled = u?.totpEnabled == true,
                        appliesToImages = s.settings.appliesToImages,
                        gracePeriodDays = s.settings.gracePeriodDays,
                    )
                }.getOrDefault(OrphanFilesDeleteSafety())
                Triple(files, usage, safety)
            }
                .onSuccess { (files, usage, safety) ->
                    _state.update {
                        it.copy(
                            files = files,
                            usage = usage,
                            deleteSafety = safety,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                }
        }
    }

    fun openDelete(file: FileRead) { _state.update { it.copy(pendingDelete = file, deleteError = null) } }
    fun closeDelete() { _state.update { it.copy(pendingDelete = null, deleteError = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearResult() { _state.update { it.copy(resultMessage = null) } }

    fun confirmDelete(password: String?, totpCode: String?) {
        val file = _state.value.pendingDelete ?: return
        _state.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            runCatching { api.deleteFileOrQueue(file.id, password, totpCode) }
                .onSuccess { pending ->
                    val grace = _state.value.deleteSafety.gracePeriodDays
                    val msg = if (pending?.pendingActionId != null) {
                        "Queued for deletion in $grace day${plural(grace)}. " +
                            "Cancel from System Safety before then."
                    } else {
                        "File deleted."
                    }
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            pendingDelete = null,
                            files = it.files.filterNot { f -> f.id == file.id },
                            resultMessage = msg,
                        )
                    }
                    runCatching { api.getFileUsage() }
                        .onSuccess { u -> _state.update { it.copy(usage = u) } }
                }
                .onFailure { e ->
                    val msg = if (e is HttpException && e.code() in listOf(400, 401))
                        "Incorrect password or authenticator code"
                    else e.toUserMessage("Failed to delete file")
                    _state.update { it.copy(isDeleting = false, deleteError = msg) }
                }
        }
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"
