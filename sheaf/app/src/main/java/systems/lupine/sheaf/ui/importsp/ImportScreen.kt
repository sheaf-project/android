package systems.lupine.sheaf.ui.importsp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.SPImportResult
import systems.lupine.sheaf.data.model.SPPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from Simply Plural") },
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
                state.isImporting -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("Importing…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.preview != null -> PreviewSection(
                    fileName = state.fileName!!,
                    preview = state.preview!!,
                    options = state.options,
                    onUpdateOptions = { viewModel.updateOptions(it) },
                    onToggleMember = { viewModel.toggleMember(it) },
                    onImport = { viewModel.runImport() },
                    onChangeFile = { filePicker.launch(arrayOf("*/*")) },
                )
                state.isPreviewing -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("Reading file…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> FilePickSection(onPick = { filePicker.launch(arrayOf("*/*")) })
            }
        }
    }
}

// ── File pick ─────────────────────────────────────────────────────────────────

@Composable
private fun FilePickSection(onPick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Outlined.FileOpen,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text("Choose your Simply Plural export file to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onPick) { Text("Choose file") }
        Text(
            "Export your data from Simply Plural via Settings → Export Data, then select the downloaded JSON file here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

// ── Preview + options ─────────────────────────────────────────────────────────

@Composable
private fun PreviewSection(
    fileName: String,
    preview: SPPreviewSummary,
    options: ImportOptions,
    onUpdateOptions: (ImportOptions.() -> ImportOptions) -> Unit,
    onToggleMember: (String) -> Unit,
    onImport: () -> Unit,
    onChangeFile: () -> Unit,
) {
    val effectiveMemberIds = options.selectedMemberIds ?: preview.members.map { it.id }.toSet()

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        TextButton(onClick = onChangeFile) { Text("Change") }
    }

    if (preview.systemName != null) {
        Text("System: ${preview.systemName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }

    HorizontalDivider()

    SectionHeader("What to import")

    if (preview.systemName != null) {
        ImportToggleRow(
            label = "System profile",
            checked = options.systemProfile,
            onCheckedChange = { onUpdateOptions { copy(systemProfile = it) } },
        )
    }

    if (preview.memberCount > 0) {
        ImportToggleRow(
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = member.id in effectiveMemberIds,
                            onCheckedChange = { onToggleMember(member.id) },
                        )
                        Text(member.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (preview.groupCount > 0) {
        ImportToggleRow(
            label = "Groups (${preview.groupCount})",
            checked = options.groups,
            onCheckedChange = { onUpdateOptions { copy(groups = it) } },
        )
    }

    if (preview.customFrontCount > 0) {
        ImportToggleRow(
            label = "Custom fronts (${preview.customFrontCount})",
            checked = options.customFronts,
            onCheckedChange = { onUpdateOptions { copy(customFronts = it) } },
        )
    }

    if (preview.frontHistoryCount > 0) {
        ImportToggleRow(
            label = "Front history (${preview.frontHistoryCount} entries)",
            checked = options.frontHistory,
            onCheckedChange = { onUpdateOptions { copy(frontHistory = it) } },
        )
    }

    if (preview.customFieldCount > 0) {
        ImportToggleRow(
            label = "Custom fields (${preview.customFieldCount})",
            checked = options.customFields,
            onCheckedChange = { onUpdateOptions { copy(customFields = it) } },
        )
    }

    if (preview.noteCount > 0) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(12.dp))
            Text(
                "Notes (${preview.noteCount}) — not supported, will be skipped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text("Import")
    }
}

@Composable
private fun ImportToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Result ────────────────────────────────────────────────────────────────────

@Composable
private fun ResultSection(result: SPImportResult, onImportAnother: () -> Unit) {
    SectionHeader("Import complete")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultRow("Members imported", result.membersImported)
            ResultRow("Groups imported", result.groupsImported)
            ResultRow("Custom fronts imported", result.customFrontsImported)
            ResultRow("Front history entries", result.frontsImported)
            ResultRow("Custom fields imported", result.customFieldsImported)
            if (result.notesSkipped > 0) {
                ResultRow("Notes skipped", result.notesSkipped)
            }
        }
    }

    if (result.warnings.isNotEmpty()) {
        SectionHeader("Warnings")
        result.warnings.forEach { warning ->
            Text(
                "• $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    OutlinedButton(onClick = onImportAnother, modifier = Modifier.fillMaxWidth()) {
        Text("Import another file")
    }
}

@Composable
private fun ResultRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
