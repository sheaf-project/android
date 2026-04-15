package systems.lupine.sheaf.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMembers: () -> Unit,
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
                title = { Text(state.system?.name?.let { "Welcome, $it" } ?: "Welcome") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openSwitchSheet() },
                icon = { Icon(Icons.Outlined.SwitchAccount, contentDescription = null) },
                text = { Text("Switch") },
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
                state.error != null -> {
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
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        EmptyState(
                            icon = Icons.Default.People,
                            title = "No one is fronting",
                            subtitle = "Tap 'Switch Front' to set who's fronting now.",
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
            onToggle = { viewModel.toggleMemberSelection(it) },
            onConfirm = { viewModel.confirmSwitch() },
            onDismiss = { viewModel.closeSwitchSheet() },
            isSwitching = state.isSwitching,
        )
    }
}

// ── Announcement card ─────────────────────────────────────────────────────────

@Composable
private fun AnnouncementCard(
    announcement: AnnouncementPublic,
    onDismiss: () -> Unit,
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
                Text(
                    announcement.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
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
                if (front?.startedAt != null) {
                    val elapsed = remember(front.startedAt) { timeAgo(front.startedAt) }
                    Text(
                        "Fronting for $elapsed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
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
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isSwitching: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Select who's fronting",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            if (members.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.People,
                    title = "No members yet",
                    subtitle = "Add members first.",
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(members, key = { it.id }) { member ->
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
                }
            }
            HorizontalDivider()
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
