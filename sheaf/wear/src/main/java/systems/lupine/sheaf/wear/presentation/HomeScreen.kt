package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import java.time.Duration
import java.time.Instant

@Composable
fun HomeScreen(navController: NavController) {
    val store = LocalWearStore.current
    val fronts by store.currentFronts.collectAsState()
    val isLoading by store.isLoading.collectAsState()

    val error by store.error.collectAsState()
    val frontingMembers = store.frontingMembers

    // Per-member effective fronting-since (chain-aware via member_since when
    // the system has coalesce_contiguous_fronts enabled). Falls back to each
    // front's started_at when absent, and uses the earliest across overlapping
    // open fronts. Capped members render with a "> " prefix.
    val (memberSinceMap, cappedSet) = remember(fronts) {
        val out = mutableMapOf<String, String>()
        val capped = mutableSetOf<String>()
        fronts.forEach { f ->
            f.memberIds.forEach { mid ->
                val since = f.memberSince[mid] ?: f.startedAt
                if (since != null) {
                    val existing = out[mid]
                    if (existing == null || since < existing) out[mid] = since
                }
                if (mid in f.memberSinceCapped) capped.add(mid)
            }
        }
        out.toMap() to capped.toSet()
    }

    Scaffold(timeText = { TimeText() }) {
        if (isLoading && frontingMembers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Currently Fronting",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }

            if (error != null) {
                item {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.error,
                    )
                }
            }

            if (frontingMembers.isEmpty()) {
                item {
                    Text(
                        text = "No one fronting",
                        style = MaterialTheme.typography.body1,
                    )
                }
            } else {
                items(frontingMembers) { member ->
                    val since = memberSinceMap[member.id]
                    val isCapped = member.id in cappedSet
                    val emoji = member.emoji?.takeIf { it.isNotBlank() }
                    val secondary = buildString {
                        val parts = mutableListOf<String>()
                        member.pronouns?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                        since?.let {
                            val ago = timeAgo(it)
                            parts.add(if (isCapped) "> $ago" else ago)
                        }
                        append(parts.joinToString(" · "))
                    }
                    Chip(
                        label = {
                            Text(if (emoji != null) "$emoji ${member.displayNameOrName}" else member.displayNameOrName)
                        },
                        secondaryLabel = secondary.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                        icon = { MemberAvatar(member = member, size = 28.dp) },
                        onClick = {},
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Chip(
                    label = { Text("Switch Front") },
                    onClick = { navController.navigate(NAV_SWITCH) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun timeAgo(isoString: String): String = runCatching {
    val duration = Duration.between(Instant.parse(isoString), Instant.now())
    when {
        duration.toMinutes() < 1  -> "just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m"
        duration.toHours()   < 24 -> "${duration.toHours()}h ${duration.toMinutes() % 60}m"
        else                      -> "${duration.toDays()}d"
    }
}.getOrDefault("—")
