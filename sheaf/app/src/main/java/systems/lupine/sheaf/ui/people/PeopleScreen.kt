package systems.lupine.sheaf.ui.people

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.People
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
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.*
import systems.lupine.sheaf.ui.groups.GroupsViewModel
import systems.lupine.sheaf.ui.members.MembersViewModel

private enum class PeopleTab { MEMBERS, GROUPS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PeopleScreen(
    onMemberClick: (String) -> Unit,
    onGroupClick: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    membersViewModel: MembersViewModel = hiltViewModel(),
    groupsViewModel: GroupsViewModel = hiltViewModel(),
) {
    val membersState by membersViewModel.state.collectAsState()
    val groupsState by groupsViewModel.state.collectAsState()

    var tab by rememberSaveable { mutableStateOf(PeopleTab.MEMBERS) }
    var memberQuery by rememberSaveable { mutableStateOf("") }
    var groupQuery by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                membersViewModel.load()
                groupsViewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredMembers = remember(memberQuery, membersState.members) {
        if (memberQuery.isBlank()) membersState.members
        else membersState.members.filter {
            it.displayNameOrName.contains(memberQuery.trim(), ignoreCase = true) ||
                it.pronouns?.contains(memberQuery.trim(), ignoreCase = true) == true
        }
    }
    val filteredGroups = remember(groupQuery, groupsState.groups) {
        if (groupQuery.isBlank()) groupsState.groups
        else groupsState.groups.filter {
            it.name.contains(groupQuery.trim(), ignoreCase = true) ||
                it.description?.contains(groupQuery.trim(), ignoreCase = true) == true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { searchOpen = !searchOpen },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(
                        if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchOpen) "Close search" else "Search",
                    )
                }
                FloatingActionButton(
                    onClick = {
                        when (tab) {
                            PeopleTab.MEMBERS -> onMemberClick("new")
                            PeopleTab.GROUPS -> onGroupClick("new")
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = when (tab) {
                            PeopleTab.MEMBERS -> "Add member"
                            PeopleTab.GROUPS -> "Add group"
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = tab == PeopleTab.MEMBERS,
                    onClick = { tab = PeopleTab.MEMBERS },
                    text = { Text("Members") },
                    icon = { Icon(Icons.Outlined.People, contentDescription = null) },
                )
                Tab(
                    selected = tab == PeopleTab.GROUPS,
                    onClick = { tab = PeopleTab.GROUPS },
                    text = { Text("Groups") },
                    icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                )
            }

            when (tab) {
                PeopleTab.MEMBERS -> MembersTabBody(
                    isLoading = membersState.isLoading,
                    error = membersState.error,
                    members = filteredMembers,
                    rawMemberCount = membersState.members.size,
                    query = memberQuery,
                    onQueryChange = { memberQuery = it },
                    showSearch = searchOpen,
                    onRetry = { membersViewModel.load() },
                    onMemberClick = onMemberClick,
                )
                PeopleTab.GROUPS -> GroupsTabBody(
                    isLoading = groupsState.isLoading,
                    error = groupsState.error,
                    groups = filteredGroups,
                    rawGroupCount = groupsState.groups.size,
                    query = groupQuery,
                    onQueryChange = { groupQuery = it },
                    showSearch = searchOpen,
                    onRetry = { groupsViewModel.load() },
                    onGroupClick = onGroupClick,
                )
            }
        }
    }
}

@Composable
private fun MembersTabBody(
    isLoading: Boolean,
    error: String?,
    members: List<MemberRead>,
    rawMemberCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onRetry: () -> Unit,
    onMemberClick: (String) -> Unit,
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null && rawMemberCount == 0 -> Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorBanner(error)
            Button(onClick = onRetry) { Text("Retry") }
        }
        rawMemberCount == 0 -> EmptyState(
            icon = Icons.Outlined.People,
            title = "No members yet",
            subtitle = "Tap + to add your first member.",
            modifier = Modifier.fillMaxSize(),
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showSearch,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                MemberSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    autoFocus = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (members.isEmpty()) {
                Text(
                    "No matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(members, key = { it.id }) { member ->
                        MemberCard(member = member, onClick = { onMemberClick(member.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsTabBody(
    isLoading: Boolean,
    error: String?,
    groups: List<GroupRead>,
    rawGroupCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onRetry: () -> Unit,
    onGroupClick: (String) -> Unit,
) {
    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null && rawGroupCount == 0 -> Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorBanner(error)
            Button(onClick = onRetry) { Text("Retry") }
        }
        rawGroupCount == 0 -> EmptyState(
            icon = Icons.Outlined.FolderOpen,
            title = "No groups yet",
            subtitle = "Tap + to create your first group.",
            modifier = Modifier.fillMaxSize(),
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showSearch,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                MemberSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = "Search groups",
                    autoFocus = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (groups.isEmpty()) {
                Text(
                    "No matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(group = group, onClick = { onGroupClick(group.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(member: MemberRead, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(member, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    member.displayNameOrName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!member.pronouns.isNullOrBlank()) {
                    Text(
                        member.pronouns!!,
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

@Composable
private fun GroupCard(group: GroupRead, onClick: () -> Unit) {
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
