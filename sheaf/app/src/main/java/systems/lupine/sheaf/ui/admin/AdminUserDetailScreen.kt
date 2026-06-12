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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.AdminExplainResponse
import systems.lupine.sheaf.data.model.AdminImportJobDetail
import systems.lupine.sheaf.data.model.AdminImportJobSummary
import systems.lupine.sheaf.data.model.AdminReasonBody
import systems.lupine.sheaf.data.model.AdminSessionRow
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

data class AdminUserDetailUiState(
    val isLoading: Boolean = false,
    val explain: AdminExplainResponse? = null,
    val sessions: List<AdminSessionRow> = emptyList(),
    val importJobs: List<AdminImportJobSummary> = emptyList(),
    val viewedImportJob: AdminImportJobDetail? = null,
    // Holds a freshly-fetched GDPR dossier JSON until the save target is
    // chosen; the screen writes it to the picked file and clears it.
    val dossierJson: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AdminUserDetailViewModel @Inject constructor(
    private val api: SheafApiService,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUserDetailUiState())
    val state: StateFlow<AdminUserDetailUiState> = _state.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                coroutineScope {
                    val explain = async { api.getAdminUserExplain(userId) }
                    // Sessions and import jobs are separate, best-effort reads:
                    // a failure in either shouldn't blank the whole dossier.
                    val sessions = async {
                        runCatching { api.getAdminUserSessions(userId) }.getOrDefault(emptyList())
                    }
                    val importJobs = async {
                        runCatching { api.getAdminUserImportJobs(userId) }.getOrDefault(emptyList())
                    }
                    Triple(explain.await(), sessions.await(), importJobs.await())
                }
            }
                .onSuccess { (explain, sessions, importJobs) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            explain = explain,
                            sessions = sessions,
                            importJobs = importJobs,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load account")) }
                }
        }
    }

    fun terminateSession(userId: String, sessionId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.terminateAdminUserSession(userId, sessionId, AdminReasonBody(reason)) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            message = "Session revoked",
                            sessions = s.sessions.filterNot { it.id == sessionId },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't revoke session")) } }
        }
    }

    fun rotateAllKeys(userId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.rotateAllAdminUserApiKeys(userId, AdminReasonBody(reason)) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(message = "Revoked ${resp.revokedCount} API key(s)")
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't rotate API keys")) } }
        }
    }

    fun resetSafety(userId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.adminResetSafety(userId, AdminReasonBody(reason)) }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            message = if (resp.changedFields.isEmpty()) {
                                "System safety already at defaults"
                            } else {
                                "Reset: ${resp.changedFields.joinToString(", ")}"
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't reset system safety")) } }
        }
    }

    fun bypassPending(userId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.adminBypassPending(userId, AdminReasonBody(reason)) }
                .onSuccess { resp ->
                    _state.update { it.copy(message = "Finalized ${resp.finalizedCount} pending action(s)") }
                }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't finalize pending actions")) } }
        }
    }

    fun viewImportJob(jobId: String, reason: String) {
        viewModelScope.launch {
            runCatching { api.getAdminImportJobDetail(jobId, AdminReasonBody(reason)) }
                .onSuccess { detail -> _state.update { it.copy(viewedImportJob = detail) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage("Couldn't load import job")) } }
        }
    }

    fun dismissImportJob() { _state.update { it.copy(viewedImportJob = null) } }

    fun exportDossier(userId: String, reason: String) {
        viewModelScope.launch {
            _state.update { it.copy(message = "Preparing export…", error = null) }
            runCatching { api.exportUserDossier(userId, AdminReasonBody(reason)).use { it.string() } }
                .onSuccess { json -> _state.update { it.copy(dossierJson = json, message = null) } }
                .onFailure { e ->
                    _state.update { it.copy(message = null, error = e.toUserMessage("Couldn't export account data")) }
                }
        }
    }

    fun clearDossier() { _state.update { it.copy(dossierJson = null) } }

    fun clearMessage() { _state.update { it.copy(message = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserDetailScreen(
    userId: String,
    onNavigateUp: () -> Unit,
    viewModel: AdminUserDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var rotateKeys by remember { mutableStateOf(false) }
    var terminateTarget by remember { mutableStateOf<AdminSessionRow?>(null) }
    var showResetSafety by remember { mutableStateOf(false) }
    var showBypassPending by remember { mutableStateOf(false) }
    var showDossierExport by remember { mutableStateOf(false) }
    var importJobTarget by remember { mutableStateOf<AdminImportJobSummary?>(null) }
    var pendingDossier by remember { mutableStateOf<String?>(null) }

    // Save-as flow for the GDPR dossier, mirroring the data-export save in
    // account settings: fetch the JSON, then let the operator pick a file to
    // write it to.
    val saveDossierLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingDossier
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
        pendingDossier = null
        viewModel.clearDossier()
    }
    LaunchedEffect(state.dossierJson) {
        state.dossierJson?.let { json ->
            pendingDossier = json
            saveDossierLauncher.launch("sheaf-account-export.json")
        }
    }

    LaunchedEffect(userId) { viewModel.load(userId) }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Account detail") },
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) { Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer) }
            }

            when {
                state.isLoading && state.explain == null ->
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.explain != null -> DetailBody(
                    explain = state.explain!!,
                    sessions = state.sessions,
                    importJobs = state.importJobs,
                    onTerminate = { terminateTarget = it },
                    onRotateKeys = { rotateKeys = true },
                    onResetSafety = { showResetSafety = true },
                    onBypassPending = { showBypassPending = true },
                    onViewImportJob = { importJobTarget = it },
                    onExportDossier = { showDossierExport = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (rotateKeys) {
        AdminReasonDialog(
            title = "Revoke all API keys?",
            message = "Revokes every API key on this account. Scripts and integrations using them will stop working immediately.",
            confirmLabel = "Revoke all",
            destructive = true,
            onConfirm = { reason, _ -> viewModel.rotateAllKeys(userId, reason); rotateKeys = false },
            onDismiss = { rotateKeys = false },
        )
    }
    terminateTarget?.let { session ->
        AdminReasonDialog(
            title = "Revoke session?",
            message = "Signs this device out: ${session.nickname ?: session.userAgent ?: session.ip ?: "unknown device"}.",
            confirmLabel = "Revoke",
            destructive = true,
            onConfirm = { reason, _ ->
                viewModel.terminateSession(userId, session.id, reason)
                terminateTarget = null
            },
            onDismiss = { terminateTarget = null },
        )
    }
    if (showResetSafety) {
        AdminReasonDialog(
            title = "Reset system safety?",
            message = "Clears this account's System Safety toggles, zeroes the grace period, and resets delete confirmation. Does not touch already-queued pending actions.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = { reason, _ -> viewModel.resetSafety(userId, reason); showResetSafety = false },
            onDismiss = { showResetSafety = false },
        )
    }
    if (showBypassPending) {
        AdminReasonDialog(
            title = "Finalize pending actions?",
            message = "Immediately finalizes every queued System Safety action on this account, bypassing the grace period. This cannot be undone.",
            confirmLabel = "Finalize now",
            destructive = true,
            onConfirm = { reason, _ -> viewModel.bypassPending(userId, reason); showBypassPending = false },
            onDismiss = { showBypassPending = false },
        )
    }
    if (showDossierExport) {
        AdminReasonDialog(
            title = "Export account data?",
            message = "Builds a GDPR Article 15 metadata export for ${state.explain?.email ?: "this account"} and lets you save it as a JSON file. This is logged.",
            confirmLabel = "Export",
            onConfirm = { reason, _ -> viewModel.exportDossier(userId, reason); showDossierExport = false },
            onDismiss = { showDossierExport = false },
        )
    }
    importJobTarget?.let { job ->
        AdminReasonDialog(
            title = "View import log?",
            message = "Opens the full event log for the ${job.source} import from ${formatAuditTimestamp(job.createdAt)}. The events can quote the user's data, so this read is logged.",
            confirmLabel = "View",
            onConfirm = { reason, _ ->
                viewModel.viewImportJob(job.id, reason)
                importJobTarget = null
            },
            onDismiss = { importJobTarget = null },
        )
    }
    state.viewedImportJob?.let { detail ->
        ImportJobDetailDialog(detail = detail, onDismiss = { viewModel.dismissImportJob() })
    }
}

@Composable
private fun DetailBody(
    explain: AdminExplainResponse,
    sessions: List<AdminSessionRow>,
    importJobs: List<AdminImportJobSummary>,
    onTerminate: (AdminSessionRow) -> Unit,
    onRotateKeys: () -> Unit,
    onResetSafety: () -> Unit,
    onBypassPending: () -> Unit,
    onViewImportJob: (AdminImportJobSummary) -> Unit,
    onExportDossier: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Text(explain.email, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(8.dp))

    DetailCard("Account") {
        InfoRow("Status", explain.accountStatus)
        InfoRow("Tier", explain.tier)
        InfoRow("Admin", if (explain.isAdmin) "yes" else "no")
        InfoRow("Email verified", if (explain.emailVerified) "yes" else "no")
        InfoRow("Two-factor", if (explain.totpEnabled) "enabled" else "disabled")
        explain.signupIp?.let { InfoRow("Signup IP", it) }
        InfoRow("Created", formatAuditTimestamp(explain.createdAt))
        explain.lastLoginAt?.let { InfoRow("Last login", formatAuditTimestamp(it)) }
        InfoRow("Active sessions", explain.activeSessionCount.toString())
        InfoRow("API keys", explain.apiKeyCount.toString())
    }

    explain.system?.let { sys ->
        Spacer(Modifier.height(12.dp))
        DetailCard("System") {
            InfoRow("Name", sys.name)
            InfoRow("Members", sys.memberCount.toString())
            InfoRow("Delete confirmation", sys.deleteConfirmation)
            InfoRow("Grace period", "${sys.gracePeriodDays} day(s)")
        }
    }

    Spacer(Modifier.height(12.dp))
    DetailCard("Sessions") {
        if (sessions.isEmpty()) {
            Text("No active sessions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sessions.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        s.nickname ?: s.userAgent ?: "Unknown device",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(s.ip, s.lastSeenAt?.let { "seen ${formatAuditTimestamp(it)}" })
                        .joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = { onTerminate(s) }) { Text("Revoke") }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    DetailCard("API keys") {
        Text(
            "${explain.apiKeyCount} key(s) on this account.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRotateKeys,
            modifier = Modifier.fillMaxWidth(),
            enabled = explain.apiKeyCount > 0,
        ) { Text("Revoke all API keys") }
    }

    if (importJobs.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        DetailCard("Import jobs") {
            importJobs.forEachIndexed { i, job ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${job.source} · ${job.status}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatAuditTimestamp(job.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onViewImportJob(job) }) { Text("Log") }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    DetailCard("Data export") {
        Text(
            "GDPR Article 15 metadata export (account, system, counts, " +
                "sessions, API-key metadata, admin history). Excludes member " +
                "content. Logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExportDossier,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export account data") }
    }

    // Emergency ops live behind their own card, below the routine info, and
    // are not offered for admin accounts (the backend refuses them there).
    if (!explain.isAdmin) {
        Spacer(Modifier.height(12.dp))
        DetailCard("Emergency") {
            Text(
                "Operator overrides. Each writes an audit entry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onResetSafety,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset system safety") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onBypassPending,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Finalize pending actions now") }
        }
    }

    if (explain.recentAdminAudit.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Recent admin actions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        explain.recentAdminAudit.forEach { row ->
            AuditCard(
                action = row.action,
                targetType = row.targetType,
                actorEmail = null,
                timestamp = row.createdAt,
                reason = row.reason,
                before = null,
                after = null,
            )
        }
    }
}

@Composable
private fun ImportJobDetailDialog(
    detail: AdminImportJobDetail,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${detail.source} import") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoRow("Status", detail.status)
                detail.finishedAt?.let { InfoRow("Finished", formatAuditTimestamp(it)) }
                detail.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (detail.counts.isNotEmpty()) {
                    HorizontalDivider()
                    detail.counts.forEach { (k, v) -> InfoRow(k, v.toString()) }
                }
                HorizontalDivider()
                Text(
                    "Events (${detail.events.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (detail.events.isEmpty()) {
                    Text(
                        "No events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.events.forEach { e ->
                    val prefix = e.recordRef?.let { "$it: " } ?: ""
                    Text(
                        "[${e.level}] $prefix${e.message}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (e.level.equals("error", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
