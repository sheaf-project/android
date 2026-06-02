package systems.lupine.sheaf.ui.pkimport

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
import systems.lupine.sheaf.data.model.PKImportResult
import systems.lupine.sheaf.data.model.PKPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluralKitFileImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: PluralKitFileImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from PluralKit (file)") },
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
            "Choose your PluralKit export JSON to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPick) { Text("Choose file") }
        Text(
            "Export from PluralKit with the `pk;export` command in DM. PK sends you a download link; save the JSON and pick it here. " +
                "Front history can be very large — leave it unchecked unless you specifically want switch logs in Sheaf.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PreviewSection(
    fileName: String,
    preview: PKPreviewSummary,
    options: PKFileImportOptions,
    onUpdateOptions: (PKFileImportOptions.() -> PKFileImportOptions) -> Unit,
    onImport: () -> Unit,
    onChangeFile: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        TextButton(onClick = onChangeFile) { Text("Change") }
    }

    HorizontalDivider()
    SectionHeader("What to import")

    ToggleRow(
        label = "System profile",
        checked = options.systemProfile,
        onCheckedChange = { onUpdateOptions { copy(systemProfile = it) } },
    )

    if (preview.memberCount > 0) {
        ToggleRow(
            label = "Members (${preview.memberCount})",
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

    if (preview.switchCount > 0) {
        ToggleRow(
            label = "Front history (${preview.switchCount} switches)",
            checked = options.frontHistory,
            onCheckedChange = { onUpdateOptions { copy(frontHistory = it) } },
        )
        if (options.frontHistory) {
            Text(
                "Large switch logs can take a while to import — the job runs in the background and shows up under Import history while it's working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun ResultSection(result: PKImportResult, onImportAnother: () -> Unit) {
    SectionHeader("Import complete")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ResultRow("Members imported", result.membersImported)
            ResultRow("Groups imported", result.groupsImported)
            ResultRow("Front history entries", result.frontsImported)
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
