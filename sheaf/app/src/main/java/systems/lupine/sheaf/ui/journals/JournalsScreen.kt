package systems.lupine.sheaf.ui.journals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import systems.lupine.sheaf.data.model.ContentRevisionRead
import systems.lupine.sheaf.data.model.JournalEntryRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.*
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// ── Journals list ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JournalsScreen(
    onEntryClick: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: JournalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(listState, state.entries.size, state.nextCursor) {
        snapshotFlowAtBottom(listState).collect { atBottom ->
            if (atBottom && state.nextCursor != null && !state.isLoadingMore) {
                viewModel.loadMore()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SheafLargeFlexibleTopAppBar(
                title = { Text("Journals") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEntryClick("new") }) {
                Icon(Icons.Default.Add, contentDescription = "New journal entry")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterRow(
                filter = state.filter,
                onChange = { viewModel.setFilter(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null && state.entries.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ErrorBanner(state.error!!)
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
                state.entries.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "No journal entries yet",
                    subtitle = "Tap + to write your first entry.",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        JournalCard(
                            entry = entry,
                            member = entry.memberId?.let { state.members[it] },
                            onClick = { onEntryClick(entry.id) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: JournalFilter,
    onChange: (JournalFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == JournalFilter.ALL,
            onClick = { onChange(JournalFilter.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == JournalFilter.SYSTEM_ONLY,
            onClick = { onChange(JournalFilter.SYSTEM_ONLY) },
            label = { Text("System-wide") },
        )
    }
}

@Composable
private fun JournalCard(
    entry: JournalEntryRead,
    member: MemberRead?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatJournalDate(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            MarkdownText(
                markdown = entry.body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                ),
                maxLines = 3,
                truncateOnTextOverflow = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (member != null) {
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            member.displayNameOrName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Text(
                        "System-wide",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                if (entry.authorMemberNames.isNotEmpty()) {
                    Text(
                        "· by ${entry.authorMemberNames.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// Lazy "is at bottom" detector — emits true once when the last item is visible.
private fun snapshotFlowAtBottom(state: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.runtime.snapshotFlow {
        val layout = state.layoutInfo
        val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= 0 && last >= layout.totalItemsCount - 2
    }

// ── Journal detail / editor ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: JournalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form  by viewModel.form.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMemberPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onNavigateUp()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = {
                    Text(
                        when {
                            viewModel.isNewEntry -> "New Entry"
                            state.isEditing -> "Edit Entry"
                            else -> form.title.ifBlank { "Untitled" }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isEditing && !viewModel.isNewEntry) viewModel.cancelEditing()
                        else onNavigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNewEntry && !state.isEditing && state.entry != null) {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.toggleRevisions() }) {
                            Icon(Icons.Default.History, contentDescription = "Revisions")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
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

            if (state.isEditing) {
                JournalEditor(
                    form = form,
                    members = state.members,
                    isNew = viewModel.isNewEntry,
                    onUpdate = viewModel::updateForm,
                    onPickMember = { showMemberPicker = true },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.cancelEditing() },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Cancel") }
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && form.body.isNotBlank(),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(if (viewModel.isNewEntry) "Create" else "Save")
                        }
                    }
                }
            } else {
                JournalReader(entry = state.entry, members = state.members)
            }
        }
    }

    if (state.showRevisions) {
        ModalBottomSheet(onDismissRequest = { viewModel.toggleRevisions() }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Revisions (${state.revisions.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp, 12.dp),
                )
                HorizontalDivider()
                if (state.revisions.isEmpty()) {
                    Text(
                        "No prior revisions for this entry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(state.revisions, key = { it.id }) { rev ->
                            RevisionRow(
                                revision = rev,
                                isRestoring = state.isRestoring,
                                onRestore = { viewModel.restoreRevision(rev.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (showMemberPicker) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(query, state.members) {
            if (query.isBlank()) state.members
            else state.members.filter { it.displayNameOrName.contains(query.trim(), ignoreCase = true) }
        }
        val showSystemWide = query.isBlank() ||
            "system-wide".contains(query.trim(), ignoreCase = true)

        ModalBottomSheet(onDismissRequest = { showMemberPicker = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Attach to member",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp, 12.dp),
                )
                MemberSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    if (showSystemWide) {
                        item {
                            ListItem(
                                headlineContent = { Text("System-wide") },
                                supportingContent = { Text("Not attached to any member") },
                                leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                                trailingContent = {
                                    if (form.memberId == null) Icon(Icons.Default.Check, contentDescription = "Selected")
                                },
                                modifier = Modifier.clickable {
                                    viewModel.updateForm { copy(memberId = null) }
                                    showMemberPicker = false
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                    items(filtered, key = { it.id }) { member ->
                        ListItem(
                            headlineContent = { Text(member.displayNameOrName) },
                            leadingContent = { MemberAvatar(member, size = 40.dp) },
                            trailingContent = {
                                if (form.memberId == member.id) Icon(Icons.Default.Check, contentDescription = "Selected")
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateForm { copy(memberId = member.id) }
                                showMemberPicker = false
                            },
                        )
                    }
                    if (filtered.isEmpty() && !showSystemWide) {
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
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete entry?") },
            text = {
                Text(
                    "This will permanently delete the entry. " +
                        "If your system has safety enabled, deletion will be queued.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun JournalReader(
    entry: systems.lupine.sheaf.data.model.JournalEntryReadWithCount?,
    members: List<MemberRead>,
) {
    if (entry == null) return
    val attachedMember = entry.memberId?.let { id -> members.firstOrNull { it.id == id } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            entry.title?.takeIf { it.isNotBlank() } ?: "Untitled",
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatJournalDate(entry.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.updatedAt != entry.createdAt) {
                Text(
                    "· edited ${formatJournalDate(entry.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (attachedMember != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberAvatar(attachedMember, size = 28.dp)
                Text(attachedMember.displayNameOrName, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(
                "System-wide entry",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.authorMemberNames.isNotEmpty()) {
            Text(
                "By: ${entry.authorMemberNames.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(4.dp))
        MarkdownText(
            markdown = entry.body,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        if (entry.revisionCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${entry.revisionCount} prior revision${if (entry.revisionCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JournalEditor(
    form: JournalFormState,
    members: List<MemberRead>,
    isNew: Boolean,
    onUpdate: (JournalFormState.() -> JournalFormState) -> Unit,
    onPickMember: () -> Unit,
) {
    var previewMode by rememberSaveable { mutableStateOf(false) }

    PrimaryTabRow(
        selectedTabIndex = if (previewMode) 1 else 0,
        containerColor = Color.Transparent,
    ) {
        Tab(
            selected = !previewMode,
            onClick = { previewMode = false },
            text = { Text("Write") },
        )
        Tab(
            selected = previewMode,
            onClick = { previewMode = true },
            text = { Text("Preview") },
        )
    }

    if (!previewMode) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { onUpdate { copy(title = it) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.body,
            onValueChange = { onUpdate { copy(body = it) } },
            label = { Text("Body *") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            form.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (form.body.isBlank()) {
            Text(
                "Nothing to preview yet — switch back to Write to start typing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MarkdownText(
                markdown = form.body,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }

    if (isNew) {
        val attached = form.memberId?.let { id -> members.firstOrNull { it.id == id } }
        OutlinedButton(
            onClick = onPickMember,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(attached?.let { "Attach: ${it.displayNameOrName}" } ?: "Attach to member (optional)")
        }
    }
}

@Composable
private fun RevisionRow(
    revision: ContentRevisionRead,
    isRestoring: Boolean,
    onRestore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            revision.title?.takeIf { it.isNotBlank() } ?: "Untitled",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            formatJournalDate(revision.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            revision.body,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (revision.editorMemberNames.isNotEmpty()) {
            Text(
                "Edited by ${revision.editorMemberNames.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        TextButton(onClick = onRestore, enabled = !isRestoring) {
            Text(if (isRestoring) "Restoring…" else "Restore this version")
        }
    }
}

private val journalDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")

private fun formatJournalDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).toLocalDateTime().format(journalDateFormatter)
}.getOrDefault(iso)
