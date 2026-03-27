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
import kotlinx.coroutines.launch

@Composable
fun GroupDetailScreen(groupId: String, navController: NavController) {
    val store = LocalWearStore.current
    val allMembers by store.members.collectAsState()
    val groups by store.groups.collectAsState()

    val group = groups.firstOrNull { it.id == groupId }
    val scope = rememberCoroutineScope()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load current group members from API
    LaunchedEffect(groupId) {
        isLoading = true
        runCatching { store.apiClient.getGroupMembers(groupId) }
            .onSuccess { members -> selectedIds = members.map { it.id }.toSet() }
            .onFailure { e -> error = e.message ?: "Failed to load group" }
        isLoading = false
    }

    Scaffold(timeText = { TimeText() }) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = group?.name ?: "Group",
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (error != null) {
                item {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(allMembers) { member ->
                val isSelected = member.id in selectedIds
                Chip(
                    label = { Text(member.displayNameOrName) },
                    secondaryLabel = member.pronouns?.let { { Text(it) } },
                    onClick = {
                        selectedIds = if (isSelected) selectedIds - member.id
                                      else selectedIds + member.id
                    },
                    colors = if (isSelected) ChipDefaults.primaryChipColors()
                             else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Chip(
                    label = {
                        if (isSaving) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            Text("Save (${selectedIds.size})")
                        }
                    },
                    onClick = {
                        scope.launch {
                            isSaving = true
                            error = null
                            runCatching {
                                store.apiClient.setGroupMembers(groupId, selectedIds.toList())
                            }.onSuccess {
                                navController.popBackStack()
                            }.onFailure { e ->
                                error = e.message ?: "Failed to save"
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
