package systems.lupine.sheaf.ui.prismimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.PrismPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.importcommon.ImportResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrismImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: PrismImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from Prism") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.error != null) ErrorBanner(state.error!!)

            when {
                state.result != null -> ResultSection(
                    result = state.result!!,
                    onImportAnother = { viewModel.reset() },
                )
                state.isImporting -> CenterSpinner("Importing…")
                state.preview != null -> PreviewSection(
                    preview = state.preview!!,
                    options = state.options,
                    onUpdateOptions = { viewModel.updateOptions(it) },
                    onToggleMember = { viewModel.toggleMember(it) },
                    onImport = { viewModel.runImport() },
                )
                state.isPreviewing -> CenterSpinner("Decrypting…")
                else -> UploadSection(
                    fileName = state.fileName,
                    passphrase = state.passphrase,
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                    onPassphraseChange = { viewModel.updatePassphrase(it) },
                    onPreview = { viewModel.runPreview() },
                )
            }
        }
    }
}

@Composable
private fun CenterSpinner(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UploadSection(
    fileName: String?,
    passphrase: String,
    onPickFile: () -> Unit,
    onPassphraseChange: (String) -> Unit,
    onPreview: () -> Unit,
) {
    var showPassphrase by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "In Prism, go to Settings → Data → Export, choose a passphrase, and save the " +
                ".prism file. Select it here together with the same passphrase. The passphrase " +
                "is encrypted at rest while the import runs and wiped when it finishes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.FileOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                fileName ?: "No file selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            TextButton(onClick = onPickFile) { Text(if (fileName == null) "Choose file" else "Change") }
        }

        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Decryption passphrase") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                    Icon(
                        if (showPassphrase) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase",
                    )
                }
            },
        )

        Button(
            onClick = onPreview,
            enabled = fileName != null && passphrase.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Decrypt + preview") }
    }
}

@Composable
private fun PreviewSection(
    preview: PrismPreviewSummary,
    options: PrismImportOptions,
    onUpdateOptions: (PrismImportOptions.() -> PrismImportOptions) -> Unit,
    onToggleMember: (String) -> Unit,
    onImport: () -> Unit,
) {
    val effectiveMemberIds = options.selectedMemberIds ?: preview.members.map { it.id }.toSet()

    if (preview.systemName != null) {
        Text("System: ${preview.systemName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }

    HorizontalDivider()
    SectionHeader("What to import")

    if (preview.systemName != null) {
        ToggleRow(
            label = "System profile",
            checked = options.systemProfile,
            onCheckedChange = { onUpdateOptions { copy(systemProfile = it) } },
        )
    }

    if (preview.memberCount > 0) {
        ToggleRow(
            label = "Members (${preview.memberCount})",
            checked = effectiveMemberIds.isNotEmpty(),
            onCheckedChange = { checked ->
                val ids = if (checked) preview.members.map { it.id }.toSet() else emptySet()
                onUpdateOptions { copy(selectedMemberIds = ids) }
            },
        )
        if (preview.members.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                preview.members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = member.id in effectiveMemberIds,
                            onCheckedChange = { onToggleMember(member.id) },
                        )
                        Text(member.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        ToggleRow(
            label = "Member avatars",
            checked = options.memberAvatars,
            onCheckedChange = { onUpdateOptions { copy(memberAvatars = it) } },
        )
    }

    if (preview.groupCount > 0) {
        ToggleRow(
            label = "Member groups (${preview.groupCount})",
            checked = options.memberGroups,
            onCheckedChange = { onUpdateOptions { copy(memberGroups = it) } },
        )
    }

    if (preview.customFieldCount > 0) {
        ToggleRow(
            label = "Custom fields (${preview.customFieldCount})",
            checked = options.customFields,
            onCheckedChange = { onUpdateOptions { copy(customFields = it) } },
        )
    }

    if (preview.frontSessionCount > 0) {
        ToggleRow(
            label = "Front history (${preview.frontSessionCount} sessions)",
            checked = options.frontSessions,
            onCheckedChange = { onUpdateOptions { copy(frontSessions = it) } },
        )
    }

    if (preview.noteCount > 0) {
        ToggleRow(
            label = "Notes (${preview.noteCount}, as journal entries)",
            checked = options.notes,
            onCheckedChange = { onUpdateOptions { copy(notes = it) } },
        )
    }

    if (preview.pollCount > 0) {
        ToggleRow(
            label = "Polls (${preview.pollCount})",
            checked = options.polls,
            onCheckedChange = { onUpdateOptions { copy(polls = it) } },
        )
    }

    if (preview.conversationCount > 0) {
        ToggleRow(
            label = "Chat messages (${preview.messageCount}, collapsed to system board)",
            checked = options.conversations,
            onCheckedChange = { onUpdateOptions { copy(conversations = it) } },
        )
    }

    if (preview.memberBoardPostCount > 0) {
        ToggleRow(
            label = "Member board posts (${preview.memberBoardPostCount})",
            checked = options.memberBoardPosts,
            onCheckedChange = { onUpdateOptions { copy(memberBoardPosts = it) } },
        )
    }

    if (preview.mediaAttachmentCount > 0) {
        ToggleRow(
            label = "Media attachments (${preview.mediaAttachmentCount})",
            checked = options.mediaAttachments,
            onCheckedChange = { onUpdateOptions { copy(mediaAttachments = it) } },
        )
    }

    val skipped = preview.sleepSessionCount + preview.habitCount + preview.reminderCount
    if (skipped > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(12.dp))
            Text(
                "Skipped on import: ${preview.sleepSessionCount} sleep, ${preview.habitCount} habits, " +
                    "${preview.reminderCount} reminders — no Sheaf surface for these yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Import")
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultSection(result: ImportResult, onImportAnother: () -> Unit) {
    SectionHeader("Import complete")

    val rows = result.rows()
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rows.isEmpty()) {
                Text("Nothing new to import.", style = MaterialTheme.typography.bodyMedium)
            } else {
                rows.forEach { (label, count) -> ResultRow(label, count) }
            }
        }
    }

    if (result.warnings.isNotEmpty()) {
        SectionHeader("Warnings")
        result.warnings.forEach { warning ->
            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Spacer(Modifier.height(4.dp))

    OutlinedButton(onClick = onImportAnother, modifier = Modifier.fillMaxWidth()) {
        Text("Import another file")
    }
}

@Composable
private fun ResultRow(label: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
