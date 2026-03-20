package systems.lupine.sheaf.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.*
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMembers: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var memberToRemove by remember { mutableStateOf<MemberRead?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Welcome, ${state.system?.name ?: ""}",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openSwitchSheet() },
                icon = { Icon(Icons.Outlined.SwitchAccount, contentDescription = null) },
                text = { Text("Switch Front") },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.frontingMembers.isNotEmpty(),
            onRefresh = { viewModel.load() },
            modifier = Modifier.fillMaxSize().padding(padding),
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
                    EmptyState(
                        icon = Icons.Default.People,
                        title = "No one is fronting",
                        subtitle = "Tap 'Switch Front' to set who's fronting now.",
                        action = {
                            TextButton(onClick = onNavigateToMembers) {
                                Text("Go to Members")
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
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
        }
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

// ── Fronting member card ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrontingMemberCard(member: MemberRead, front: FrontRead?, onLongClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick)) {
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
                    .height(52.dp),
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
