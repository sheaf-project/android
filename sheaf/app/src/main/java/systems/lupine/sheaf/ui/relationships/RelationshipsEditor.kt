@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.relationships

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.REL_DIRECTION_INCOMING
import systems.lupine.sheaf.data.model.REL_DIRECTION_OUTGOING
import systems.lupine.sheaf.data.model.RelationshipFromViewpoint
import systems.lupine.sheaf.data.model.RelationshipTypeRead
import systems.lupine.sheaf.data.model.SYMMETRY_DIRECTIONAL
import systems.lupine.sheaf.data.model.SYMMETRY_EITHER
import systems.lupine.sheaf.data.model.SYMMETRY_SYMMETRIC

/**
 * Reusable relationships section for a member or group. Loads the node's edges
 * (server-resolved labels + direction), lists them, and - unless [readOnly] -
 * offers an add form. Renders as a plain column section (no scaffold) so it can
 * be dropped into an existing editor/profile screen. When [readOnly] and there
 * are no relationships, renders nothing.
 */
@Composable
fun RelationshipsEditor(
    scope: String,
    nodeId: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    viewModel: RelationshipsEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(scope, nodeId) { viewModel.load(scope, nodeId) }
    val state by viewModel.state.collectAsState()

    if (readOnly && !state.isLoading && state.relationships.isEmpty()) return

    val noun = if (scope == REL_SCOPE_GROUP) "group" else "member"

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Relationships",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            // A failed load is no longer latched, so this really does re-run it.
            if (!state.isLoading && state.relationships.isEmpty() && state.types.isEmpty()) {
                TextButton(onClick = { viewModel.retry() }) { Text("Retry") }
            }
        }

        when {
            state.isLoading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            state.relationships.isEmpty() && readOnly -> {} // handled by early return
            state.relationships.isEmpty() -> Text(
                "No relationships yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> state.relationships.forEach { rel ->
                RelationshipRow(
                    rel = rel,
                    otherName = state.nameById[rel.otherId] ?: "Unknown",
                    readOnly = readOnly,
                    onRemove = { viewModel.remove(rel.id) },
                )
            }
        }

        if (!readOnly && !state.isLoading) {
            if (state.types.isEmpty()) {
                Text(
                    "Define a relationship type in Settings > System > Relationships first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AddRelationshipForm(
                    noun = noun,
                    types = state.types,
                    candidates = state.candidates,
                    isSaving = state.isSaving,
                    onAdd = { other, type, forward, mutual ->
                        viewModel.add(buildRelationshipEdge(nodeId, other.id, type, forward, mutual))
                    },
                )
            }
        }
    }
}

private fun directionGlyph(direction: String): ImageVector = when (direction) {
    REL_DIRECTION_OUTGOING -> Icons.AutoMirrored.Filled.ArrowForward
    REL_DIRECTION_INCOMING -> Icons.AutoMirrored.Filled.ArrowBack
    else -> Icons.Filled.SwapHoriz
}

@Composable
private fun RelationshipRow(
    rel: RelationshipFromViewpoint,
    otherName: String,
    readOnly: Boolean,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            directionGlyph(rel.direction),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${rel.label}: $otherName",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        if (!readOnly) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${rel.label} relationship with $otherName")
            }
        }
    }
}

@Composable
private fun AddRelationshipForm(
    noun: String,
    types: List<RelationshipTypeRead>,
    candidates: List<RelationshipNodeRef>,
    isSaving: Boolean,
    onAdd: (other: RelationshipNodeRef, type: RelationshipTypeRead, forward: Boolean, mutual: Boolean) -> Unit,
) {
    var type by remember { mutableStateOf<RelationshipTypeRead?>(null) }
    var other by remember { mutableStateOf<RelationshipNodeRef?>(null) }
    var forward by remember { mutableStateOf(true) }
    var mutual by remember { mutableStateOf(false) }

    val sym = type?.symmetry
    val showDirection = (sym == SYMMETRY_DIRECTIONAL || sym == SYMMETRY_EITHER) && !(sym == SYMMETRY_EITHER && mutual)
    val showMutual = sym == SYMMETRY_EITHER

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider()

        PickerDropdown(
            label = "Type",
            value = type?.name,
            options = types.map { it.id to it.name },
            onPick = { id ->
                type = types.first { it.id == id }
                forward = true
                mutual = false
            },
        )

        PickerDropdown(
            label = "Other $noun",
            value = other?.name,
            options = candidates.map { it.id to it.name },
            onPick = { id -> other = candidates.first { it.id == id } },
        )

        if (showMutual) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().selectable(selected = mutual, onClick = { mutual = !mutual }),
            ) {
                Checkbox(checked = mutual, onCheckedChange = { mutual = it })
                Text("Mutual (both are ${type?.forwardLabel})", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (showDirection) {
            val t = type!!
            Column {
                DirectionOption(
                    text = "This $noun is the ${t.forwardLabel}",
                    selected = forward,
                    onClick = { forward = true },
                )
                DirectionOption(
                    text = "This $noun is the ${t.reverseLabel ?: t.forwardLabel}",
                    selected = !forward,
                    onClick = { forward = false },
                )
            }
        }

        Button(
            onClick = {
                val ty = type; val ot = other
                if (ty != null && ot != null) {
                    onAdd(ot, ty, forward, mutual)
                    type = null; other = null; forward = true; mutual = false
                }
            },
            enabled = type != null && other != null && !isSaving,
        ) {
            if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Add relationship")
        }
    }
}

@Composable
private fun DirectionOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PickerDropdown(
    label: String,
    value: String?,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Choose...") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("None available") }, onClick = { expanded = false }, enabled = false)
            }
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPick(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
