package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AdminJobRead
import systems.lupine.sheaf.data.model.AdminPushoverUsage
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class AdminJobsUiState(
    val isLoading: Boolean = false,
    val jobs: List<AdminJobRead> = emptyList(),
    val pushover: AdminPushoverUsage? = null,
    val runningJob: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AdminJobsViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminJobsUiState())
    val state: StateFlow<AdminJobsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.getAdminJobs() }
                .onSuccess { jobs ->
                    _state.update { it.copy(isLoading = false, jobs = jobs) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load jobs")) }
                }
            // Pushover usage is a separate, optional stat; its absence (e.g.
            // pushover not configured on this instance) shouldn't error the page.
            runCatching { api.getAdminPushoverUsage() }
                .onSuccess { usage -> _state.update { it.copy(pushover = usage) } }
        }
    }

    fun runJob(name: String) {
        if (_state.value.runningJob != null) return
        viewModelScope.launch {
            _state.update { it.copy(runningJob = name, message = null, error = null) }
            runCatching { api.runAdminJob(name) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            runningJob = null,
                            message = "$name: ${resp.status} (${resp.itemsProcessed} processed)",
                        )
                    }
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(runningJob = null, error = e.toUserMessage("Job failed to run")) }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminJobsScreen(
    onNavigateUp: () -> Unit,
    viewModel: AdminJobsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Maintenance jobs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(vertical = 8.dp)) }
            state.message?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) { Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }

            state.pushover?.let { p ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Pushover usage (${p.month})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (p.enforced) "${p.count} / ${p.cap} deliveries this month"
                            else "${p.count} deliveries this month (cap not enforced)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            when {
                state.jobs.isEmpty() && state.isLoading ->
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.jobs.isEmpty() ->
                    Text(
                        "No registered jobs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                else -> state.jobs.forEach { job ->
                    JobCard(
                        job = job,
                        isRunning = state.runningJob == job.name,
                        anyRunning = state.runningJob != null,
                        onRun = { viewModel.runJob(job.name) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun JobCard(
    job: AdminJobRead,
    isRunning: Boolean,
    anyRunning: Boolean,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(job.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        job.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = onRun, enabled = !anyRunning) { Text("Run") }
                }
            }
            job.lastRun?.let { run ->
                Spacer(Modifier.height(8.dp))
                val failed = run.status.equals("failed", ignoreCase = true)
                Text(
                    buildString {
                        append("Last run: ")
                        append(run.status)
                        run.finishedAt?.let { append(" · ").append(formatAuditTimestamp(it)) }
                        append(" · ${run.itemsProcessed} processed")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                run.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } ?: run {
                Spacer(Modifier.height(8.dp))
                Text("Never run.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
