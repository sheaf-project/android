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
import systems.lupine.sheaf.ui.components.*

// ── Groups list ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupsScreen(
    onGroupClick: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SheafLargeFlexibleTopAppBar(
                title = { Text("Groups") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onGroupClick("new") }) {
                Icon(Icons.Default.Add, contentDescription = "Add group")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(state.error!!)
                Button(onClick = { viewModel.load() }) { Text("Retry") }
            }
            state.groups.isEmpty() -> EmptyState(
                icon = Icons.Default.FolderOpen,
                title = "No groups yet",
                subtitle = "Tap + to create your first group.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.groups, key = { it.id }) { group ->
                    GroupCard(group = group, onClick = { onGroupClick(group.id) })
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: systems.lupine.sheaf.data.model.GroupRead, onClick: () -> Unit) {
    val accent = parseColor(group.color ?: "#534AB7") ?: MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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

            OutlinedTextField(
                value = form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorSwatch(hex = form.color, size = 36.dp)
                Text(form.color, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GroupColorPalette(selected = form.color, onSelect = { viewModel.updateForm { copy(color = it) } })

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
        ModalBottomSheet(onDismissRequest = { viewModel.closeMemberSheet() }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Edit Group Members", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp, 12.dp))
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(state.allMembers, key = { it.id }) { member ->
                        val isSelected = member.id in state.memberSelection
                        ListItem(
                            headlineContent = { Text(member.displayNameOrName) },
                            leadingContent = { MemberAvatar(member, size = 40.dp) },
                            trailingContent = { Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleMember(member.id) }) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
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
