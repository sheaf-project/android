package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

@Composable
fun MemberProfileScreen(memberId: String, navController: NavController) {
    val store = LocalWearStore.current
    val members by store.members.collectAsState()
    val fronts by store.currentFronts.collectAsState()

    val member = members.firstOrNull { it.id == memberId }
    val isFronting = fronts.any { it.memberIds.contains(memberId) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            if (member == null) {
                item { Text("Member not found", style = MaterialTheme.typography.body1) }
                return@ScalingLazyColumn
            }

            item {
                Text(
                    text = member.displayNameOrName,
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.fillMaxWidth(),
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

            if (!member.description.isNullOrBlank()) {
                item {
                    Text(
                        text = member.description,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }

            if (!member.color.isNullOrBlank()) {
                val colorInt = runCatching {
                    android.graphics.Color.parseColor(
                        if (member.color.startsWith("#")) member.color else "#${member.color}"
                    )
                }.getOrNull()
                if (colorInt != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(24.dp)
                                .background(Color(colorInt), CircleShape)
                        )
                    }
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
