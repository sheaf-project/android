package systems.lupine.sheaf.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.ui.components.*
import systems.lupine.sheaf.ui.relationships.REL_SCOPE_GROUP
import systems.lupine.sheaf.ui.relationships.RelationshipsEditor

// ── Group list card ───────────────────────────────────────────────────────────

@Composable
/**
 * A group row: indented by nesting depth, tap to expand its members inline,
 * with an edit affordance. Lives here next to the group detail screen but is
 * rendered by the Groups tab of the People screen, which is the only place
 * groups are listed.
 */
internal fun GroupCard(
    group: systems.lupine.sheaf.data.model.GroupRead,
    depth: Int,
    expanded: Boolean,
    members: List<systems.lupine.sheaf.data.model.MemberRead>?,
    loading: Boolean,
    error: String?,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = parseColor(group.color ?: "#534AB7") ?: MaterialTheme.colorScheme.primary
    Card(
        onClick = onToggleExpand,
        // Indent subgroups under their parent. Capped so deep nesting stays
        // usable on a narrow screen.
        modifier = Modifier.fillMaxWidth().padding(start = (minOf(depth, 4) * 16).dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!group.description.isNullOrBlank()) {
                    Text(
                        group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            GroupMembersInline(
                members = members,
                loading = loading,
                error = error,
            )
        }
    }
}

@Composable
private fun GroupMembersInline(
    members: List<systems.lupine.sheaf.data.model.MemberRead>?,
    loading: Boolean,
    error: String?,
) {
    when {
        loading && members == null -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        error != null && members == null -> Text(
            error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
        members != null && members.isEmpty() -> Text(
            "No members in this group.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        members != null -> Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            members.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberAvatar(m, size = 32.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            m.displayNameOrName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        m.pronouns?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Group detail ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateUp: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form  by viewModel.form.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val descriptionImagePicker = rememberMarkdownImagePicker(
        viewModel.markdownImages,
        viewModel.viewModelScope,
    )

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onNavigateUp()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text(if (viewModel.isNewGroup) "New Group" else form.name.ifBlank { "Group" }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNewGroup) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.error != null) ErrorBanner(state.error!!)

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.updateForm { copy(name = it) } },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MarkdownBodyEditor(
                value = form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = "Description",
                minLines = 3,
                imagePicker = descriptionImagePicker,
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorSwatch(hex = form.color, size = 36.dp)
                Text(form.color, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GroupColorPalette(selected = form.color, onSelect = { viewModel.updateForm { copy(color = it) } })

            // Parent group (subgroups). Exclude this group and its descendants
            // so a group can't become its own ancestor; the server also caps
            // nesting depth.
            val currentId = if (viewModel.isNewGroup) null else groupId
            val eligibleParents = remember(state.allGroups, currentId) {
                if (currentId == null) state.allGroups
                else {
                    val descendants = collectDescendants(currentId, state.allGroups)
                    state.allGroups.filter { it.id != currentId && it.id !in descendants }
                }
            }
            ParentGroupDropdown(
                groups = eligibleParents,
                selectedId = form.parentId,
                onSelect = { viewModel.updateForm { copy(parentId = it) } },
            )

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text(if (viewModel.isNewGroup) "Create Group" else "Save Changes")
            }

            // Members section (only for existing groups)
            if (!viewModel.isNewGroup) {
                SectionHeader("Members (${state.members.size})")
                if (state.members.isEmpty()) {
                    Text(
                        "No members in this group.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.members.forEach { member ->
                            MemberListItem(member = member, onClick = {})
                        }
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.openMemberSheet() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Members")
                }

                RelationshipsEditor(
                    scope = REL_SCOPE_GROUP,
                    nodeId = groupId,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete group?") },
            text = { Text("This will permanently delete \"${form.name}\". Members will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }

    if (state.showMemberSheet) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(query, state.allMembers) {
            if (query.isBlank()) state.allMembers
            else state.allMembers.filter { it.displayNameOrName.contains(query.trim(), ignoreCase = true) }
        }

        ModalBottomSheet(onDismissRequest = { viewModel.closeMemberSheet() }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Edit Group Members", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 12.dp))
                MemberSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.id }) { member ->
                        val isSelected = member.id in state.memberSelection
                        ListItem(
                            headlineContent = { Text(member.displayNameOrName) },
                            leadingContent = { MemberAvatar(member, size = 40.dp) },
                            trailingContent = { Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleMember(member.id) }) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "No matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
                HorizontalDivider()
                Button(
                    onClick = { viewModel.saveMembers() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) { Text("Save Members") }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private val groupPalette = listOf(
    "#534AB7", "#7F77DD", "#0F6E56", "#993C1D",
    "#185FA5", "#993556", "#3B6D11", "#854F0B",
)

@Composable
private fun GroupColorPalette(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        groupPalette.forEach { hex ->
            val color = parseColor(hex) ?: return@forEach
            FilterChip(
                selected = hex.equals(selected, ignoreCase = true),
                onClick = { onSelect(hex) },
                label = {},
                colors = FilterChipDefaults.filterChipColors(containerColor = color, selectedContainerColor = color),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentGroupDropdown(
    groups: List<systems.lupine.sheaf.data.model.GroupRead>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedId }?.name
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName ?: "None (top-level)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Parent group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None (top-level)") },
                onClick = { onSelect(null); expanded = false },
            )
            groups.forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.name) },
                    onClick = { onSelect(g.id); expanded = false },
                )
            }
        }
    }
}

/** Ids of every group descended from [rootId] (its children, recursively). */
private fun collectDescendants(
    rootId: String,
    all: List<systems.lupine.sheaf.data.model.GroupRead>,
): Set<String> {
    val childrenOf = all.groupBy { it.parentId }
    val result = mutableSetOf<String>()
    val stack = ArrayDeque<String>()
    stack.add(rootId)
    while (stack.isNotEmpty()) {
        childrenOf[stack.removeLast()]?.forEach { child ->
            if (result.add(child.id)) stack.add(child.id)
        }
    }
    return result
}

/**
 * Flatten the group list into parent-before-children order with a depth for
 * each, so the list can indent subgroups under their parent. Roots are groups
 * with no parent (or a parent that isn't in the set); orphans fall back to
 * roots so nothing is dropped.
 */
internal fun orderGroupsHierarchically(
    groups: List<systems.lupine.sheaf.data.model.GroupRead>,
): List<Pair<systems.lupine.sheaf.data.model.GroupRead, Int>> {
    val byId = groups.associateBy { it.id }
    val childrenOf = groups.groupBy { it.parentId }
    val out = mutableListOf<Pair<systems.lupine.sheaf.data.model.GroupRead, Int>>()
    fun visit(group: systems.lupine.sheaf.data.model.GroupRead, depth: Int) {
        out += group to depth
        childrenOf[group.id]?.sortedBy { it.name.lowercase() }?.forEach { visit(it, depth + 1) }
    }
    groups.filter { it.parentId == null || it.parentId !in byId }
        .sortedBy { it.name.lowercase() }
        .forEach { visit(it, 0) }
    return out
}
