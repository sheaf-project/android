package systems.lupine.sheaf.ui.notifications.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.ReminderRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onNavigateUp: () -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<ReminderRead?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNew,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New reminder") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.reminders.isEmpty() -> EmptyState()
                else -> {
                    Text(
                        "Push or webhook events your system fires on a schedule or in " +
                            "response to fronting changes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    state.reminders.forEach { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            channelName = state.channelNames[reminder.channelId],
                            memberName = reminder.triggerMemberId?.let { state.memberNames[it] },
                            onClick = { onEdit(reminder.id) },
                            onToggle = { viewModel.toggleEnabled(reminder) },
                            onDelete = { deleteTarget = reminder },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    deleteTarget?.let { reminder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete reminder?") },
            text = { Text("\"${reminder.name}\" will stop firing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(reminder.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderRead,
    channelName: String?,
    memberName: String?,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(reminder.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    triggerSummary(reminder, memberName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                channelName?.let { c ->
                    Text(
                        "via $c",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!reminder.enabled) {
                    Text(
                        "Paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                triggerIcon(reminder.triggerType),
                contentDescription = null,
                tint = if (reminder.enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { onToggle() },
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("No reminders yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Reminders fire on a schedule or when a member starts/stops " +
                    "fronting. They deliver through one of your notification " +
                    "channels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun triggerIcon(triggerType: String): ImageVector = when (triggerType) {
    "automated" -> Icons.Outlined.Timer
    "repeated" -> Icons.Outlined.Schedule
    else -> Icons.Outlined.Alarm
}

private fun triggerSummary(r: ReminderRead, memberName: String?): String = when (r.triggerType) {
    "automated" -> {
        val who = memberName ?: "Someone"
        val evt = when (r.triggerEvent) {
            "start" -> "starts fronting"
            "stop" -> "stops fronting"
            "any" -> "fronts or stops"
            else -> "changes front"
        }
        val delay = r.delaySeconds?.let { formatDelay(it) }
        if (delay != null) "$who $evt, +$delay" else "$who $evt"
    }
    "repeated" -> {
        val time = r.scheduleTime ?: "?"
        when (r.scheduleKind) {
            "daily" -> "Daily at $time"
            "weekly" -> "Weekly at $time" + (r.scheduleDowMask?.let { " (${dowSummary(it)})" } ?: "")
            "monthly" -> "Monthly on day ${r.scheduleDom ?: "?"} at $time"
            else -> "Scheduled at $time"
        }
    }
    else -> r.triggerType
}

private fun formatDelay(seconds: Int): String = when {
    seconds == 0 -> "immediately"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    seconds < 86400 -> "${seconds / 3600}h"
    else -> "${seconds / 86400}d"
}

private fun dowSummary(mask: Int): String {
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    val on = (0 until 7).mapNotNull { if (mask and (1 shl it) != 0) names[it] else null }
    return if (on.size == 7) "every day"
    else if (on.isEmpty()) "no days"
    else on.joinToString("")
}
