package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

@Composable
fun GroupsScreen(navController: NavController) {
    val store = LocalWearStore.current
    val groups by store.groups.collectAsState()

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Groups",
                    style = MaterialTheme.typography.title3,
                )
            }

            if (groups.isEmpty()) {
                item { Text("No groups", style = MaterialTheme.typography.body1) }
            } else {
                items(groups) { group ->
                    Chip(
                        label = { Text(group.name) },
                        secondaryLabel = group.description?.takeIf { it.isNotBlank() }
                            ?.let { { Text(it) } },
                        onClick = { navController.navigate("$NAV_GROUP_DETAIL/${group.id}") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
