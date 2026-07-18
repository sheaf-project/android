package systems.lupine.sheaf.ui.ampersandimport

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
import systems.lupine.sheaf.data.model.AmpersandPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.importcommon.ImportResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmpersandImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: AmpersandImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from Ampersand") },
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
        Text("Choose your Ampersand export to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onPick) { Text("Choose file") }
        Text(
            "In Ampersand, open Settings → Import & export → Export your data to a JSON file (note: this is a different option to 'Export your data', which " +
                "produces an incompatible file format), and save the .json file. Then select it here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PreviewSection(
    fileName: String,
    preview: AmpersandPreviewSummary,
    options: AmpersandImportOptions,
    onUpdateOptions: (AmpersandImportOptions.() -> AmpersandImportOptions) -> Unit,
    onImport: () -> Unit,
    onChangeFile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        TextButton(onClick = onChangeFile) { Text("Change") }
    }

    HorizontalDivider()
    SectionHeader("What to import")

    // Members always import; shown for context, not as a toggle.
    if (preview.memberCount > 0) {
        Text(
            "Members (${preview.memberCount})",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }

    if (preview.systemCount > 0) {
        ImportToggleRow(
            label = "Systems as groups (${preview.systemCount})",
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
    if (preview.tagCount > 0) {
        ImportToggleRow(
            label = "Tags (${preview.tagCount})",
            checked = options.tags,
            onCheckedChange = { onUpdateOptions { copy(tags = it) } },
        )
    }
    if (preview.customFieldCount > 0) {
        ImportToggleRow(
            label = "Custom fields (${preview.customFieldCount})",
            checked = options.customFields,
            onCheckedChange = { onUpdateOptions { copy(customFields = it) } },
        )
    }
    if (preview.frontHistoryCount > 0) {
        ImportToggleRow(
            label = "Front history (${preview.frontHistoryCount} entries)",
            checked = options.frontHistory,
            onCheckedChange = { onUpdateOptions { copy(frontHistory = it) } },
        )
    }
    if (preview.journalCount > 0) {
        ImportToggleRow(
            label = "Journal entries (${preview.journalCount})",
            checked = options.journals,
            onCheckedChange = { onUpdateOptions { copy(journals = it) } },
        )
    }
    if (preview.noteCount > 0) {
        ImportToggleRow(
            label = "Notes (${preview.noteCount})",
            checked = options.notes,
            onCheckedChange = { onUpdateOptions { copy(notes = it) } },
        )
    }
    if (preview.boardMessageCount > 0 || preview.pollCount > 0) {
        val label = buildString {
            append("Board messages")
            if (preview.boardMessageCount > 0) append(" (${preview.boardMessageCount})")
            if (preview.pollCount > 0) append(" & polls (${preview.pollCount})")
        }
        ImportToggleRow(
            label = label,
            checked = options.boardMessages,
            onCheckedChange = { onUpdateOptions { copy(boardMessages = it) } },
        )
    }
    if (preview.reminderCount > 0) {
        ImportToggleRow(
            label = "Reminders (${preview.reminderCount})",
            checked = options.reminders,
            onCheckedChange = { onUpdateOptions { copy(reminders = it) } },
        )
    }
    if (preview.assetCount > 0) {
        ImportToggleRow(
            label = "Images (${preview.assetCount})",
            checked = options.images,
            onCheckedChange = { onUpdateOptions { copy(images = it) } },
        )
    }

    preview.limitWarnings.forEach { warning ->
        SkippedNote(warning)
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
