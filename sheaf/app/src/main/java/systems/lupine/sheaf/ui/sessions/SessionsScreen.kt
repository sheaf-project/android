package systems.lupine.sheaf.ui.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.SessionRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var revokeOthersConfirm by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<SessionRead?>(null) }
    var renameTarget by remember { mutableStateOf<SessionRead?>(null) }

    Scaffold(
        topBar = {
            SheafTopAppBar(
                title = { Text("Active Sessions") },
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
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val current = state.sessions.filter { it.isCurrent }
                val others = state.sessions.filter { !it.isCurrent }

                if (current.isNotEmpty()) {
                    SectionHeader("This Session")
                    current.forEach { session ->
                        SessionListItem(
                            session = session,
                            onRename = { renameTarget = session },
                            onRevoke = null,
                        )
                        HorizontalDivider()
                    }
                }

                if (others.isNotEmpty()) {
                    SectionHeader("Other Sessions")
                    others.forEach { session ->
                        SessionListItem(
                            session = session,
                            onRename = { renameTarget = session },
                            onRevoke = { revokeTarget = session },
                        )
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { revokeOthersConfirm = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Revoke All Other Sessions")
                    }
                }

                if (state.sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No active sessions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────

    renameTarget?.let { session ->
        var nickname by remember(session.id) { mutableStateOf(session.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname") },
                    placeholder = { Text("e.g. Work laptop") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameSession(session.id, nickname.trim())
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    // ── Revoke single session dialog ──────────────────────────────────────────

    revokeTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke session?") },
            text = {
                Text("${session.nickname ?: session.clientName} will be signed out immediately.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.revokeSession(session.id); revokeTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Revoke") }
            },
            dismissButton = { TextButton(onClick = { revokeTarget = null }) { Text("Cancel") } },
        )
    }

    // ── Revoke all others dialog ──────────────────────────────────────────────

    if (revokeOthersConfirm) {
        AlertDialog(
            onDismissRequest = { revokeOthersConfirm = false },
            title = { Text("Revoke all other sessions?") },
            text = { Text("All other devices will be signed out. This session will remain active.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.revokeOtherSessions(); revokeOthersConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Revoke All") }
            },
            dismissButton = { TextButton(onClick = { revokeOthersConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SessionListItem(
    session: SessionRead,
    onRename: () -> Unit,
    onRevoke: (() -> Unit)?,
) {
    ListItem(
        headlineContent = {
            Text(
                session.nickname ?: session.clientName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (session.nickname != null) {
                    Text(
                        session.clientName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                session.lastActiveAt?.let { at ->
                    Text(
                        "Last active: ${at.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                session.lastActiveIp?.let { ip ->
                    Text(
                        ip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = if (session.isCurrent) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                }
                if (onRevoke != null) {
                    IconButton(onClick = onRevoke) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Revoke",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
    )
}
