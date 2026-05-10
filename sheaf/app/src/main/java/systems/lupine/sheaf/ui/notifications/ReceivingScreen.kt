package systems.lupine.sheaf.ui.notifications

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
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import systems.lupine.sheaf.data.model.ReceivingChannelView
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivingScreen(
    onNavigateUp: () -> Unit,
    viewModel: ReceivingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var unsubscribeTarget by remember { mutableStateOf<ReceivingChannelView?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Receiving") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                        "Notification channels delivering to this account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    state.channels.forEach { channel ->
                        ReceivingRow(
                            channel = channel,
                            onUnsubscribe = { unsubscribeTarget = channel },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    unsubscribeTarget?.let { channel ->
        AlertDialog(
            onDismissRequest = { unsubscribeTarget = null },
            title = { Text("Unsubscribe?") },
            text = {
                Text(
                    buildString {
                        append("\"${channel.channelName}\"")
                        if (!channel.systemLabel.isNullOrBlank()) append(" from ${channel.systemLabel}")
                        append(" will stop delivering to this account.")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unsubscribe(channel.channelId)
                        unsubscribeTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Unsubscribe") }
            },
            dismissButton = {
                TextButton(onClick = { unsubscribeTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReceivingRow(
    channel: ReceivingChannelView,
    onUnsubscribe: () -> Unit,
) {
    val disabled = channel.destinationState.equals("disabled", ignoreCase = true)
    ListItem(
        headlineContent = { Text(channel.channelName) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!channel.systemLabel.isNullOrBlank()) {
                    Text(
                        "from ${channel.systemLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    destinationLabel(channel.destinationType) +
                        (if (disabled) " · disabled" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (disabled) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = if (disabled) MaterialTheme.colorScheme.outline
                       else MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            if (!disabled) {
                IconButton(onClick = onUnsubscribe) {
                    Icon(
                        Icons.Outlined.RemoveCircle,
                        contentDescription = "Unsubscribe",
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
                Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "No active subscriptions",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Open a magic link from a system you want to follow, and it'll show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun destinationLabel(type: String): String = when (type.lowercase()) {
    "web_push" -> "Web push"
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
    "apns_dev", "apns_prod" -> Icons.Outlined.PhoneIphone
    "web_push" -> Icons.Outlined.Public
    else -> Icons.Outlined.NotificationsActive
}
