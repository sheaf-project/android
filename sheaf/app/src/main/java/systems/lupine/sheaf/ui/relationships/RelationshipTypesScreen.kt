@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.relationships

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.RELATIONSHIP_PRESETS
import systems.lupine.sheaf.data.model.RelationshipPreset
import systems.lupine.sheaf.data.model.RelationshipTypeCreate
import systems.lupine.sheaf.data.model.RelationshipTypeRead
import systems.lupine.sheaf.data.model.RelationshipTypeUpdate
import systems.lupine.sheaf.data.model.SYMMETRY_DIRECTIONAL
import systems.lupine.sheaf.data.model.SYMMETRY_EITHER
import systems.lupine.sheaf.data.model.SYMMETRY_SYMMETRIC
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

// Symmetry ("Kind") options as shown to the user, in creation order.
private val SYMMETRY_OPTIONS = listOf(
    SYMMETRY_SYMMETRIC to "Symmetric (one label)",
    SYMMETRY_DIRECTIONAL to "Directional (two labels)",
    SYMMETRY_EITHER to "Either (both / mutual)",
)

private fun symmetryLabel(value: String): String =
    SYMMETRY_OPTIONS.firstOrNull { it.first == value }?.second ?: value

@Composable
fun RelationshipTypesScreen(
    onNavigateUp: () -> Unit,
    viewModel: RelationshipTypesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<RelationshipTypeRead?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RelationshipTypeRead?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Relationships") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New type") },
                // Label the FAB directly; Material wraps the text slot in an
                // AnimatedVisibility that can drop its semantics.
                modifier = Modifier.semantics { contentDescription = "New relationship type" },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(16.dp)) }
            Text(
                "Define the kinds of relationship you can draw between your members or groups. " +
                    "Add the relationships themselves from each member or group.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.types.isEmpty() -> Text(
                    "No relationship types yet. Tap + to create one (or start from a preset).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                )
                else -> state.types.forEach { type ->
                    ListItem(
                        headlineContent = { Text(type.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(type.summary) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = type }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit ${type.name}")
                                }
                                IconButton(onClick = { deleteTarget = type }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${type.name}")
                                }
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    if (creating) {
        RelationshipTypeDialog(
            existing = null,
            isSaving = state.isSaving,
            onDismiss = { creating = false },
            onSubmitCreate = { body -> viewModel.create(body) { creating = false } },
            onSubmitUpdate = { _, _ -> },
        )
    }
    editing?.let { type ->
        RelationshipTypeDialog(
            existing = type,
            isSaving = state.isSaving,
            onDismiss = { editing = null },
            onSubmitCreate = { },
            onSubmitUpdate = { id, body -> viewModel.update(id, body) { editing = null } },
        )
    }
    deleteTarget?.let { type ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${type.name}\"?") },
            text = {
                Text(
                    "This also removes every relationship between members or groups that uses " +
                        "this type. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(type.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RelationshipTypeDialog(
    existing: RelationshipTypeRead?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmitCreate: (RelationshipTypeCreate) -> Unit,
    onSubmitUpdate: (String, RelationshipTypeUpdate) -> Unit,
) {
    val isEdit = existing != null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var symmetry by remember { mutableStateOf(existing?.symmetry ?: SYMMETRY_SYMMETRIC) }
    var forwardLabel by remember { mutableStateOf(existing?.forwardLabel ?: "") }
    var reverseLabel by remember { mutableStateOf(existing?.reverseLabel ?: "") }

    val symmetric = symmetry == SYMMETRY_SYMMETRIC
    val valid = name.isNotBlank() && forwardLabel.isNotBlank() && (symmetric || reverseLabel.isNotBlank())

    fun applyPreset(p: RelationshipPreset) {
        name = p.name
        symmetry = p.symmetry
        forwardLabel = p.forwardLabel
        reverseLabel = p.reverseLabel ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit relationship type" else "New relationship type") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isEdit) {
                    PresetDropdown(onPick = ::applyPreset)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Partner") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isEdit) {
                    // symmetry is immutable server-side; show it read-only.
                    Text(
                        "Kind: ${symmetryLabel(symmetry)} (fixed - delete and recreate to change)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    KindDropdown(value = symmetry, onChange = { symmetry = it })
                }
                OutlinedTextField(
                    value = forwardLabel,
                    onValueChange = { forwardLabel = it },
                    label = { Text(if (symmetric) "Label" else "Forward label (source side)") },
                    placeholder = { Text(if (symmetric) "e.g. partner" else "e.g. parent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!symmetric) {
                    OutlinedTextField(
                        value = reverseLabel,
                        onValueChange = { reverseLabel = it },
                        label = { Text("Reverse label (target side)") },
                        placeholder = { Text("e.g. child") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !isSaving,
                onClick = {
                    val reverse = if (symmetric) null else reverseLabel.trim()
                    if (isEdit) {
                        onSubmitUpdate(
                            existing!!.id,
                            RelationshipTypeUpdate(
                                name = name.trim(),
                                forwardLabel = forwardLabel.trim(),
                                reverseLabel = reverse,
                            ),
                        )
                    } else {
                        onSubmitCreate(
                            RelationshipTypeCreate(
                                name = name.trim(),
                                symmetry = symmetry,
                                forwardLabel = forwardLabel.trim(),
                                reverseLabel = reverse,
                            ),
                        )
                    }
                },
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (isEdit) "Save" else "Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PresetDropdown(onPick: (RelationshipPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Start from a preset (optional)") },
            placeholder = { Text("Choose...") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RELATIONSHIP_PRESETS.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onPick(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun KindDropdown(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = symmetryLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text("Kind") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SYMMETRY_OPTIONS.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onChange(v)
                        expanded = false
                    },
                )
            }
        }
    }
}
