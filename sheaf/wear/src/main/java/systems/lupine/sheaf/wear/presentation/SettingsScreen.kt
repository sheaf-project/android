package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

@Composable
fun SettingsScreen(navController: NavController) {
    val store = LocalWearStore.current
    val auth = LocalWearAuth.current
    val members by store.members.collectAsState()
    val fronts by store.currentFronts.collectAsState()

    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(text = "Settings", style = MaterialTheme.typography.title3)
            }
            item {
                Text(
                    text = auth.baseUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .ifBlank { "Not configured" },
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
            item {
                Text(
                    text = "${members.size} members · ${fronts.size} fronting",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                )
            }
            item {
                Chip(
                    label = { Text("Refresh") },
                    onClick = { store.loadAll() },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!confirmSignOut) {
                item {
                    Chip(
                        label = { Text("Sign Out") },
                        onClick = { confirmSignOut = true },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colors.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    Text(
                        text = "Sign out?",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.error,
                    )
                }
                item {
                    Chip(
                        label = { Text("Confirm") },
                        onClick = {
                            auth.clearCredentials()
                            store.clearData()
                            navController.popBackStack()
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colors.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Chip(
                        label = { Text("Cancel") },
                        onClick = { confirmSignOut = false },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
