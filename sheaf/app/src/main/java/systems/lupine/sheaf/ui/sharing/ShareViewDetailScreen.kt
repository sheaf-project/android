package systems.lupine.sheaf.ui.sharing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import systems.lupine.sheaf.data.model.ShareViewRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareViewDetailScreen(
    onBack: () -> Unit,
    viewModel: ShareViewDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<PickerKind?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var detachGroup by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(state.view?.name ?: "Share view") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val view = state.view
        when {
            state.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            state.loadError != null || view == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ErrorBanner(state.loadError ?: "This view is not available")
                Button(onClick = { viewModel.load() }) { Text("Retry") }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.actionError?.let {
                    ErrorBanner(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }

                if (view.hasPendingFlags) {
                    Text(
                        "Some changes to this view are staged and are not live yet. " +
                            "The switches below show what is live now.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                SectionHeader("What this view shows")
                ExposureFlag.entries.forEach { flag ->
                    val live = flag.liveValue(view)
                    val staged = flag.pendingValue(view)
                    ListItem(
                        headlineContent = { Text(flag.label) },
                        supportingContent = {
                            Text(
                                if (staged != null) {
                                    "${flag.supporting}. Staged to turn " +
                                        (if (staged) "on" else "off") + "."
                                } else {
                                    flag.supporting
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = live,
                                onCheckedChange = { viewModel.setFlag(flag, it) },
                                // Turning something on is a publish; turning it
                                // off is always available.
                                enabled = !state.busy && (live || state.publicProfilesEnabled),
                            )
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
                ListItem(
                    headlineContent = { Text("Member permalinks") },
                    supportingContent = {
                        Text("Stable links to members the roster already shows. Exposes nobody new.")
                    },
                    trailingContent = {
                        Switch(
                            checked = view.memberPermalinks,
                            onCheckedChange = { viewModel.setMemberPermalinks(it) },
                            enabled = !state.busy && (view.memberPermalinks || state.publicProfilesEnabled),
                        )
                    },
                )

                SectionHeader("Members in this view")
                view.members.forEach { row ->
                    val name = state.allMembers.find { it.id == row.memberId }?.name ?: "Unknown member"
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { memberStatus(row.status, row.served, row.notServedReason, row.addedViaGroupId)?.let { Text(it) } },
                        trailingContent = {
                            TextButton(onClick = { viewModel.removeMember(row.memberId) }, enabled = !state.busy) {
                                Text("Remove")
                            }
                        },
                    )
                }
                TextButton(
                    onClick = { picking = PickerKind.MEMBER },
                    enabled = !state.busy && state.publicProfilesEnabled,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("Add a member") }

                SectionHeader("Custom fields shown")
                view.fields.forEach { row ->
                    val name = state.allFields.find { it.id == row.fieldId }?.name ?: "Unknown field"
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = if (row.status == "pending") {
                            { Text("Waiting out the grace window") }
                        } else null,
                        trailingContent = {
                            TextButton(onClick = { viewModel.removeField(row.fieldId) }, enabled = !state.busy) {
                                Text("Remove")
                            }
                        },
                    )
                }
                TextButton(
                    onClick = { picking = PickerKind.FIELD },
                    enabled = !state.busy && state.publicProfilesEnabled,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("Add a field") }

                SectionHeader("Groups used as a member picker")
                view.groups.forEach { row ->
                    val name = state.allGroups.find { it.id == row.groupId }?.name ?: "Unknown group"
                    ListItem(
                        headlineContent = { Text(name) },
                        trailingContent = {
                            TextButton(onClick = { detachGroup = row.groupId }, enabled = !state.busy) {
                                Text("Detach")
                            }
                        },
                    )
                }
                TextButton(
                    onClick = { picking = PickerKind.GROUP },
                    enabled = !state.busy && state.publicProfilesEnabled,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("Add a group") }

                SectionHeader("Check and remove")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.loadPreview() },
                        enabled = !state.previewLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Preview as visitor") }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Delete view") }
                }

                androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
            }
        }
    }

    picking?.let { kind ->
        val options = when (kind) {
            PickerKind.MEMBER -> state.allMembers
                .filter { m -> state.view?.members?.none { it.memberId == m.id } == true }
                .map { it.id to it.name }
            PickerKind.FIELD -> state.allFields
                .filter { f -> state.view?.fields?.none { it.fieldId == f.id } == true }
                .map { it.id to it.name }
            PickerKind.GROUP -> state.allGroups
                .filter { g -> state.view?.groups?.none { it.groupId == g.id } == true }
                .map { it.id to it.name }
        }
        PickerDialog(
            title = when (kind) {
                PickerKind.MEMBER -> "Add a member"
                PickerKind.FIELD -> "Add a field"
                PickerKind.GROUP -> "Add a group"
            },
            options = options,
            onPick = { id ->
                when (kind) {
                    PickerKind.MEMBER -> viewModel.addMember(id)
                    PickerKind.FIELD -> viewModel.addField(id)
                    PickerKind.GROUP -> viewModel.addGroup(id)
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }

    detachGroup?.let { groupId ->
        AlertDialog(
            onDismissRequest = { detachGroup = null },
            title = { Text("Detach this group?") },
            text = {
                Text(
                    "You can keep the members it added, or take them out of the view along " +
                        "with the group. Members you picked by hand are never affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeGroup(groupId, true); detachGroup = null }) {
                    Text("Detach and remove members")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.removeGroup(groupId, false); detachGroup = null }) {
                    Text("Keep members")
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this view?") },
            text = { Text("Any links pointing at it stop working immediately.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteView(); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    state.groupAddResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearGroupAddResult() },
            title = { Text("Group added") },
            text = { Text(groupAddSummary(result.added, result.skippedNeverShareable, result.skippedNotPublic)) },
            confirmButton = { TextButton(onClick = { viewModel.clearGroupAddResult() }) { Text("OK") } },
        )
    }

    state.stepUp?.let {
        StepUpSheet(
            authTier = state.authTier,
            totpEnabled = state.totpEnabled,
            isBusy = state.busy,
            errorMessage = state.stepUpError,
            onConfirm = { pw, code -> viewModel.confirmStepUp(pw, code) },
            onDismiss = { viewModel.dismissStepUp() },
        )
    }

    if (state.preview != null || state.previewError != null) {
        PreviewDialog(
            preview = state.preview,
            error = state.previewError,
            onDismiss = { viewModel.clearPreview() },
        )
    }
}

private enum class PickerKind { MEMBER, FIELD, GROUP }

private fun ExposureFlag.liveValue(v: ShareViewRead): Boolean = when (this) {
    ExposureFlag.INCLUDE_MEMBERS -> v.includeMembers
    ExposureFlag.INCLUDE_BIO -> v.includeBio
    ExposureFlag.INCLUDE_FRONTING -> v.includeFronting
    ExposureFlag.FRONTING_SHOW_COUNT -> v.frontingShowCount
    ExposureFlag.INCLUDE_RELATIONSHIPS -> v.includeRelationships
    ExposureFlag.INCLUDE_GROUPS -> v.includeGroups
}

private fun ExposureFlag.pendingValue(v: ShareViewRead): Boolean? = when (this) {
    ExposureFlag.INCLUDE_MEMBERS -> v.pendingIncludeMembers
    ExposureFlag.INCLUDE_BIO -> v.pendingIncludeBio
    ExposureFlag.INCLUDE_FRONTING -> v.pendingIncludeFronting
    ExposureFlag.FRONTING_SHOW_COUNT -> v.pendingFrontingShowCount
    ExposureFlag.INCLUDE_RELATIONSHIPS -> v.pendingIncludeRelationships
    ExposureFlag.INCLUDE_GROUPS -> v.pendingIncludeGroups
}

// The reason comes from the projection itself, so it already knows about
// archived and deletion-queued members. Never invent one when it is absent.
private fun memberStatus(
    status: String,
    served: Boolean,
    reason: String?,
    addedViaGroupId: String?,
): String? {
    val bits = mutableListOf<String>()
    if (status == "pending") bits += "Waiting out the grace window"
    if (!served) {
        bits += when (reason) {
            "never_shareable" -> "Not shown: marked never shareable"
            "deletion_queued" -> "Not shown: queued for deletion"
            "archived" -> "Not shown: archived"
            "private" -> "Not shown: their privacy keeps them off public"
            "pending" -> "Not shown yet"
            else -> "Not shown"
        }
    }
    if (addedViaGroupId != null) bits += "Added by a group"
    return bits.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun groupAddSummary(added: Int, neverShareable: Int, notPublic: Int): String {
    val head = "$added member${if (added == 1) "" else "s"} added."
    val skips = mutableListOf<String>()
    if (neverShareable > 0) skips += "$neverShareable marked never shareable"
    if (notPublic > 0) skips += "$notPublic whose privacy keeps them off public"
    return if (skips.isEmpty()) head else "$head Left out: ${skips.joinToString(", ")}."
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text("Nothing left to add.")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            modifier = Modifier.clickable { onPick(id) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * A null section here is the bundle's spelling of the anonymous surface's 404:
 * that section is unaddressable, which is a different thing from a section
 * that is served and empty. The wording keeps the two apart.
 */
@Composable
private fun PreviewDialog(
    preview: systems.lupine.sheaf.data.model.SharePreview?,
    error: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("As a visitor sees it") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    error != null -> Text(error)
                    preview == null -> Text("Nothing to show.")
                    else -> {
                        Text(preview.system.name, style = MaterialTheme.typography.titleMedium)
                        preview.system.tag?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        preview.system.memberCount?.let { Text("$it members") }
                        Text(sectionLine("Roster", preview.members?.size))
                        Text(sectionLine("Fronting", preview.fronting?.members?.size))
                        Text(sectionLine("Relationships", preview.relationships?.relationships?.size))
                        Text(sectionLine("Groups", preview.groups?.groups?.size))
                        preview.members.orEmpty().forEach { m ->
                            Text(
                                "${m.name}${m.pronouns?.let { " ($it)" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun sectionLine(label: String, count: Int?): String =
    if (count == null) "$label: not published" else "$label: $count"
