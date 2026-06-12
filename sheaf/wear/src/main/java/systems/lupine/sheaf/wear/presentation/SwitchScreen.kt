package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SwitchScreen(navController: NavController) {
    val store = LocalWearStore.current
    val settings = LocalWearSettings.current
    val members by store.members.collectAsState()
    val topFronters by store.topFronters.collectAsState()
    val fronts by store.currentFronts.collectAsState()
    // Order the picker by the quick-switch ranking (pins + recency-weighted
    // score) so the members reached for most sit at the top, then the rest of
    // the roster in its existing order. Empty ranking falls back to plain order.
    val orderedMembers = remember(members, topFronters) {
        if (topFronters.isEmpty()) {
            members
        } else {
            val present = members.associateBy { it.id }
            val ranked = topFronters.mapNotNull { present[it.id] }
            val rankedIds = ranked.mapTo(HashSet()) { it.id }
            ranked + members.filterNot { it.id in rankedIds }
        }
    }
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

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        // Scrollbar only renders in the list branch; the Scaffold still
        // hosts it unconditionally because Scaffold expects a stable
        // composition. The PositionIndicator no-ops when the underlying
        // list state hasn't been laid out yet.
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
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
                // Round wears clip anything that pokes past the curved bezel.
                // The confirm chip sits at the screen bottom, so on round we
                // lift it up into the wider middle band and shrink its width
                // so the corners stay inside the circle. Play rejected
                // earlier builds for the rectangular layout clipping on a
                // Pixel Watch in review.
                val isRound = LocalConfiguration.current.isScreenRound
                val confirmBottomInset = if (isRound) 18.dp else 4.dp
                val confirmWidthFraction = if (isRound) 0.62f else 0.85f
                // Lift the list's bottom padding to match so the last list
                // item never lands behind the floating confirm chip.
                val listBottomInset = confirmBottomInset + 60.dp
                Box(modifier = Modifier.fillMaxSize()) {
                    // Bottom contentPadding leaves room for the pinned confirm
                    // chip overlay; the list scrolls behind it so the confirm
                    // is reachable no matter how long the member list is.
                    ScalingLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = listBottomInset),
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

                        items(orderedMembers) { member ->
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
                        // Round wears: corners get clipped by the curved
                        // bezel if the chip sits at the very bottom edge or
                        // is too wide. We lift it up into the wider middle
                        // band and shrink the width so the rounded corners
                        // stay inside the circle. On rectangular wears we
                        // keep the original bottom-pinned look since there's
                        // no curve to worry about. Height stays shorter than
                        // the default Chip (~52dp) to free list real-estate.
                        // Modifier order matters: the outer paddings reserve
                        // safe-area below + horizontal slack first, then
                        // the chip itself takes 0.62 width × 40dp inside
                        // that reduced viewport. Reversed, the chip would
                        // be 40dp but with 18dp of empty space *inside* its
                        // bottom edge — the actual confirm hit-box would
                        // shrink and the rendered chip would still clip.
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = confirmBottomInset)
                            .padding(horizontal = 8.dp)
                            .fillMaxWidth(confirmWidthFraction)
                            .height(40.dp),
                    )
                }
            }
        }
    }
}
