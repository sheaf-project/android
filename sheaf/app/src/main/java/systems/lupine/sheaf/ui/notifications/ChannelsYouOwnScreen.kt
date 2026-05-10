package systems.lupine.sheaf.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Send
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
import systems.lupine.sheaf.data.model.NotificationChannelRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsYouOwnScreen(
    onNavigateUp: () -> Unit,
    onCreateNew: () -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: ChannelsYouOwnViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var deleteTarget by remember { mutableStateOf<NotificationChannelRead?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Channels you own") },
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
                text = { Text("New invite") },
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
                state.channels.isEmpty() -> EmptyState()
                else -> {
                    Text(
                        "Notification channels people can subscribe to for updates from your system.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    state.channels.forEach { channel ->
                        ChannelRow(
                            channel = channel,
                            onClick = { onChannelClick(channel.id) },
                            onToggle = { viewModel.toggleEnabled(channel) },
                            onDelete = { deleteTarget = channel },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(96.dp)) // room for FAB
        }
    }

    deleteTarget?.let { channel ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete channel?") },
            text = {
                Text(
                    "\"${channel.name}\" will be removed and stop delivering. Any " +
                        "activation link you've shared will become invalid."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChannel(channel.id)
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
private fun ChannelRow(
    channel: NotificationChannelRead,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPending = channel.destinationState.equals("pending_registration", ignoreCase = true)
    val isDisabled = channel.destinationState.equals("disabled", ignoreCase = true)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(channel.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    destinationLabel(channel.destinationType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stateLabel(channel.destinationState),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isPending -> MaterialTheme.colorScheme.tertiary
                        isDisabled -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                channel.lastDeliveredAt?.let { at ->
                    Text(
                        "Last delivered: ${at.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                destinationIcon(channel.destinationType),
                contentDescription = null,
                tint = if (isDisabled) MaterialTheme.colorScheme.outline
                       else MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isPending) {
                    Switch(
                        checked = !isDisabled,
                        onCheckedChange = { onToggle() },
                    )
                }
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
                Icons.Outlined.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No channels yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Create a channel to invite someone to receive notifications when " +
                    "your system's fronting state changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun destinationLabel(type: String): String = when (type.lowercase()) {
    "web_push" -> "Web push (browser)"
    "fcm" -> "Android push"
    "apns_dev", "apns_prod" -> "iOS push"
    "email" -> "Email"
    "webhook" -> "Webhook"
    "ntfy" -> "ntfy"
    "pushover" -> "Pushover"
    "discord" -> "Discord"
    else -> type
}

private fun destinationIcon(type: String): ImageVector = when (type.lowercase()) {
    "fcm" -> Icons.Outlined.PhoneAndroid
    "apns_dev", "apns_prod" -> Icons.Outlined.PhoneAndroid
    "web_push" -> Icons.Outlined.Public
    else -> Icons.Outlined.Cloud
}

private fun stateLabel(state: String): String = when (state.lowercase()) {
    "pending_registration" -> "Pending — share the link to activate"
    "active" -> "Active"
    "disabled" -> "Disabled"
    else -> state
}
