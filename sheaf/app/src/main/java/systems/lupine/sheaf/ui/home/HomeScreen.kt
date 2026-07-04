package systems.lupine.sheaf.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import systems.lupine.sheaf.data.model.AnnouncementPublic
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.auth.AuthViewModel
import systems.lupine.sheaf.ui.components.*
import systems.lupine.sheaf.ui.theme.LocalWarningColors
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMembers: () -> Unit,
    onNavigateToSystemSafety: () -> Unit,
    onNavigateToRetention: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val authConfig by authViewModel.authConfig.collectAsState()
    val isPendingDeletion = state.user?.accountStatus == "pending_deletion"
    var memberToRemove by remember { mutableStateOf<MemberRead?>(null) }

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
        topBar = {
            SheafCenterAlignedTopAppBar(
                title = {
                    val systemColor = state.system?.color?.let { parseColor(it)?.toThemeAdapted() }
                    Text(
                        text = buildAnnotatedString {
                            append("Welcome")
                            state.system?.name?.let { name ->
                                append(", ")
                                withStyle(SpanStyle(color = systemColor ?: LocalContentColor.current)) {
                                    append(name)
                                }
                            }
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToMessages) {
                        Icon(Icons.Outlined.Forum, contentDescription = "Board messages")
                    }
                    // Notifications hub on the top bar: same parity with
                    // web's sidebar (notifications is a first-class entry,
                    // not buried two taps into Settings). One tap from
                    // Home reaches owned channels, your subscriptions,
                    // your devices, and reminders.
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openSwitchSheet() },
                icon = { Icon(Icons.Outlined.SwitchAccount, contentDescription = null) },
                text = { Text("Switch") },
            )
        },
        // Quick-switch chip row pinned above the bottom navigation. Sits
        // here rather than inline in the scrollable content so it's
        // always one tap away regardless of how far the user has scrolled
        // through fronting cards or announcements.
        bottomBar = {
            QuickSwitchCarousel(
                members = state.quickSwitchMembers,
                defaultReplaceFronts = state.system?.replaceFrontsDefault ?: true,
                onSwitch = { id, replace -> viewModel.quickSwitch(id, replace) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isPendingDeletion) {
                PendingDeletionBanner(
                    deletionRequestedAt = state.user?.deletionRequestedAt,
                    graceDays = authConfig?.accountDeletionGraceDays,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val pendingActionsCount = state.pendingSafetyActions.size
            val pendingChangesCount = state.pendingSafetyChanges.size
            if (pendingActionsCount > 0) {
                val earliest = state.pendingSafetyActions.mapNotNull { parseFinalize(it.finalizeAfter) }.minOrNull()
                SafetyPendingBanner(
                    kind = SafetyBannerKind.ACTIONS,
                    count = pendingActionsCount,
                    earliestFinalize = earliest,
                    onClick = onNavigateToSystemSafety,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (pendingChangesCount > 0) {
                val earliest = state.pendingSafetyChanges.mapNotNull { parseFinalize(it.finalizeAfter) }.minOrNull()
                SafetyPendingBanner(
                    kind = SafetyBannerKind.CHANGES,
                    count = pendingChangesCount,
                    earliestFinalize = earliest,
                    onClick = onNavigateToSystemSafety,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            state.pendingTrimNotice?.let { notice ->
                TrimNoticePendingBanner(
                    notice = notice,
                    onClick = onNavigateToRetention,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            OfflineSyncChip(
                isOnline = state.isOnline,
                pendingOpCount = state.pendingOpCount,
                refreshFailed = state.refreshFailed,
                onRetry = { viewModel.load() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PullToRefreshBox(
                isRefreshing = state.isLoading && state.frontingMembers.isNotEmpty(),
                onRefresh = { viewModel.load() },
                modifier = Modifier.weight(1f),
            ) {
            when {
                state.isLoading && state.frontingMembers.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.isOnline -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorBanner(state.error!!)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                }
                state.frontingMembers.isEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        state.visibleAnnouncements.forEach { announcement ->
                            AnnouncementCard(
                                announcement = announcement,
                                onDismiss = { viewModel.dismissAnnouncement(announcement.id) },
                                onDontShowAgain = { viewModel.dontShowAgainAnnouncement(announcement.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        EmptyState(
                            icon = Icons.Default.People,
                            title = "No one is fronting",
                            subtitle = "Tap a member in the bar below to start a front, " +
                                "or use Switch for more options.",
                            action = {
                                TextButton(onClick = onNavigateToMembers) {
                                    Text("Go to Members")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.visibleAnnouncements, key = { "ann_${it.id}" }) { announcement ->
                            AnnouncementCard(
                                announcement = announcement,
                                onDismiss = { viewModel.dismissAnnouncement(announcement.id) },
                                onDontShowAgain = { viewModel.dontShowAgainAnnouncement(announcement.id) },
                            )
                        }
                        items(state.frontingMembers, key = { it.id }) { member ->
                            val front = state.currentFronts.find { member.id in it.memberIds }
                            FrontingMemberCard(
                                member = member,
                                front = front,
                                onLongClick = { memberToRemove = member },
                            )
                        }
                        // Spacer clears the FAB and the pinned quick-switch
                        // bottomBar above the system nav.
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
            } // PullToRefreshBox
        } // Column
    }

    if (memberToRemove != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove from front?") },
            text = { Text("Remove ${memberToRemove!!.displayNameOrName} from front?") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFromFront(memberToRemove!!.id); memberToRemove = null }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) { Text("Cancel") }
            },
        )
    }

    if (state.showSwitchSheet) {
        SwitchFrontSheet(
            members = state.allMembers,
            selected = state.switchSelection,
            endCurrent = state.switchEndCurrent,
            customStatus = state.switchCustomStatus,
            groups = state.groups,
            memberGroups = state.memberGroups,
            activeGroupId = state.switchActiveGroupId,
            onToggle = { viewModel.toggleMemberSelection(it) },
            onSetEndCurrent = { viewModel.setSwitchEndCurrent(it) },
            onSetCustomStatus = { viewModel.setSwitchCustomStatus(it) },
            onSetActiveGroup = { viewModel.setSwitchActiveGroup(it) },
            onConfirm = { viewModel.confirmSwitch() },
            onDismiss = { viewModel.closeSwitchSheet() },
            isSwitching = state.isSwitching,
        )
    }
}

// ── Offline / sync status chip ────────────────────────────────────────────────

@Composable
private fun OfflineSyncChip(
    isOnline: Boolean,
    pendingOpCount: Int,
    refreshFailed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warningColors = LocalWarningColors.current
    when {
        !isOnline -> SuggestionChip(
            onClick = {},
            label = { Text("Offline — changes will sync when back online") },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = warningColors.container,
                labelColor = warningColors.onContainer,
            ),
            modifier = modifier,
        )
        refreshFailed -> SuggestionChip(
            onClick = onRetry,
            label = { Text("Showing cached data — tap to retry") },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = warningColors.container,
                labelColor = warningColors.onContainer,
            ),
            modifier = modifier,
        )
        pendingOpCount > 0 -> SuggestionChip(
            onClick = {},
            label = { Text("Syncing $pendingOpCount pending change${if (pendingOpCount == 1) "" else "s"}…") },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = modifier,
        )
        else -> {}
    }
}

// ── Announcement card ─────────────────────────────────────────────────────────

@Composable
private fun AnnouncementCard(
    announcement: AnnouncementPublic,
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warningColors = LocalWarningColors.current
    val containerColor = when (announcement.severity) {
        "critical" -> MaterialTheme.colorScheme.errorContainer
        "warning"  -> warningColors.container
        else       -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (announcement.severity) {
        "critical" -> MaterialTheme.colorScheme.onErrorContainer
        "warning"  -> warningColors.onContainer
        else       -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    announcement.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
                InlineMarkdownText(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    linkColor = contentColor,
                )
                if (announcement.dismissible) {
                    TextButton(
                        onClick = onDontShowAgain,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                    ) {
                        Text("Don't show again", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (announcement.dismissible) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = contentColor)
                }
            }
        }
    }
}

// ── Fronting member card ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrontingMemberCard(member: MemberRead, front: FrontRead?, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MemberAvatar(member = member, size = 56.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    member.displayNameOrName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.pronouns != null) {
                    Text(
                        member.pronouns,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val effectiveSince = front?.memberSince?.get(member.id) ?: front?.startedAt
                if (effectiveSince != null) {
                    val capped = front?.memberSinceCapped?.contains(member.id) == true
                    val elapsed = remember(effectiveSince) { timeAgo(effectiveSince) }
                    Text(
                        if (capped) "Fronting for > $elapsed" else "Fronting for $elapsed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                // Custom-status note. Italicised, secondary tone so it
                // reads as a quote next to the structural info above.
                // Skipped entirely when the front carries no status —
                // most fronts won't.
                front?.customStatus?.takeIf { it.isNotBlank() }?.let { status ->
                    Text(
                        text = "“$status”",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            ActiveDot()
        }
    }
}

// ── Switch front bottom sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchFrontSheet(
    members: List<MemberRead>,
    selected: Set<String>,
    endCurrent: Boolean,
    customStatus: String,
    groups: List<systems.lupine.sheaf.data.model.GroupRead>,
    memberGroups: Map<String, Set<String>>,
    activeGroupId: String?,
    onToggle: (String) -> Unit,
    onSetEndCurrent: (Boolean) -> Unit,
    onSetCustomStatus: (String) -> Unit,
    onSetActiveGroup: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSwitching: Boolean,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, members, activeGroupId, memberGroups) {
        members.filter { m ->
            val groupOk = activeGroupId == null || (memberGroups[m.id]?.contains(activeGroupId) == true)
            val queryOk = query.isBlank() || m.displayNameOrName.contains(query.trim(), ignoreCase = true)
            groupOk && queryOk
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Select who's fronting",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (groups.isNotEmpty()) {
                GroupFilterChips(
                    groups = groups,
                    activeGroupId = activeGroupId,
                    onSelect = onSetActiveGroup,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (members.isNotEmpty()) {
                MemberSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()
            if (members.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.People,
                    title = "No members yet",
                    subtitle = "Add members first.",
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.id }) { member ->
                        val isSelected = member.id in selected
                        ListItem(
                            headlineContent = { Text(member.displayNameOrName) },
                            supportingContent = member.pronouns?.let { { Text(it) } },
                            leadingContent = { MemberAvatar(member, size = 40.dp) },
                            trailingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggle(member.id) },
                                )
                            },
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
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetEndCurrent(!endCurrent) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = endCurrent,
                    onCheckedChange = { onSetEndCurrent(it) },
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "End current fronts",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (endCurrent) "The new front replaces what's already active."
                        else "The new front runs alongside existing ones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Optional per-front custom status. Same field as web's
            // "Custom status" input on the edit-front dialog — rides
            // along on the FrontCreate so the entry shows the same note
            // on every client. Empty trim -> sent as null (server clears).
            OutlinedTextField(
                value = customStatus,
                onValueChange = onSetCustomStatus,
                label = { Text("Custom status (optional)") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Button(
                onClick = onConfirm,
                enabled = !isSwitching && selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
            ) {
                if (isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Confirm Switch (${selected.size})")
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// ── System Safety pending banner ──────────────────────────────────────────────

private enum class SafetyBannerKind { ACTIONS, CHANGES }

@Composable
private fun SafetyPendingBanner(
    kind: SafetyBannerKind,
    count: Int,
    earliestFinalize: OffsetDateTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val critical = earliestFinalize != null &&
        Duration.between(OffsetDateTime.now(), earliestFinalize).toHours() < 24
    val containerColor = if (critical) MaterialTheme.colorScheme.errorContainer
                         else LocalWarningColors.current.container
    val onContainerColor = if (critical) MaterialTheme.colorScheme.onErrorContainer
                           else LocalWarningColors.current.onContainer

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = safetyBannerMessage(kind, count, earliestFinalize),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun safetyBannerMessage(kind: SafetyBannerKind, count: Int, earliest: OffsetDateTime?): String {
    val time = earliest?.let { formatRelativeFinalize(it) } ?: "soon"
    return when (kind) {
        SafetyBannerKind.ACTIONS ->
            if (count == 1) "1 pending destructive action — finalizes $time."
            else "$count pending destructive actions — next finalizes $time."
        SafetyBannerKind.CHANGES ->
            if (count == 1) "Safety settings change pending — finalizes $time."
            else "$count safety settings changes pending — next finalizes $time."
    }
}

private fun formatRelativeFinalize(target: OffsetDateTime): String {
    val duration = Duration.between(OffsetDateTime.now(), target)
    if (duration.isNegative || duration.isZero) return "any moment"
    val hours = duration.toHours()
    if (hours < 24) {
        val h = (duration.toMinutes() + 59) / 60
        return "in ${h}h"
    }
    val days = (duration.toMinutes() + 24 * 60 - 1) / (24 * 60)
    return if (days == 1L) "in 1 day" else "in $days days"
}

private fun parseFinalize(iso: String): OffsetDateTime? = try {
    OffsetDateTime.parse(iso)
} catch (_: DateTimeParseException) {
    null
}

// ── Retention trim-notice banner ──────────────────────────────────────────────

@Composable
private fun TrimNoticePendingBanner(
    notice: systems.lupine.sheaf.data.model.RetentionTrimNoticeRead,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveAt = parseFinalize(notice.effectiveAt)
    val critical = effectiveAt != null &&
        Duration.between(OffsetDateTime.now(), effectiveAt).toHours() < 24
    val containerColor = if (critical) MaterialTheme.colorScheme.errorContainer
                         else LocalWarningColors.current.container
    val onContainerColor = if (critical) MaterialTheme.colorScheme.onErrorContainer
                           else LocalWarningColors.current.onContainer

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(20.dp),
            )
            val time = effectiveAt?.let { formatRelativeFinalize(it) } ?: "soon"
            Text(
                "Plan downgrade trim pending: revisions over the new tier limits will be pruned $time. Tap to review.",
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Pending deletion banner ───────────────────────────────────────────────────

@Composable
private fun PendingDeletionBanner(
    deletionRequestedAt: String?,
    graceDays: Int?,
    modifier: Modifier = Modifier,
) {
    val timeRemaining = formatDeletionTimeRemaining(deletionRequestedAt, graceDays)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                buildString {
                    append("Account pending deletion.")
                    if (timeRemaining != null) append(" $timeRemaining remaining.")
                    append(" Go to Settings to cancel account deletion.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ── Quick switch carousel ────────────────────────────────────────────────────

/**
 * Horizontal row of one-tap switch chips, populated from
 * `/v1/members/top-fronters` (pinned members first, then recency-
 * weighted score). Sits beneath the fronting cards on the populated
 * home, or beneath the empty-state copy when no one is fronting yet —
 * so starting a front is one tap away in either state.
 *
 * Tap commits a switch with the system's default replace-fronts
 * behaviour. Long-press opens a menu that lets the user explicitly
 * pick "switch (end current)" vs "add to front", per Nocturnal's
 * request — same instruction repeated on iOS.
 */
@Composable
private fun QuickSwitchCarousel(
    members: List<MemberRead>,
    defaultReplaceFronts: Boolean,
    onSwitch: (memberId: String, replaceFronts: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (members.isEmpty()) return
    // The Scaffold's bottomBar slot doesn't auto-inset for the system
    // navigation bar, so the chip row was getting clipped a few pixels
    // by the gesture/3-button nav. navigationBarsPadding lifts the
    // whole carousel above the nav inset; a small extra 4dp at the
    // bottom matches the gap above the row so the float doesn't read
    // as glued to the nav.
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = "Quick switch",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(members, key = { it.id }) { member ->
                QuickSwitchChip(
                    member = member,
                    onTap = { onSwitch(member.id, defaultReplaceFronts) },
                    onSwitchReplacing = { onSwitch(member.id, true) },
                    onAddToFront = { onSwitch(member.id, false) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickSwitchChip(
    member: MemberRead,
    onTap: () -> Unit,
    onSwitchReplacing: () -> Unit,
    onAddToFront: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.combinedClickable(
                onClick = onTap,
                onLongClick = { menuOpen = true },
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            ) {
                MemberAvatar(member = member, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = member.displayNameOrName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Switch (end current)") },
                onClick = { menuOpen = false; onSwitchReplacing() },
            )
            DropdownMenuItem(
                text = { Text("Add to front") },
                onClick = { menuOpen = false; onAddToFront() },
            )
        }
    }
}

// ── Time helpers ──────────────────────────────────────────────────────────────

private fun timeAgo(isoString: String): String {
    return runCatching {
        val duration = Duration.between(Instant.parse(isoString), Instant.now())
        when {
            duration.toMinutes() < 1  -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m"
            duration.toHours()   < 24 -> "${duration.toHours()}h ${duration.toMinutes() % 60}m"
            else                      -> "${duration.toDays()}d"
        }
    }.getOrDefault("—")
}
