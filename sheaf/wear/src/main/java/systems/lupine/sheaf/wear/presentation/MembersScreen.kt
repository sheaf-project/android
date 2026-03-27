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

@Composable
fun MembersScreen(navController: NavController) {
    val store = LocalWearStore.current
    val members by store.members.collectAsState()
    val fronts by store.currentFronts.collectAsState()
    val isLoading by store.isLoading.collectAsState()

    val error by store.error.collectAsState()
    val frontingIds = fronts.flatMap { it.memberIds }.toSet()

    Scaffold(timeText = { TimeText() }) {
        if (isLoading && members.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Members",
                    style = MaterialTheme.typography.title3,
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

            item {
                Chip(
                    label = { Text("Add Member") },
                    onClick = { navController.navigate(NAV_ADD_MEMBER) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (members.isEmpty()) {
                item { Text("No members", style = MaterialTheme.typography.body1) }
            } else {
                items(members) { member ->
                    val isFronting = member.id in frontingIds
                    Chip(
                        label = { Text(member.displayNameOrName) },
                        secondaryLabel = member.pronouns?.let { { Text(it) } },
                        icon = if (isFronting) ({
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color(0xFF1D9E75), CircleShape)
                            )
                        }) else null,
                        onClick = { navController.navigate("$NAV_MEMBER_PROFILE/${member.id}") },
                        colors = if (isFronting) ChipDefaults.primaryChipColors()
                                 else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
