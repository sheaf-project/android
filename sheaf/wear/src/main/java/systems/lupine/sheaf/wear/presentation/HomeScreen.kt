package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val oldest = store.oldestFront

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
                    Chip(
                        label = { Text(member.displayNameOrName) },
                        secondaryLabel = member.pronouns?.let { { Text(it) } },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF1D9E75), CircleShape)
                            )
                        },
                        onClick = {},
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                oldest?.startedAt?.let { startedAt ->
                    item {
                        Text(
                            text = "Fronting for ${timeAgo(startedAt)}",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.primary,
                        )
                    }
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
