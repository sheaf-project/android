@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ")

package systems.lupine.sheaf.ui.fields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.CustomFieldRead
import systems.lupine.sheaf.ui.components.*

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun fieldTypeIcon(fieldType: String): ImageVector = when (fieldType) {
    "text"        -> Icons.Outlined.TextFields
    "number"      -> Icons.Outlined.Tag
    "date"        -> Icons.Outlined.CalendarMonth
    "boolean"     -> Icons.Outlined.ToggleOn
    "select"      -> Icons.AutoMirrored.Outlined.List
    "multiselect" -> Icons.Outlined.Checklist
    else          -> Icons.AutoMirrored.Outlined.List
}

private val privacyOptions  = listOf("private", "friends", "public")
private val fieldTypeOptions = listOf("text", "number", "date", "boolean", "select", "multiselect")

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsScreen(
    onNavigateUp: () -> Unit,
    viewModel: CustomFieldsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var showAddSheet    by remember { mutableStateOf(false) }
    var editingField    by remember { mutableStateOf<CustomFieldRead?>(null) }
    var deletingField   by remember { mutableStateOf<CustomFieldRead?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Custom Fields") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add field")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.error != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(state.error!!)
                Button(onClick = { viewModel.clearError(); viewModel.load() }) { Text("Retry") }
            }

            state.fields.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "No custom fields yet",
                subtitle = "Custom fields let you store extra info on each member.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp, // clear FAB
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.fields, key = { it.id }) { field ->
                    FieldListItem(
                        field = field,
                        onClick = { editingField = field },
                        onDeleteClick = { deletingField = field },
                    )
                }
            }
        }
    }

    // ── Add field bottom sheet ─────────────────────────────────────────────────

    if (showAddSheet) {
        AddFieldSheet(
            isSaving = state.isSaving,
            onDismiss = { showAddSheet = false },
            onSave = { name, fieldType, privacy ->
                viewModel.createField(name, fieldType, privacy)
                showAddSheet = false
            },
        )
    }

    // ── Edit field dialog ──────────────────────────────────────────────────────

    editingField?.let { field ->
        EditFieldDialog(
            field = field,
            isSaving = state.isSaving,
            onDismiss = { editingField = null },
            onSave = { name, privacy ->
                viewModel.updateField(field.id, name, privacy)
                editingField = null
            },
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────

    deletingField?.let { field ->
        AlertDialog(
            onDismissRequest = { deletingField = null },
            title = { Text("Delete field?") },
            text = { Text("\"${field.name}\" and all its values will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteField(field.id); deletingField = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingField = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Field list item ───────────────────────────────────────────────────────────

@Composable
private fun FieldListItem(
    field: CustomFieldRead,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(field.name, style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(field.fieldTypeDisplay, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(
                imageVector = fieldTypeIcon(field.fieldType),
                contentDescription = field.fieldTypeDisplay,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            field.privacyDisplay,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${field.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    )
    HorizontalDivider()
}

// ── Add field bottom sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, fieldType: String, privacy: String) -> Unit,
) {
    var name      by remember { mutableStateOf("") }
    var fieldType by remember { mutableStateOf(fieldTypeOptions.first()) }
    var privacy   by remember { mutableStateOf(privacyOptions.first()) }
    var expanded  by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add Custom Field", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Field name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = fieldType.replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Field type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    fieldTypeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.replaceFirstChar { it.uppercase() }) },
                            onClick = { fieldType = option; expanded = false },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Privacy",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    privacyOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = privacy == option,
                            onClick = { privacy = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = privacyOptions.size,
                            ),
                        ) {
                            Text(option.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            Button(
                onClick = { onSave(name.trim(), fieldType, privacy) },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Add Field")
                }
            }
        }
    }
}

// ── Edit field dialog ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFieldDialog(
    field: CustomFieldRead,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, privacy: String) -> Unit,
) {
    var name    by remember(field.id) { mutableStateOf(field.name) }
    var privacy by remember(field.id) { mutableStateOf(field.privacy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Field name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "Type: ${field.fieldTypeDisplay}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Privacy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        privacyOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = privacy == option,
                                onClick = { privacy = option },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = privacyOptions.size,
                                ),
                            ) {
                                Text(option.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), privacy) },
                enabled = !isSaving && name.isNotBlank(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
