package systems.lupine.sheaf.ui.imports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class ImportJobDetailUiState(
    val job: ImportJobRead? = null,
    val isLoading: Boolean = false,
    val isActioning: Boolean = false,
    val actionDone: Boolean = false,
    val error: String? = null,
)

/**
 * Detail view of a single import job. Refreshes on init and, when the
 * job is non-terminal, keeps polling at the same cadence as the
 * importer screens so the user can watch a pending/running import
 * land without manual refresh. The poll loop dies when the screen
 * tears down (viewModelScope) or when the job lands on a terminal
 * status.
 *
 * Also drives the cancel-or-archive action, which the backend folds
 * into a single DELETE: pending → cancel, terminal → archive,
 * running → 409 (we surface that to the UI as an error).
 */
@HiltViewModel
class ImportJobDetailViewModel @Inject constructor(
    private val api: SheafApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val jobId: String = checkNotNull(savedStateHandle["jobId"]) {
        "ImportJobDetailViewModel needs a jobId nav arg"
    }

    private val _state = MutableStateFlow(ImportJobDetailUiState())
    val state: StateFlow<ImportJobDetailUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getImportJob(jobId) }
                .onSuccess { job ->
                    _state.update { it.copy(job = job, isLoading = false) }
                    if (job.status !in ImportJobStatus.terminal) startPolling()
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load import")) }
                }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val updated = runCatching { api.getImportJob(jobId) }.getOrNull() ?: continue
                _state.update { it.copy(job = updated) }
                if (updated.status in ImportJobStatus.terminal) break
            }
        }
    }

    /**
     * Cancel (if pending) or archive (if terminal). Server-side which-one
     * is implicit from the job's current status. UI labels the button
     * differently to avoid surprising the user.
     */
    fun cancelOrArchive() {
        viewModelScope.launch {
            _state.update { it.copy(isActioning = true, error = null) }
            runCatching { api.cancelOrArchiveImport(jobId) }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _state.update { it.copy(isActioning = false, actionDone = true) }
                    } else if (response.code() == 409) {
                        _state.update {
                            it.copy(
                                isActioning = false,
                                error = "This import is currently running. Wait for it to finish, then archive.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isActioning = false,
                                error = "Couldn't update the import (HTTP ${response.code()})",
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isActioning = false, error = e.toUserMessage("Action failed")) }
                }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1500
    }
}
