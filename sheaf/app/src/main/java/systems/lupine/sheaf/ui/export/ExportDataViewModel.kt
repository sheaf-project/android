package systems.lupine.sheaf.ui.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.ExportJobRead
import systems.lupine.sheaf.data.model.ExportJobRequest
import systems.lupine.sheaf.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Interchange format the export is rendered in. */
enum class ExportFormat(val label: String, val description: String) {
    SHEAF("Sheaf", "Full-fidelity backup, re-importable into another Sheaf instance."),
    OPENPLURAL("OpenPlural", "OpenPlural v0.1, for interchange with other compatible apps. JSON here is uri-only; the full backup zip carries image bytes."),
}

data class ExportUiState(
    val format: ExportFormat = ExportFormat.SHEAF,
    val jobs: List<ExportJobRead> = emptyList(),
    val isLoadingJobs: Boolean = false,
    val totpEnabled: Boolean = false,
    val isExportingJson: Boolean = false,
    val isSubmittingJob: Boolean = false,
    val downloadingJobId: String? = null,
    val showStepUp: Boolean = false,
    val stepUpError: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ExportDataViewModel @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    init {
        loadTotpEnabled()
        refreshJobs(poll = true)
    }

    fun setFormat(format: ExportFormat) = _state.update { it.copy(format = format) }

    private fun loadTotpEnabled() {
        viewModelScope.launch {
            runCatching { api.getMe() }
                .onSuccess { user -> _state.update { it.copy(totpEnabled = user.totpEnabled == true) } }
        }
    }

    /** Synchronous JSON export, streamed straight into [uri]. */
    fun exportJsonTo(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isExportingJson = true, error = null, message = null) }
            val formatParam = if (_state.value.format == ExportFormat.OPENPLURAL) "openplural" else "sheaf"
            runCatching {
                withContext(Dispatchers.IO) {
                    api.exportAll(format = formatParam).byteStream().use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                        } ?: error("Couldn't open the chosen location")
                    }
                }
            }
                .onSuccess { _state.update { it.copy(isExportingJson = false, message = "Export saved") } }
                .onFailure { e -> _state.update { it.copy(isExportingJson = false, error = e.toUserMessage("Export failed")) } }
        }
    }

    /** Suggested filename for the synchronous JSON export. */
    fun jsonFileName(timestamp: String): String =
        if (_state.value.format == ExportFormat.OPENPLURAL) "sheaf-export-$timestamp.openplural.json"
        else "sheaf-export-$timestamp.json"

    fun openStepUp() = _state.update { it.copy(showStepUp = true, stepUpError = null) }
    fun dismissStepUp() = _state.update { it.copy(showStepUp = false, stepUpError = null) }

    /** Enqueue the async full-backup-with-images job after step-up auth. */
    fun requestFullBackup(password: String, totpCode: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmittingJob = true, stepUpError = null) }
            val format = if (_state.value.format == ExportFormat.OPENPLURAL) "openplural" else "sheaf_native"
            runCatching {
                api.createExportJob(
                    ExportJobRequest(
                        includeImages = true,
                        format = format,
                        password = password,
                        totpCode = totpCode?.takeIf { it.isNotBlank() },
                    )
                )
            }
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSubmittingJob = false,
                            showStepUp = false,
                            message = "Backup queued. We'll build it in the background; check back here.",
                        )
                    }
                    refreshJobs(poll = true)
                }
                .onFailure { e ->
                    // Keep the dialog open on an auth failure so the user can retry.
                    _state.update { it.copy(isSubmittingJob = false, stepUpError = e.toUserMessage("Couldn't start the backup")) }
                }
        }
    }

    /** Stream a finished backup zip into [uri]. */
    fun downloadJobTo(jobId: String, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(downloadingJobId = jobId, error = null, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    api.downloadExportJob(jobId).byteStream().use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                        } ?: error("Couldn't open the chosen location")
                    }
                }
            }
                .onSuccess { _state.update { it.copy(downloadingJobId = null, message = "Backup saved") } }
                .onFailure { e -> _state.update { it.copy(downloadingJobId = null, error = e.toUserMessage("Download failed")) } }
        }
    }

    fun refreshJobs(poll: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingJobs = it.jobs.isEmpty()) }
            runCatching { api.listExportJobs() }
                .onSuccess { jobs ->
                    _state.update { it.copy(isLoadingJobs = false, jobs = jobs) }
                    if (poll && jobs.any { it.status == "pending" || it.status == "running" }) {
                        pollActiveJobs()
                    }
                }
                .onFailure { _state.update { it.copy(isLoadingJobs = false) } }
        }
    }

    /** Poll while any job is still building, then stop. */
    private fun pollActiveJobs() {
        viewModelScope.launch {
            while (_state.value.jobs.any { it.status == "pending" || it.status == "running" }) {
                delay(POLL_INTERVAL_MS)
                val jobs = runCatching { api.listExportJobs() }.getOrNull() ?: break
                _state.update { it.copy(jobs = jobs) }
            }
        }
    }

    fun clearMessages() = _state.update { it.copy(error = null, message = null) }

    fun fileNameForJob(job: ExportJobRead): String =
        if (job.format == "openplural") "sheaf-export-${job.id}.openplural.zip"
        else "sheaf-export-${job.id}.zip"

    companion object {
        private const val POLL_INTERVAL_MS: Long = 5000
    }
}
