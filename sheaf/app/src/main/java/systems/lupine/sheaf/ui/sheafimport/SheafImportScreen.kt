package systems.lupine.sheaf.ui.sheafimport

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
import systems.lupine.sheaf.data.model.SheafImportResult
import systems.lupine.sheaf.data.model.SheafPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheafImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: SheafImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from Sheaf Export") },
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
                state.result != null -> SheafResultSection(
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
                state.preview != null -> SheafPreviewSection(
                    fileName = state.fileName!!,
                    preview = state.preview!!,
                    options = state.options,
                    onUpdateOptions = { viewModel.updateOptions(it) },
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
                else -> SheafFilePickSection(onPick = { filePicker.launch(arrayOf("*/*")) })
            }
        }
    }
}

@Composable
private fun SheafFilePickSection(onPick: () -> Unit) {
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
        Text(
            "Choose your Sheaf JSON export file to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPick) { Text("Choose file") }
        Text(
            "Export your data from Sheaf via Settings → Export All Data, then select the downloaded JSON file here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SheafPreviewSection(
    fileName: String,
    preview: SheafPreviewSummary,
    options: SheafImportOptions,
    onUpdateOptions: (SheafImportOptions.() -> SheafImportOptions) -> Unit,
    onImport: () -> Unit,
    onChangeFile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        TextButton(onClick = onChangeFile) { Text("Change") }
    }

    HorizontalDivider()
    SectionHeader("What to import")

    SheafImportToggleRow(
        label = "System profile",
        checked = options.systemProfile,
        onCheckedChange = { onUpdateOptions { copy(systemProfile = it) } },
    )

    if (preview.memberCount > 0) {
        SheafImportToggleRow(
            label = "Members (${preview.memberCount})",
            checked = true,
            onCheckedChange = {},
            enabled = false,
        )
    }

    if (preview.frontCount > 0) {
        SheafImportToggleRow(
            label = "Front history (${preview.frontCount} entries)",
            checked = options.fronts,
            onCheckedChange = { onUpdateOptions { copy(fronts = it) } },
        )
    }

    if (preview.groupCount > 0) {
        SheafImportToggleRow(
            label = "Groups (${preview.groupCount})",
            checked = options.groups,
            onCheckedChange = { onUpdateOptions { copy(groups = it) } },
        )
    }

    if (preview.tagCount > 0) {
        SheafImportToggleRow(
            label = "Tags (${preview.tagCount})",
            checked = options.tags,
            onCheckedChange = { onUpdateOptions { copy(tags = it) } },
        )
    }

    if (preview.customFieldCount > 0) {
        SheafImportToggleRow(
            label = "Custom fields (${preview.customFieldCount})",
            checked = options.customFields,
            onCheckedChange = { onUpdateOptions { copy(customFields = it) } },
        )
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Import") }
}

@Composable
private fun SheafImportToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SheafResultSection(result: SheafImportResult, onImportAnother: () -> Unit) {
    SectionHeader("Import complete")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SheafResultRow("Members imported", result.membersImported)
            SheafResultRow("Front history entries", result.frontsImported)
            SheafResultRow("Groups imported", result.groupsImported)
            SheafResultRow("Tags imported", result.tagsImported)
            SheafResultRow("Custom fields imported", result.customFieldsImported)
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
private fun SheafResultRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
