package systems.lupine.sheaf.ui.members

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.*

// ── Members list ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onMemberClick: (String) -> Unit,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var contextMenuMember by remember { mutableStateOf<MemberRead?>(null) }
    var memberToDelete by remember { mutableStateOf<MemberRead?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Members") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onMemberClick("new") }) {
                Icon(Icons.Default.Add, contentDescription = "Add member")
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
            state.members.isEmpty() -> EmptyState(
                icon = Icons.Default.PersonAdd,
                title = "No members yet",
                subtitle = "Tap + to add your first system member.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 0.dp, end = 0.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
            ) {
                items(state.members) { member ->
                    Box {
                        MemberListItem(
                            member = member,
                            onClick = { onMemberClick(member.id) },
                            onLongClick = { contextMenuMember = member },
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        DropdownMenu(
                            expanded = contextMenuMember?.id == member.id,
                            onDismissRequest = { contextMenuMember = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to front") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                onClick = {
                                    viewModel.addToFront(member.id)
                                    contextMenuMember = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Switch front to only them") },
                                leadingIcon = { Icon(Icons.Default.SwitchAccount, contentDescription = null) },
                                onClick = {
                                    viewModel.switchFrontToOnly(member.id)
                                    contextMenuMember = null
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Remove member", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    memberToDelete = member
                                    contextMenuMember = null
                                },
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }

    if (memberToDelete != null) {
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text("Remove member?") },
            text = { Text("This will permanently delete ${memberToDelete!!.displayNameOrName}. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteMember(memberToDelete!!.id); memberToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Member detail / edit / create ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: String,
    onNavigateUp: () -> Unit,
    viewModel: MemberDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form  by viewModel.form.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Navigate away on save/delete
    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onNavigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isNewMember) "New Member" else form.name.ifBlank { "Member" })
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNewMember) {
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

            // Avatar preview
            if (!viewModel.isNewMember && state.member != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MemberAvatar(member = state.member!!, size = 72.dp)
                }
            }

            if (state.error != null) ErrorBanner(state.error!!)

            // ── Form fields ───────────────────────────────────────────────────

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.updateForm { copy(name = it) } },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.displayName,
                onValueChange = { viewModel.updateForm { copy(displayName = it) } },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.pronouns,
                onValueChange = { viewModel.updateForm { copy(pronouns = it) } },
                label = { Text("Pronouns") },
                placeholder = { Text("e.g. they/them") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.birthday,
                onValueChange = { viewModel.updateForm { copy(birthday = it) } },
                label = { Text("Birthday") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Color picker row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Colour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorSwatch(hex = form.color, size = 36.dp)
                Text(form.color, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Quick palette
            ColorPalette(selected = form.color, onSelect = { viewModel.updateForm { copy(color = it) } })

            // Privacy
            SectionHeader("Privacy")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("public", "friends", "private").forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = form.privacy == level,
                        onClick = { viewModel.updateForm { copy(privacy = level) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) { Text(level.replaceFirstChar { it.uppercase() }) }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text(if (viewModel.isNewMember) "Add Member" else "Save Changes")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete member?") },
            text = { Text("This will permanently delete ${form.name}. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Colour palette ─────────────────────────────────────────────────────────────

private val palette = listOf(
    "#7F77DD", "#534AB7", "#1D9E75", "#E24B4A",
    "#BA7517", "#185FA5", "#D4537E", "#888780",
    "#639922", "#D85A30",
)

@Composable
private fun ColorPalette(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        palette.forEach { hex ->
            val color = parseColor(hex) ?: return@forEach
            val isSelected = hex.equals(selected, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(hex) },
                label = {},
                leadingIcon = { Box(Modifier.size(16.dp).padding(1.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = color,
                    selectedContainerColor = color,
                ),
                border = if (isSelected) FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = true,
                    borderColor = MaterialTheme.colorScheme.primary,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    selectedBorderWidth = 2.dp,
                ) else FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                ),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
