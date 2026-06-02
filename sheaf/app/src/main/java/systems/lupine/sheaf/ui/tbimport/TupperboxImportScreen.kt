package systems.lupine.sheaf.ui.tbimport

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
import systems.lupine.sheaf.data.model.TBImportResult
import systems.lupine.sheaf.data.model.TBPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TupperboxImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: TupperboxImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from Tupperbox") },
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
        Text(
            "Choose your Tupperbox export JSON to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPick) { Text("Choose file") }
        Text(
            "Export from Tupperbox with the `tul!export` command in DM. Tupperbox sends back a JSON file with your tuppers and groups; pick it here. " +
                "Tupperbox exports don't carry fronting history or system-level metadata, so only members and groups land in Sheaf.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PreviewSection(
    fileName: String,
    preview: TBPreviewSummary,
    options: TBImportOptions,
    onUpdateOptions: (TBImportOptions.() -> TBImportOptions) -> Unit,
    onImport: () -> Unit,
    onChangeFile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        TextButton(onClick = onChangeFile) { Text("Change") }
    }

    HorizontalDivider()
    SectionHeader("What to import")

    if (preview.memberCount > 0) {
        ToggleRow(
            label = "Tuppers (${preview.memberCount})",
            checked = true,
            onCheckedChange = {},
            enabled = false,
        )
    }

    if (preview.groupCount > 0) {
        ToggleRow(
            label = "Groups (${preview.groupCount})",
            checked = options.groups,
            onCheckedChange = { onUpdateOptions { copy(groups = it) } },
        )
    }

    Spacer(Modifier.height(4.dp))

    Button(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Import") }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultSection(result: TBImportResult, onImportAnother: () -> Unit) {
    SectionHeader("Import complete")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultRow("Tuppers imported", result.membersImported)
            ResultRow("Groups imported", result.groupsImported)
        }
    }

    if (result.warnings.isNotEmpty()) {
        SectionHeader("Warnings")
        result.warnings.forEach { w ->
            Text("• $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
