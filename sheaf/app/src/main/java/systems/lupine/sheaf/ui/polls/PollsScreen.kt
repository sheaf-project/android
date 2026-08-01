package systems.lupine.sheaf.ui.polls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.PollRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import androidx.compose.ui.draw.alpha
import systems.lupine.sheaf.ui.components.PENDING_DELETE_ALPHA
import systems.lupine.sheaf.ui.components.PendingDeleteBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollsScreen(
    onPollClick: (String) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: PollsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // No navigation icon: Polls is now a top-level bottom-nav
            // destination, so there's nothing to navigate "up" to.
            SheafTopAppBar(title = { Text("Polls") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNew,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New poll") },
                // Label the FAB directly; the text slot's semantics can be dropped
                // by Material3's expand/collapse AnimatedVisibility. See HomeScreen.
                modifier = Modifier
                    .testTag("polls_new_fab")
                    .semantics { contentDescription = "New poll" },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.open.isEmpty() && state.closed.isEmpty() -> EmptyState()
                else -> {
                    if (state.open.isNotEmpty()) {
                        SectionHeader("Open")
                        state.open.forEach { poll ->
                            PollRow(poll = poll, onClick = { onPollClick(poll.id) })
                            HorizontalDivider()
                        }
                    }
                    if (state.closed.isNotEmpty()) {
                        SectionHeader("Closed")
                        state.closed.forEach { poll ->
                            PollRow(poll = poll, onClick = { onPollClick(poll.id) })
                            HorizontalDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun PollRow(poll: PollRead, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .alpha(if (poll.pendingDeleteAt != null) PENDING_DELETE_ALPHA else 1f),
        headlineContent = { Text(poll.question) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${poll.options.size} options · ${poll.totalVotes} votes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (poll.isClosed) "Closed ${poll.closedSince?.take(10) ?: ""}"
                    else "Closes ${poll.closesAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (poll.isClosed) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
                )
                PendingDeleteBadge(poll.pendingDeleteAt)
            }
        },
        leadingContent = {
            Icon(
                Icons.Outlined.HowToVote,
                contentDescription = null,
                tint = if (poll.isClosed) MaterialTheme.colorScheme.outline
                       else MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.HowToVote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("No polls yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Polls let members of your system vote on something. Useful for " +
                    "decisions you want to make together.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
