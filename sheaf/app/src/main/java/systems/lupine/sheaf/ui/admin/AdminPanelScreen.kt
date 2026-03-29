package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.AdminUserUpdate
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onNavigateUp: () -> Unit,
    viewModel: AdminPanelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    state.maintenanceMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMaintenanceMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel") },
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
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            state.maintenanceMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            val authStatus = state.authStatus
            if (authStatus != null && !authStatus.verified) {
                StepUpSection(
                    isSteppingUp = state.isSteppingUp,
                    requiresTotp = authStatus.totpEnabled,
                    error = state.stepUpError,
                    onStepUp = { password, totp -> viewModel.stepUp(password, totp) },
                )
                return@Column
            }

            // ── Stats ─────────────────────────────────────────────────────────
            state.stats?.let { stats ->
                SectionHeader("Stats", modifier = Modifier.padding(horizontal = 16.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatCell("Users", stats.totalUsers.toString(), Modifier.weight(1f))
                            StatCell("Members", stats.totalMembers.toString(), Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            StatCell(
                                "Storage",
                                formatBytes(stats.totalStorageBytes),
                                Modifier.weight(1f),
                            )
                        }
                        if (stats.usersByTier.isNotEmpty()) {
                            HorizontalDivider()
                            Text("By tier", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            stats.usersByTier.entries.chunked(2).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { (tier, count) ->
                                        StatCell(tier.replace('_', ' ').replaceFirstChar { it.uppercase() }, count.toString(), Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Approvals ─────────────────────────────────────────────────────
            if (state.approvals.isNotEmpty()) {
                SectionHeader("Pending Approvals (${state.approvals.size})", modifier = Modifier.padding(horizontal = 16.dp))
                state.approvals.forEach { user ->
                    ListItem(
                        headlineContent = { Text(user.email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("Signed up: ${user.createdAt.take(10)}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.approveUser(user.id) }) {
                                    Icon(Icons.Outlined.Check, contentDescription = "Approve", tint = MaterialTheme.colorScheme.tertiary)
                                }
                                IconButton(onClick = { viewModel.rejectUser(user.id) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Users ─────────────────────────────────────────────────────────
            SectionHeader("Users", modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = state.search,
                onValueChange = { viewModel.setSearch(it) },
                label = { Text("Search users") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            state.users.forEach { user ->
                UserListItem(
                    user = user,
                    onUpdate = { update -> viewModel.updateUser(user.id, update) },
                )
                HorizontalDivider()
            }

            // ── Maintenance ───────────────────────────────────────────────────
            SectionHeader("Maintenance", modifier = Modifier.padding(horizontal = 16.dp))
            MaintenanceButton("Run Retention") { viewModel.runRetention() }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            MaintenanceButton("Run Cleanup") { viewModel.runCleanup() }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            MaintenanceButton("Run Storage Audit") { viewModel.runStorageAudit() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

@Composable
private fun StepUpSection(
    isSteppingUp: Boolean,
    requiresTotp: Boolean,
    error: String?,
    onStepUp: (password: String, totp: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Admin Authentication", style = MaterialTheme.typography.titleLarge)
        Text(
            "Confirm your identity to access the admin panel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) ErrorBanner(error)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (requiresTotp) {
            OutlinedTextField(
                value = totpCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                label = { Text("Authenticator Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = { onStepUp(password, totpCode) },
            enabled = !isSteppingUp && password.isNotBlank() && (!requiresTotp || totpCode.length == 6),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isSteppingUp) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Authenticate")
        }
    }
}

@Composable
private fun UserListItem(user: systems.lupine.sheaf.data.model.AdminUserRead, onUpdate: (AdminUserUpdate) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(user.email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.tier, style = MaterialTheme.typography.bodySmall)
                    Text("·", style = MaterialTheme.typography.bodySmall)
                    Text(user.accountStatus, style = MaterialTheme.typography.bodySmall)
                    if (user.isAdmin) {
                        Text("·", style = MaterialTheme.typography.bodySmall)
                        Text("admin", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            },
        )
    }

    if (showDialog) {
        UserEditDialog(user = user, onDismiss = { showDialog = false }, onSave = { update ->
            onUpdate(update)
            showDialog = false
        })
    }
}

@Composable
private fun UserEditDialog(
    user: systems.lupine.sheaf.data.model.AdminUserRead,
    onDismiss: () -> Unit,
    onSave: (AdminUserUpdate) -> Unit,
) {
    var tier by remember { mutableStateOf(user.tier) }
    var isAdmin by remember { mutableStateOf(user.isAdmin) }
    var memberLimitText by remember { mutableStateOf(user.memberLimit?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tier", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("free", "plus", "self_hosted").forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tier == t,
                            onClick = { tier = t },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) { Text(t.replace('_', ' '), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                    Text("Admin", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = memberLimitText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) memberLimitText = it },
                    label = { Text("Member limit override") },
                    placeholder = { Text("Leave empty for default") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(AdminUserUpdate(
                    tier = tier.takeIf { it != user.tier },
                    isAdmin = isAdmin.takeIf { it != user.isAdmin },
                    memberLimit = memberLimitText.toIntOrNull(),
                    clearMemberLimit = if (memberLimitText.isBlank() && user.memberLimit != null) true else null,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MaintenanceButton(label: String, onClick: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Surface(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(label) })
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(label) },
            text = { Text("Are you sure you want to run this operation?") },
            confirmButton = {
                TextButton(onClick = { onClick(); confirm = false }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
