package systems.lupine.sheaf.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.ImportJobEvent
import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobStatus
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportJobDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: ImportJobDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.actionDone) {
        if (state.actionDone) onNavigateUp()
    }

    val job = state.job
    val actionability = job?.let { jobActionability(it) } ?: JobActionability.NONE

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Import details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    if (actionability != JobActionability.NONE) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                val (label, icon) = when (actionability) {
                                    JobActionability.CANCEL ->
                                        "Cancel import" to Icons.Outlined.Cancel
                                    JobActionability.ARCHIVE ->
                                        "Archive" to Icons.Outlined.Archive
                                    JobActionability.NONE -> "" to Icons.Outlined.Archive
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(icon, contentDescription = null) },
                                    enabled = !state.isActioning,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.cancelOrArchive()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && job == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                job != null -> JobDetailBody(
                    job = job,
                    error = state.error,
                    isActioning = state.isActioning,
                )
                state.error != null -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { ErrorBanner(state.error!!) }
            }
        }
    }
}

@Composable
private fun JobDetailBody(
    job: ImportJobRead,
    error: String?,
    isActioning: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null) ErrorBanner(error)
        if (isActioning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }
        }
        HeaderCard(job)
        if (job.counts.isNotEmpty()) CountsCard(job.counts)
        if (job.lastError != null) ErrorCard(job.lastError)
        if (job.events.isNotEmpty()) EventsCard(job.events)
    }
}

@Composable
private fun HeaderCard(job: ImportJobRead) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    importSourceLabel(job.source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = job.status)
            }
            Spacer(Modifier.height(8.dp))
            val startedLabel = relativeTimestamp(job.startedAt ?: job.createdAt)
            val finishedLabel = relativeTimestamp(job.finishedAt)
            if (startedLabel != null) {
                DetailRow(label = "Started", value = startedLabel)
            }
            if (finishedLabel != null) {
                DetailRow(label = "Finished", value = finishedLabel)
            }
            if (job.archivedAt != null) {
                DetailRow(label = "Archived", value = relativeTimestamp(job.archivedAt) ?: job.archivedAt)
            }
        }
    }
}

@Composable
private fun CountsCard(counts: Map<String, Int>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Imported", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            // Display in stable insertion order — the server's runner
            // pushes counts in a sensible order already (members first,
            // then fronts, etc.) so we don't need to re-sort here.
            counts.entries.forEachIndexed { idx, (key, value) ->
                if (idx > 0) HorizontalDivider()
                DetailRow(label = humaniseCountKey(key), value = value.toString())
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last error", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EventsCard(events: List<ImportJobEvent>) {
    // Surface warnings and errors prominently; skip info-level (they're
    // largely "started stage X" / "finished stage X" noise that doesn't
    // help the user diagnose anything).
    val notable = events.filter { it.level == "warning" || it.level == "error" }
    if (notable.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Events (${notable.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            notable.forEachIndexed { idx, event ->
                if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                EventRow(event)
            }
        }
    }
}

@Composable
private fun EventRow(event: ImportJobEvent) {
    val tint = when (event.level) {
        "error"   -> MaterialTheme.colorScheme.error
        "warning" -> MaterialTheme.colorScheme.tertiary
        else      -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.level.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
            if (event.stage.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    " · ${event.stage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            event.message,
            style = MaterialTheme.typography.bodySmall,
        )
        if (event.recordRef != null) {
            Text(
                "Record: ${event.recordRef}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Inline copy of the list-side helper for the same enum-derived label.
 *  Avoids exporting JobActionability from the viewmodel file as public API. */
private fun jobActionability(job: ImportJobRead): JobActionability = when {
    job.status == ImportJobStatus.PENDING -> JobActionability.CANCEL
    job.status in ImportJobStatus.terminal && job.archivedAt == null -> JobActionability.ARCHIVE
    else -> JobActionability.NONE
}

/**
 * Map a counts dict key like "members_imported" to a user-facing label.
 * Source-agnostic: each importer pushes a small set of keys and the
 * vocabulary doesn't overlap enough to need per-source routing.
 */
private fun humaniseCountKey(key: String): String = when (key) {
    "members_imported"        -> "Members"
    "custom_fronts_imported"  -> "Custom fronts"
    "fronts_imported"         -> "Fronts"
    "groups_imported"         -> "Groups"
    "tags_imported"           -> "Tags"
    "custom_fields_imported"  -> "Custom fields"
    "notes_skipped"           -> "Notes skipped"
    "journals_imported"       -> "Journals"
    "messages_imported"       -> "Messages"
    "polls_imported"          -> "Polls"
    "reminders_imported"      -> "Reminders"
    "notifications_imported"  -> "Notifications"
    else -> key
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase() }
}
