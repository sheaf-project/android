package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SwitchScreen(navController: NavController) {
    val store = LocalWearStore.current
    val members by store.members.collectAsState()
    val fronts by store.currentFronts.collectAsState()
    val error by store.error.collectAsState()
    val scope = rememberCoroutineScope()

    val frontingIds = fronts.flatMap { it.memberIds }.toSet()
    var selected by remember(frontingIds) { mutableStateOf(frontingIds) }
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
                ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
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

                    items(members) { member ->
                        val isSelected = member.id in selected
                        Chip(
                            label = { Text(member.displayNameOrName) },
                            onClick = {
                                selected = if (isSelected) selected - member.id
                                           else selected + member.id
                            },
                            colors = if (isSelected) ChipDefaults.primaryChipColors()
                                     else ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    item {
                        Chip(
                            label = {
                                Text(if (selected.isEmpty()) "Clear Front" else "Switch (${selected.size})")
                            },
                            onClick = {
                                isSwitching = true
                                scope.launch {
                                    val ok = store.switchFront(selected.toList())
                                    isSwitching = false
                                    if (ok) switched = true
                                }
                            },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
