package systems.lupine.sheaf.ui.pluralspaceimport

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
import systems.lupine.sheaf.data.model.PluralSpacePreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.importcommon.ImportResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluralSpaceImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: PluralSpaceImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from PluralSpace") },
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
                    fileName = state.fileName!!,
                    preview = state.preview!!,
                    options = state.options,
                    onUpdateOptions = { viewModel.updateOptions(it) },
                    onToggleMember = { viewModel.toggleMember(it) },
                    onImport = { viewModel.runImport() },
                    onChangeFile = { filePicker.launch(arrayOf("*/*")) },
                )
                state.isPreviewing -> CenterSpinner("Reading file…")
                else -> FilePickSection(onPick = { filePicker.launch(arrayOf("*/*")) })
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
        Text("Choose your PluralSpace export to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onPick) { Text("Choose file") }
        Text(
            "In PluralSpace, open Settings → Data export and generate an export. " +
                "Download the resulting .zip, then select it here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PreviewSection(
    fileName: String,
    preview: PluralSpacePreviewSummary,
    options: PluralSpaceImportOptions,
    onUpdateOptions: (PluralSpaceImportOptions.() -> PluralSpaceImportOptions) -> Unit,
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
        ImportToggleRow(
            label = "Member avatars",
            checked = options.memberAvatars,
            onCheckedChange = { onUpdateOptions { copy(memberAvatars = it) } },
        )
        ImportToggleRow(
            label = "Roles as tags",
            checked = options.rolesAsTags,
            onCheckedChange = { onUpdateOptions { copy(rolesAsTags = it) } },
        )
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

    if (preview.customFieldCount > 0) {
        ImportToggleRow(
            label = "Custom fields (${preview.customFieldCount})",
            checked = options.customFields,
            onCheckedChange = { onUpdateOptions { copy(customFields = it) } },
        )
    }

    if (preview.frontCount > 0) {
        ImportToggleRow(
            label = "Front history (${preview.frontCount} entries)",
            checked = options.fronts,
            onCheckedChange = { onUpdateOptions { copy(fronts = it) } },
        )
    }

    if (preview.journalEntryCount > 0) {
        ImportToggleRow(
            label = "Journal entries (${preview.journalEntryCount})",
            checked = options.journalEntries,
            onCheckedChange = { onUpdateOptions { copy(journalEntries = it) } },
        )
    }

    if (preview.chatMessageCount > 0) {
        ImportToggleRow(
            label = "Chat messages (${preview.chatMessageCount}, collapsed to system board)",
            checked = options.chatMessages,
            onCheckedChange = { onUpdateOptions { copy(chatMessages = it) } },
        )
    }

    if (preview.pollCount > 0) {
        ImportToggleRow(
            label = "Polls (${preview.pollCount})",
            checked = options.polls,
            onCheckedChange = { onUpdateOptions { copy(polls = it) } },
        )
    }

    if (preview.thoughtCount > 0) {
        SkippedNote("Thoughts (${preview.thoughtCount}) — no Sheaf equivalent, will be skipped")
    }

    Spacer(Modifier.height(4.dp))

    Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Import")
    }
}

@Composable
private fun ImportToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SkippedNote(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
