package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import systems.lupine.sheaf.wear.util.stripMarkdown

@Composable
fun MemberProfileScreen(memberId: String, navController: NavController) {
    val store = LocalWearStore.current
    val members by store.members.collectAsState()
    val fronts by store.currentFronts.collectAsState()

    val member = members.firstOrNull { it.id == memberId }
    val isFronting = fronts.any { it.memberIds.contains(memberId) }
    val plainDescription = remember(member?.description) {
        member?.description?.takeIf { it.isNotBlank() }?.let(::stripMarkdown)
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (member == null) {
                item { Text("Member not found", style = MaterialTheme.typography.body1) }
                return@ScalingLazyColumn
            }

            item {
                MemberAvatar(member = member, size = 56.dp)
            }

            item {
                val emoji = member.emoji?.takeIf { it.isNotBlank() }
                Text(
                    text = if (emoji != null) "$emoji ${member.displayNameOrName}" else member.displayNameOrName,
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }

            if (member.displayName != null && member.displayName.isNotBlank()) {
                item {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (isFronting) {
                item {
                    Text(
                        text = "Currently fronting",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!member.pronouns.isNullOrBlank()) {
                item {
                    Text(
                        text = member.pronouns,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            if (!plainDescription.isNullOrBlank()) {
                item {
                    Text(
                        text = plainDescription,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            item {
                Chip(
                    label = { Text("Back") },
                    onClick = { navController.popBackStack() },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}
