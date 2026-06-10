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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    // Sessions are a separate, best-effort read: a failure here
                    // shouldn't blank the whole dossier.
                    val sessions = async {
                        runCatching { api.getAdminUserSessions(userId) }.getOrDefault(emptyList())
                    }
                    explain.await() to sessions.await()
                }
            }
                .onSuccess { (explain, sessions) ->
                    _state.update {
                        it.copy(isLoading = false, explain = explain, sessions = sessions)
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
    var rotateKeys by remember { mutableStateOf(false) }
    var terminateTarget by remember { mutableStateOf<AdminSessionRow?>(null) }
    var showResetSafety by remember { mutableStateOf(false) }
    var showBypassPending by remember { mutableStateOf(false) }

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
                    onTerminate = { terminateTarget = it },
                    onRotateKeys = { rotateKeys = true },
                    onResetSafety = { showResetSafety = true },
                    onBypassPending = { showBypassPending = true },
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
}

@Composable
private fun DetailBody(
    explain: AdminExplainResponse,
    sessions: List<AdminSessionRow>,
    onTerminate: (AdminSessionRow) -> Unit,
    onRotateKeys: () -> Unit,
    onResetSafety: () -> Unit,
    onBypassPending: () -> Unit,
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
