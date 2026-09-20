package systems.lupine.sheaf.ui.importflow

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
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.importcommon.ImportResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateUp: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val source = viewModel.source

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }
    val pickFile = { filePicker.launch(arrayOf("*/*")) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text(source.title) },
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
                    restartLabel = source.restartLabel,
                    onImportAnother = { viewModel.reset() },
                )

                state.isImporting -> Waiting("Importing…")

                state.preview != null -> PreviewSection(
                    fileName = state.fileName,
                    preview = state.preview!!,
                    selected = state.selected,
                    selectedMemberIds = state.selectedMemberIds,
                    onSetCategory = viewModel::setCategory,
                    onSetAllMembers = viewModel::setAllMembers,
                    onToggleMember = viewModel::toggleMember,
                    onImport = { viewModel.runImport() },
                    onChangeFile = { pickFile() },
                )

                state.isPreviewing -> Waiting(
                    if (source.input is ImportInput.Token) "Fetching…" else "Reading file…"
                )

                else -> InputSection(
                    input = source.input,
                    fileName = state.fileName,
                    credential = state.credential,
                    onPickFile = { pickFile() },
                    onCredentialChange = viewModel::updateCredential,
                    onPreview = { viewModel.runPreview() },
                )
            }
        }
    }
}

@Composable
private fun Waiting(label: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Input ─────────────────────────────────────────────────────────────────────

@Composable
private fun InputSection(
    input: ImportInput,
    fileName: String?,
    credential: String,
    onPickFile: () -> Unit,
    onCredentialChange: (String) -> Unit,
    onPreview: () -> Unit,
) {
    when (input) {
        is ImportInput.File -> Column(
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
                input.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onPickFile) { Text("Choose file") }
            Text(
                input.help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        is ImportInput.EncryptedFile -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                input.help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            SecretField(input.fieldLabel, credential, onCredentialChange)
            Button(
                onClick = onPreview,
                enabled = fileName != null && credential.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(input.action) }
        }

        is ImportInput.Token -> Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                input.help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecretField(input.fieldLabel, credential, onCredentialChange)
            Text(
                input.footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Button(
                onClick = onPreview,
                enabled = credential.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(input.action) }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onValueChange: (String) -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { reveal = !reveal }) {
                Icon(
                    if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (reveal) "Hide $label" else "Show $label",
                )
            }
        },
    )
}

// ── Preview + options ─────────────────────────────────────────────────────────

@Composable
private fun PreviewSection(
    fileName: String?,
    preview: ImportPreview,
    selected: Map<String, Boolean>,
    selectedMemberIds: Set<String>?,
    onSetCategory: (String, Boolean) -> Unit,
    onSetAllMembers: (Boolean) -> Unit,
    onToggleMember: (String) -> Unit,
    onImport: () -> Unit,
    onChangeFile: () -> Unit,
) {
    val memberIds = selectedMemberIds ?: preview.members.map { it.id }.toSet()

    if (fileName != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = onChangeFile) { Text("Change") }
        }
    }

    if (preview.systemName != null) {
        Text(
            "System: ${preview.systemName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }

    if (preview.headline != null) {
        Text(
            preview.headline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider()
    SectionHeader("What to import")

    // The system profile row sits above the roster on every source that has
    // one; everything else follows the members it applies to.
    val profile = preview.categories.firstOrNull { it.key == "system_profile" }
    if (profile != null && profile.visible) {
        CategoryRow(profile, selected[profile.key] == true, onSetCategory)
    }

    preview.locked.forEach { label ->
        ToggleRow(label = label, checked = true, onCheckedChange = {}, enabled = false)
    }

    if (preview.members.isNotEmpty()) {
        ToggleRow(
            label = "Members (${preview.memberCount})",
            checked = memberIds.isNotEmpty(),
            onCheckedChange = onSetAllMembers,
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            preview.members.forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = member.id in memberIds,
                        onCheckedChange = { onToggleMember(member.id) },
                    )
                    Text(member.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    preview.categories
        .filter { it.visible && it.key != "system_profile" }
        .forEach { CategoryRow(it, selected[it.key] == true, onSetCategory) }

    preview.skipped.forEach { note ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(12.dp))
            Text(
                note,
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
private fun CategoryRow(
    category: ImportCategory,
    checked: Boolean,
    onSetCategory: (String, Boolean) -> Unit,
) {
    ToggleRow(
        label = category.label,
        checked = checked,
        onCheckedChange = { onSetCategory(category.key, it) },
    )
    if (checked && category.note != null) {
        Text(
            category.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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

// ── Result ────────────────────────────────────────────────────────────────────

@Composable
private fun ResultSection(
    result: ImportResult,
    restartLabel: String,
    onImportAnother: () -> Unit,
) {
    SectionHeader("Import complete")

    val rows = result.rows()
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rows.isEmpty()) {
                Text("Nothing new to import.", style = MaterialTheme.typography.bodyMedium)
            } else {
                rows.forEach { (label, count) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
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
        Text(restartLabel)
    }
}
