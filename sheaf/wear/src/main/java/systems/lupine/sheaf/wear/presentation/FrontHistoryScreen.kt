package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import systems.lupine.sheaf.wear.data.WearMember
import java.time.Duration
import java.time.Instant

/**
 * Wear front-history viewer: scrollable list of recent fronting-set
 * transitions, newest first. Reads the client-side ring buffer the
 * WearStore writes on every set change.
 *
 * Each row renders the avatars of the members fronting at that point
 * stacked next to a comma-joined name list, with a relative timestamp.
 */
@Composable
fun FrontHistoryScreen(navController: NavController) {
    val store = LocalWearStore.current
    val members by store.members.collectAsState()
    val recent by store.recentFronts.collectAsState()
    val isLoading by store.isLoading.collectAsState()
    val recentError by store.recentFrontsError.collectAsState()

    val byId = remember(members) { members.associateBy { it.id } }
    // API returns newest first already; render in that order.
    val history = remember(recent) {
        recent.mapNotNull { f ->
            val ts = f.startedAt
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            HistoryEntry(ts, f.memberIds, ongoing = f.endedAt.isNullOrBlank())
        }
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Recent fronts",
                        style = MaterialTheme.typography.title3,
                    )
                    if (isLoading && history.isNotEmpty()) {
                        // Quiet spinner alongside the title — list already
                        // has content, refresh is happening in the background.
                        Spacer(Modifier.size(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            when {
                isLoading && history.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                        }
                    }
                }
                recentError != null && history.isEmpty() -> {
                    item {
                        Text(
                            text = "Couldn't load history.",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.error,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    item {
                        Chip(
                            label = { Text("Retry") },
                            onClick = { store.loadAll() },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                history.isEmpty() -> {
                    item {
                        Text(
                            text = "No history yet. Switches you make will appear here.",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                else -> {
                    items(history) { entry ->
                        HistoryRow(
                            members = entry.memberIds.mapNotNull { byId[it] },
                            timestamp = entry.timestamp,
                            ongoing = entry.ongoing,
                        )
                    }
                    // If a refresh failed while we still have stale list
                    // data, slot a small advisory row at the bottom so the
                    // user knows what they're looking at isn't fresh.
                    if (recentError != null) {
                        item {
                            Text(
                                text = "Refresh failed — showing cached.",
                                style = MaterialTheme.typography.caption2,
                                color = MaterialTheme.colors.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        item {
                            Chip(
                                label = { Text("Retry") },
                                onClick = { store.loadAll() },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class HistoryEntry(
    val timestamp: Long,
    val memberIds: List<String>,
    val ongoing: Boolean,
)

@Composable
private fun HistoryRow(members: List<WearMember>, timestamp: Long, ongoing: Boolean) {
    // "+" suffix on the relative timestamp marks an entry that hasn't ended;
    // gives the user a quick read on which row is the live one.
    val ago = remember(timestamp, ongoing) {
        val base = timeAgoFromMillis(timestamp)
        if (ongoing) "$base+" else base
    }
    val names = members.joinToString(", ") { it.displayNameOrName }.ifEmpty { "(no fronters)" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Avatars first; cap at 3 to keep rows fitting on round-screen
        // widths. Names + relative time fill the remainder.
        members.take(3).forEach { m ->
            MemberAvatar(member = m, size = 24.dp)
        }
        if (members.size > 3) {
            Text(
                text = "+${members.size - 3}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
        Text(
            text = "$names · $ago",
            style = MaterialTheme.typography.caption1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun timeAgoFromMillis(ms: Long): String = runCatching {
    val d = Duration.between(Instant.ofEpochMilli(ms), Instant.now())
    when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
        d.toHours() < 24 -> "${d.toHours()}h ago"
        else -> "${d.toDays()}d ago"
    }
}.getOrDefault("just now")
