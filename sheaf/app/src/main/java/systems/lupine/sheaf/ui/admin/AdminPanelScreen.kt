package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.AnnouncementCreate
import systems.lupine.sheaf.data.model.AnnouncementRead
import systems.lupine.sheaf.data.model.AnnouncementUpdate
import systems.lupine.sheaf.data.model.InviteCodeRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onNavigateUp: () -> Unit,
    onNavigateToAudit: () -> Unit = {},
    onNavigateToJobs: () -> Unit = {},
    onNavigateToUsers: () -> Unit = {},
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
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
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
                    level = authStatus.level,
                    totpEnabled = authStatus.totpEnabled,
                    error = state.stepUpError,
                    onStepUp = { password, totp -> viewModel.stepUp(password, totp) },
                )
                return@Column
            }

            // ── Audit log ───────────────────────────────────────────────────
            SectionHeader("Audit", modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedButton(
                onClick = onNavigateToAudit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) { Text("View audit log") }

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
                if (state.approvals.size > 1) {
                    OutlinedButton(
                        onClick = { viewModel.bulkApprove() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text("Approve all (${state.approvals.size})") }
                }
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
            // The list itself lives on its own screen: it is unbounded, and
            // inline it buried everything below it.
            SectionHeader("Users", modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedButton(
                onClick = onNavigateToUsers,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    state.stats?.totalUsers
                        ?.let { count -> "Search and manage accounts ($count)" }
                        ?: "Search and manage accounts",
                )
            }
            Spacer(Modifier.height(8.dp))

            // ── Invite Codes ──────────────────────────────────────────────────
            var showCreateInviteDialog by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader("Invite Codes", modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreateInviteDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create invite")
                }
            }
            if (state.invites.isEmpty()) {
                Text(
                    "No invite codes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.invites.forEach { invite ->
                InviteCodeListItem(invite = invite, onDelete = { viewModel.deleteInvite(invite.id) })
                HorizontalDivider()
            }
            if (showCreateInviteDialog) {
                CreateInviteDialog(
                    isCreating = state.isCreatingInvite,
                    error = state.createInviteError,
                    onCreate = { maxUses, note, expiresAt ->
                        viewModel.createInvite(maxUses, note, expiresAt)
                    },
                    onDismiss = { showCreateInviteDialog = false; viewModel.clearCreateInviteError() },
                )
                LaunchedEffect(state.isCreatingInvite) {
                    if (!state.isCreatingInvite && state.createInviteError == null) {
                        showCreateInviteDialog = false
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── Announcements ─────────────────────────────────────────────────
            var showCreateAnnouncementDialog by remember { mutableStateOf(false) }
            var announcementToEdit by remember { mutableStateOf<AnnouncementRead?>(null) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader("Announcements", modifier = Modifier.weight(1f))
                IconButton(onClick = { showCreateAnnouncementDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create announcement")
                }
            }
            if (state.announcementError != null) {
                Text(
                    state.announcementError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.announcements.isEmpty()) {
                Text(
                    "No announcements",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.announcements.forEach { announcement ->
                AnnouncementListItem(
                    announcement = announcement,
                    onEdit = { announcementToEdit = announcement },
                    onDelete = { viewModel.deleteAnnouncement(announcement.id) },
                )
                HorizontalDivider()
            }
            if (showCreateAnnouncementDialog) {
                AnnouncementDialog(
                    isSaving = state.isSavingAnnouncement,
                    error = state.announcementError,
                    onSave = { create -> viewModel.createAnnouncement(create) },
                    onDismiss = { showCreateAnnouncementDialog = false; viewModel.clearAnnouncementError() },
                )
                LaunchedEffect(state.announcementSaved) {
                    if (state.announcementSaved) {
                        showCreateAnnouncementDialog = false
                        viewModel.clearAnnouncementSaved()
                    }
                }
            }
            announcementToEdit?.let { editing ->
                AnnouncementDialog(
                    initial = editing,
                    isSaving = state.isSavingAnnouncement,
                    error = state.announcementError,
                    onSave = { create ->
                        viewModel.updateAnnouncement(editing.id, AnnouncementUpdate(
                            title = create.title,
                            body = create.body,
                            severity = create.severity,
                            dismissible = create.dismissible,
                            active = create.active,
                            startsAt = create.startsAt,
                            expiresAt = create.expiresAt,
                        ))
                    },
                    onDismiss = { announcementToEdit = null; viewModel.clearAnnouncementError() },
                )
                LaunchedEffect(state.announcementSaved) {
                    if (state.announcementSaved) {
                        announcementToEdit = null
                        viewModel.clearAnnouncementSaved()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // ── Maintenance ───────────────────────────────────────────────────
            SectionHeader("Maintenance", modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedButton(
                onClick = onNavigateToJobs,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) { Text("Maintenance jobs") }
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
    level: String,
    totpEnabled: Boolean,
    error: String?,
    onStepUp: (password: String, totp: String) -> Unit,
) {
    val needsPassword = level == "password"
    val needsTotp = level == "totp"
    val totpRequiredButMissing = needsTotp && !totpEnabled

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
            when {
                totpRequiredButMissing ->
                    "This server requires authenticator-based step-up for admin access, " +
                        "but you don't have an authenticator set up yet. Enable 2FA in Settings, " +
                        "then come back here."
                needsPassword -> "Confirm your password to access the admin panel."
                needsTotp -> "Enter your authenticator code to access the admin panel."
                else -> "Confirm your identity to access the admin panel."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) ErrorBanner(error)
        if (needsPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (needsTotp && totpEnabled) {
            OutlinedTextField(
                value = totpCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                label = { Text("Authenticator Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = { onStepUp(password, totpCode) },
            enabled = !isSteppingUp && !totpRequiredButMissing &&
                (!needsPassword || password.isNotBlank()) &&
                (!needsTotp || totpCode.length == 6),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isSteppingUp) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text("Authenticate")
        }
    }
}

@Composable
private fun InviteCodeListItem(invite: InviteCodeRead, onDelete: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", invite.code))) } }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(invite.code, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            },
            supportingContent = {
                val uses = if (invite.maxUses == 0) "${invite.useCount} uses (unlimited)"
                           else "${invite.useCount} / ${invite.maxUses} uses"
                val expiry = invite.expiresAt?.let { " · expires ${it.take(10)}" } ?: ""
                val note = invite.note?.let { " · $it" } ?: ""
                Text(uses + expiry + note, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete invite?") },
            text = { Text("The code \"${invite.code}\" will be invalidated immediately.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CreateInviteDialog(
    isCreating: Boolean,
    error: String?,
    onCreate: (maxUses: Int, note: String?, expiresAt: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var maxUsesText by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var expiresAt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("Create Invite Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = maxUsesText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) maxUsesText = it },
                    label = { Text("Max uses (0 = unlimited)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = expiresAt,
                    onValueChange = { expiresAt = it },
                    label = { Text("Expires at (optional)") },
                    placeholder = { Text("2026-12-31T00:00:00Z") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(maxUsesText.toIntOrNull() ?: 0, note, expiresAt) },
                enabled = !isCreating,
            ) {
                if (isCreating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") }
        },
    )
}

@Composable
private fun AnnouncementListItem(
    announcement: AnnouncementRead,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(announcement.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(announcement.severity, style = MaterialTheme.typography.bodySmall)
                    Text("·", style = MaterialTheme.typography.bodySmall)
                    Text(if (announcement.active) "active" else "inactive", style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete announcement?") },
            text = { Text("\"${announcement.title}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AnnouncementDialog(
    initial: AnnouncementRead? = null,
    isSaving: Boolean,
    error: String?,
    onSave: (AnnouncementCreate) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var severity by remember { mutableStateOf(initial?.severity ?: "info") }
    var dismissible by remember { mutableStateOf(initial?.dismissible ?: true) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var startsAt by remember { mutableStateOf(initial?.startsAt ?: "") }
    var expiresAt by remember { mutableStateOf(initial?.expiresAt ?: "") }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (initial == null) "Create Announcement" else "Edit Announcement") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Severity", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("info", "warning", "critical").forEachIndexed { index, s ->
                        SegmentedButton(
                            selected = severity == s,
                            onClick = { severity = s },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) { Text(s, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dismissible, onCheckedChange = { dismissible = it })
                    Text("Dismissible", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = active, onCheckedChange = { active = it })
                    Text("Active", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = startsAt,
                    onValueChange = { startsAt = it },
                    label = { Text("Starts at (optional)") },
                    placeholder = { Text("2026-01-01T00:00:00Z") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = expiresAt,
                    onValueChange = { expiresAt = it },
                    label = { Text("Expires at (optional)") },
                    placeholder = { Text("2026-12-31T00:00:00Z") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(AnnouncementCreate(
                        title = title,
                        body = body,
                        severity = severity,
                        dismissible = dismissible,
                        active = active,
                        startsAt = startsAt.ifBlank { null },
                        expiresAt = expiresAt.ifBlank { null },
                    ))
                },
                enabled = !isSaving && title.isNotBlank() && body.isNotBlank(),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (initial == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        },
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
