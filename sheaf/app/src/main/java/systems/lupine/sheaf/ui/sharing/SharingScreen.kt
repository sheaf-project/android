package systems.lupine.sheaf.ui.sharing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import systems.lupine.sheaf.data.model.ShareAuditEntry
import systems.lupine.sheaf.data.model.ShareGrantRead
import systems.lupine.sheaf.data.model.ShareViewRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.datePickerDate
import systems.lupine.sheaf.ui.components.datePickerMillis
import systems.lupine.sheaf.ui.theme.LocalWarningColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScreen(
    onBack: () -> Unit,
    onOpenView: (String) -> Unit,
    viewModel: SharingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var granting by remember { mutableStateOf<ShareViewRead?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Sharing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                androidx.compose.material3.FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New share view")
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }

            state.loadError != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = {
                    ErrorBanner(state.loadError!!)
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                },
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                state.audit.profileSuppressed?.let {
                    WarningCard(
                        text = suppressionMessage(it),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (state.pendingExposures.isNotEmpty()) {
                    WarningCard(
                        text = if (state.pendingExposures.size == 1) {
                            "1 change that makes something public is waiting out the grace window."
                        } else {
                            "${state.pendingExposures.size} changes that make something public " +
                                "are waiting out the grace window."
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // The switch being off does not hide what is already here: an
                // owner must always be able to reach revoke.
                if (!state.publicProfilesEnabled) {
                    WarningCard(
                        text = "Public profiles are turned off on this instance. Nothing new can " +
                            "be published, but anything already shared stays listed here so you " +
                            "can still revoke it.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                state.actionError?.let {
                    ErrorBanner(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }

                SectionHeader("Share views")
                if (state.views.isEmpty()) {
                    Text(
                        "A view is a curated slice of your system. Make one, put members in it, " +
                            "then share it with a link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    state.views.forEachIndexed { i, v ->
                        if (i > 0) RowDivider()
                        ShareViewRow(
                            view = v,
                            canPublish = state.publicProfilesEnabled,
                            onOpen = { onOpenView(v.id) },
                            onShare = { granting = v },
                        )
                    }
                }

                SectionHeader("Who can see what")
                if (state.audit.entries.isEmpty()) {
                    Text(
                        "Nothing is shared right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    state.audit.entries.forEachIndexed { i, entry ->
                        if (i > 0) RowDivider()
                        AuditRow(
                            entry = entry,
                            busy = state.busy,
                            onRotate = { viewModel.rotateGrant(entry.grant.id) },
                            onRevoke = { viewModel.revokeGrant(entry.grant.id) },
                        )
                    }
                }

                // A grant the audit does not describe: pending, expired or
                // revoked. Listed so revoke stays reachable for all of them.
                val auditedIds = state.audit.entries.map { it.grant.id }.toSet()
                val dormant = state.grants.filter { it.id !in auditedIds && it.revokedAt == null }
                if (dormant.isNotEmpty()) {
                    SectionHeader("Not currently serving")
                    dormant.forEachIndexed { i, grant ->
                        if (i > 0) RowDivider()
                        DormantGrantRow(
                            grant = grant,
                            viewName = state.views.find { it.id == grant.viewId }?.name ?: "Unknown view",
                            busy = state.busy,
                            onRevoke = { viewModel.revokeGrant(grant.id) },
                        )
                    }
                }

                androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
            }
        }
    }

    if (creating) {
        CreateViewDialog(
            busy = state.busy,
            onCreate = { name -> viewModel.createView(name); creating = false },
            onDismiss = { creating = false },
        )
    }

    granting?.let { v ->
        CreateGrantDialog(
            viewName = v.name,
            busy = state.busy,
            graceDays = state.graceDays,
            onCreate = { subjectType, note, expiresAt ->
                viewModel.createGrant(v.id, subjectType, note, expiresAt)
                granting = null
            },
            onDismiss = { granting = null },
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

    state.needsAttestation?.let {
        AdultAttestationDialog(
            isBusy = state.busy,
            onConfirm = { viewModel.attestAdult() },
            onDismiss = { viewModel.dismissAttestation() },
        )
    }

    state.newToken?.let { token ->
        TokenDialog(token = token, onDismiss = { viewModel.clearToken() })
    }
}

@Composable
private fun ShareViewRow(
    view: ShareViewRead,
    canPublish: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    ListItem(
        leadingContent = { RowIcon(Icons.Outlined.FilterNone) },
        headlineContent = { Text(view.name) },
        supportingContent = {
            val bits = buildList {
                add(if (view.isShared) "Live" else "Not shared")
                add("${view.members.size} members")
                if (view.hasPendingFlags) add("changes staged")
            }
            Text(bits.joinToString(" · "))
        },
        trailingContent = {
            TextButton(onClick = onShare, enabled = canPublish) { Text("Share") }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

// Same shape as a share view row, so the two lists read as one screen: icon,
// the view's name, a summary underneath, and actions tucked behind a menu.
@Composable
private fun AuditRow(
    entry: ShareAuditEntry,
    busy: Boolean,
    onRotate: () -> Unit,
    onRevoke: () -> Unit,
) {
    val isLink = entry.grant.subjectType == "link"
    ListItem(
        leadingContent = { RowIcon(grantIcon(isLink)) },
        headlineContent = { Text(entry.viewName) },
        supportingContent = {
            Text(
                listOfNotNull(
                    "${grantLabel(isLink)} · ${expiryLine(entry.grant.expiresAt)}",
                    auditCounts(entry),
                    entry.grant.note,
                ).joinToString("\n"),
            )
        },
        trailingContent = {
            GrantMenu(busy = busy, onRotate = onRotate.takeIf { isLink }, onRevoke = onRevoke)
        },
    )
}

/**
 * The curated count and the served count are different numbers and both belong
 * on screen: curated describes the owner's work, served describes what a
 * visitor actually gets. A null served count means the roster is off, which is
 * not the same as zero.
 */
private fun auditCounts(entry: ShareAuditEntry): String {
    val parts = mutableListOf<String>()
    parts += if (!entry.includeMembers) {
        "${entry.memberCount} members curated, roster not shown"
    } else {
        val served = entry.servedMemberCount
        if (served != null && served != entry.memberCount) {
            "$served of ${entry.memberCount} members shown"
        } else {
            "${entry.memberCount} members"
        }
    }
    if (entry.fieldCount > 0) parts += "${entry.fieldCount} fields"
    if (entry.includeRelationships) parts += "${entry.relationshipCount} relationships"
    if (entry.includeGroups) parts += "${entry.groupCount} groups"
    if (entry.includeBio) parts += "bios"
    if (entry.includeFronting) parts += "fronting"
    return parts.joinToString(", ")
}

@Composable
private fun DormantGrantRow(
    grant: ShareGrantRead,
    viewName: String,
    busy: Boolean,
    onRevoke: () -> Unit,
) {
    val isLink = grant.subjectType == "link"
    ListItem(
        leadingContent = { RowIcon(grantIcon(isLink)) },
        headlineContent = { Text(viewName) },
        supportingContent = {
            Text("${grantLabel(isLink)} · ${dormantReason(grant.status, grant.expiresAt)}")
        },
        trailingContent = { GrantMenu(busy = busy, onRotate = null, onRevoke = onRevoke) },
    )
}

@Composable
private fun GrantMenu(busy: Boolean, onRotate: (() -> Unit)?, onRevoke: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = !busy) {
        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        onRotate?.let {
            DropdownMenuItem(text = { Text("Rotate link") }, onClick = { open = false; it() })
        }
        DropdownMenuItem(text = { Text("Revoke") }, onClick = { open = false; onRevoke() })
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

private fun grantIcon(isLink: Boolean): ImageVector =
    if (isLink) Icons.Outlined.Link else Icons.Outlined.Public

private fun grantLabel(isLink: Boolean): String = if (isLink) "Secret link" else "Public profile"

private fun expiryLine(expiresAt: String?): String {
    val date = parseExpiry(expiresAt) ?: return "No expiry set"
    return if (date.isBefore(LocalDate.now())) {
        "Expired ${date.format(EXPIRY_FORMAT)}"
    } else {
        "Expires ${date.format(EXPIRY_FORMAT)}"
    }
}

/**
 * Why a grant the audit does not describe is not serving. An expiry in the
 * future is not a reason on its own, so it is only named once it has passed.
 */
internal fun dormantReason(
    status: String,
    expiresAt: String?,
    today: LocalDate = LocalDate.now(),
): String {
    val expiry = parseExpiry(expiresAt)
    return when {
        status == "pending" -> "Waiting out the grace window"
        expiry != null && expiry.isBefore(today) -> "Expired ${expiry.format(EXPIRY_FORMAT)}"
        else -> status.replaceFirstChar { it.uppercase() }
    }
}

// Server timestamps are UTC instants; the date a user cares about is the one
// on their own calendar.
internal fun parseExpiry(iso: String?): LocalDate? = iso?.let {
    runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
}

@Composable
private fun WarningCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalWarningColors.current.container),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = LocalWarningColors.current.onContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalWarningColors.current.onContainer,
            )
        }
    }
}

// Deliberately coarse server-side: the anonymous surface returns one uniform
// 404 for all of these, so naming them precisely would buy nothing.
private fun suppressionMessage(reason: String): String = when (reason) {
    "publishing_blocked" ->
        "An operator has switched publishing off for this account. Nothing is being served."
    "system_private" ->
        "Your system privacy is not set to public, so nothing is being served. " +
            "Change it in System settings."
    "account_state" ->
        "This account is not in good standing, so nothing is being served."
    else -> "Nothing is being served right now."
}

@Composable
private fun CreateViewDialog(
    busy: Boolean,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New share view") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A view starts empty and shares nothing until you add members and make a link.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !busy,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank() && !busy) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateGrantDialog(
    viewName: String,
    busy: Boolean,
    graceDays: Int,
    onCreate: (subjectType: String, note: String?, expiresAt: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var subjectType by remember { mutableStateOf("link") }
    var expiry by remember { mutableStateOf<LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    // A grant that is staged does not start serving until the grace window
    // passes, so an expiry inside that window would produce a link that lives
    // and dies without ever having been readable.
    val expiresBeforeItStarts = expiryIsBeforeActivation(expiry, graceDays)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share \"$viewName\"") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = subjectType == "link",
                        onClick = { subjectType = "link" },
                        label = { Text("Secret link") },
                    )
                    FilterChip(
                        selected = subjectType == "public",
                        onClick = { subjectType = "public" },
                        label = { Text("Public profile") },
                    )
                }
                Text(
                    if (subjectType == "link") {
                        "Anyone with the link can see this view. The link is shown once and " +
                            "cannot be retrieved again."
                    } else {
                        "Anyone who knows your system id can see this view."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    enabled = !busy,
                )

                Text("Expires", style = MaterialTheme.typography.labelLarge)
                // Expiry is fixed at creation: the API has no way to move it
                // afterwards, so changing your mind later means rotating the
                // token or revoking and starting again.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpiryPreset("Never", expiry == null) { expiry = null }
                    listOf(7L, 30L, 90L).forEach { days ->
                        val date = LocalDate.now().plusDays(days)
                        ExpiryPreset("$days days", expiry == date) { expiry = date }
                    }
                    ExpiryPreset("Pick a date", false) { pickingDate = true }
                }
                Text(
                    expiry?.let { "Stops working at the end of ${it.format(EXPIRY_FORMAT)}." }
                        ?: "This link keeps working until you revoke it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expiresBeforeItStarts) {
                    Text(
                        "That is on or before the day this goes live, so it would expire " +
                            "without ever having been readable. Pick a later date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "The expiry cannot be changed later. To shorten or extend it you would " +
                        "rotate the link or revoke it and make a new one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (graceDays > 0) {
                    Text(
                        "This will not go live for $graceDays day${if (graceDays == 1) "" else "s"}. " +
                            "You can revoke it at any point before then, immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(subjectType, note.trim(), expiry?.let(::endOfDayUtc)) },
                enabled = !busy && !expiresBeforeItStarts,
            ) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        // Today is excluded along with the past: an expiry of "end of today"
        // is a link with hours to live, which is never what the picker meant.
        val earliest = LocalDate.now().plusDays(1)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = datePickerMillis(expiry ?: earliest),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !datePickerDate(utcTimeMillis).isBefore(earliest)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { expiry = datePickerDate(it) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ExpiryPreset(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

internal val EXPIRY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * True when a staged grant would expire on or before the day it goes live.
 *
 * With a grace window the grant sits PENDING and serves nobody until it
 * activates, so an expiry inside that window produces a link that was never
 * readable at any point in its life.
 */
internal fun expiryIsBeforeActivation(
    expiry: LocalDate?,
    graceDays: Int,
    today: LocalDate = LocalDate.now(),
): Boolean {
    if (expiry == null) return false
    return !expiry.isAfter(today.plusDays(graceDays.toLong()))
}

/**
 * The last instant of the chosen day in the device's own timezone, as UTC.
 *
 * Picking a date means "still works on that day", so the boundary is its end
 * rather than its start, and it is the user's midnight that matters, not UTC's.
 */
internal fun endOfDayUtc(date: LocalDate): String =
    date.atTime(23, 59, 59)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toString()

@Composable
private fun TokenDialog(token: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your share link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Copy this now. It is not stored anywhere it can be read back, so this is " +
                        "the only time it can be shown.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        token,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { clipboard.setText(AnnotatedString(token)); onDismiss() }) {
                Text("Copy and close")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
