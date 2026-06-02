package systems.lupine.sheaf.ui.pkapiimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import systems.lupine.sheaf.data.model.PKImportResult
import systems.lupine.sheaf.data.model.PKPreviewSummary
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluralKitApiImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: PluralKitApiImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import from PluralKit (API)") },
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
                    onImport = { viewModel.runImport() },
                )
                state.isPreviewing -> CenterSpinner("Talking to PluralKit…")
                else -> TokenSection(
                    token = state.token,
                    onTokenChange = { viewModel.updateToken(it) },
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
private fun TokenSection(
    token: String,
    onTokenChange: (String) -> Unit,
    onPreview: () -> Unit,
) {
    var showToken by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
        Text(
            "Paste your PluralKit token to fetch your system directly. " +
                "Run `pk;token` in DM with PluralKit on Discord; PK will reply with the token.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text("PluralKit token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showToken) "Hide token" else "Show token",
                    )
                }
            },
        )
        Text(
            "The token is sent over HTTPS to Sheaf, used once for the import job, encrypted while the job runs, and wiped on completion. It is never logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Button(
            onClick = onPreview,
            enabled = token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Preview") }
    }
}

@Composable
private fun PreviewSection(
    preview: PKPreviewSummary,
    options: PKApiImportOptions,
    onUpdateOptions: (PKApiImportOptions.() -> PKApiImportOptions) -> Unit,
    onImport: () -> Unit,
) {
    SectionHeader("Preview")

    if (preview.systemName != null) {
        Text(
            preview.systemName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
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

    // Switch count on API preview is a sampled summary (PK paginates them),
    // so we can't show "exact N entries" — just offer the toggle.
    ToggleRow(
        label = "Front history (switch log)",
        checked = options.frontHistory,
        onCheckedChange = { onUpdateOptions { copy(frontHistory = it) } },
    )
    if (options.frontHistory) {
        Text(
            "PluralKit will paginate the switch log over multiple requests; the import job continues in the background and shows up under Import history while it's running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text("Import another system")
    }
}

@Composable
private fun ResultRow(label: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
