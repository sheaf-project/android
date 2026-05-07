package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SwitchScreen(navController: NavController) {
    val store = LocalWearStore.current
    val settings = LocalWearSettings.current
    val members by store.members.collectAsState()
    val fronts by store.currentFronts.collectAsState()
    val error by store.error.collectAsState()
    val endExistingDefault by settings.endExistingFronts.collectAsState()
    val scope = rememberCoroutineScope()

    val frontingIds = fronts.flatMap { it.memberIds }.toSet()
    var selected by remember(frontingIds) { mutableStateOf(frontingIds) }
    // Per-screen-entry copy of the user's default. Flipping it here is a
    // one-shot override for this switch and doesn't change the saved default.
    var endExisting by remember(endExistingDefault) { mutableStateOf(endExistingDefault) }
    var isSwitching by remember { mutableStateOf(false) }
    var switched by remember { mutableStateOf(false) }

    LaunchedEffect(switched) {
        if (switched) {
            delay(1000)
            navController.popBackStack()
        }
    }

    Scaffold(timeText = { TimeText() }) {
        when {
            isSwitching -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            switched -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Switched!",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.secondary,
                    )
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Bottom contentPadding leaves room for the pinned confirm
                    // chip overlay; the list scrolls behind it so the confirm
                    // is reachable no matter how long the member list is.
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 72.dp),
                    ) {
                        item {
                            Text(
                                text = "Switch Front",
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
                            ToggleChip(
                                checked = endExisting,
                                onCheckedChange = { endExisting = it },
                                label = { Text("End existing fronts") },
                                toggleControl = {
                                    androidx.wear.compose.material.Switch(
                                        checked = endExisting,
                                        onCheckedChange = null,
                                    )
                                },
                                colors = ToggleChipDefaults.toggleChipColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        items(members) { member ->
                            val isSelected = member.id in selected
                            val emoji = member.emoji?.takeIf { it.isNotBlank() }
                            Chip(
                                label = {
                                    Text(if (emoji != null) "$emoji ${member.displayNameOrName}" else member.displayNameOrName)
                                },
                                icon = { MemberAvatar(member = member, size = 28.dp) },
                                onClick = {
                                    selected = if (isSelected) selected - member.id
                                               else selected + member.id
                                },
                                colors = if (isSelected) ChipDefaults.primaryChipColors()
                                         else ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Chip(
                        label = {
                            // Box wrap centres the text inside the chip's full
                            // width; default Chip alignment is start, which
                            // looks off-balance for a commit-action chip.
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (selected.isEmpty()) "Clear Front" else "Switch (${selected.size})",
                                )
                            }
                        },
                        onClick = {
                            isSwitching = true
                            scope.launch {
                                val ok = store.switchFront(selected.toList(), endExisting)
                                isSwitching = false
                                if (ok) switched = true
                            }
                        },
                        // Mint-green commit accent so the action chip reads
                        // distinctly from the purple selected-member chips.
                        colors = ChipDefaults.chipColors(
                            backgroundColor = MaterialTheme.colors.secondary,
                            contentColor = MaterialTheme.colors.onSecondary,
                        ),
                        // Narrower than the list chips and a bit further from
                        // the bottom edge so the round bezel doesn't clip the
                        // label on Pixel Watch.
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.85f)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
