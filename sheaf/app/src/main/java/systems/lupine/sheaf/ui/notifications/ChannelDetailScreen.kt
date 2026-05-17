package systems.lupine.sheaf.ui.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.NotificationChannelRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    channelId: String,
    onNavigateUp: () -> Unit,
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var deleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(channelId) { viewModel.load(channelId) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Channel") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(vertical = 8.dp))
            }
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.channel != null -> Content(
                    channel = state.channel!!,
                    reissuedUrl = state.reissuedActivationUrl,
                    isReissuing = state.isReissuing,
                    onReissue = { viewModel.reissueActivation(channelId) },
                    onToggleEnabled = { viewModel.toggleEnabled() },
                    onDelete = { deleteConfirm = true },
                    onDismissReissuedUrl = { viewModel.dismissReissuedUrl() },
                    context = context,
                )
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete channel?") },
            text = {
                Text(
                    "\"${state.channel?.name ?: "This channel"}\" will be removed " +
                        "and stop delivering. Any link you've shared will become " +
                        "invalid."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(channelId, onDeleted = onNavigateUp)
                        deleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Content(
    channel: NotificationChannelRead,
    reissuedUrl: String?,
    isReissuing: Boolean,
    onReissue: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    onDismissReissuedUrl: () -> Unit,
    context: Context,
) {
    val isPending = channel.destinationState.equals("pending_registration", ignoreCase = true)
    val isActive = channel.destinationState.equals("active", ignoreCase = true)
    val isDisabled = channel.destinationState.equals("disabled", ignoreCase = true)

    Spacer(Modifier.height(8.dp))
    Text(channel.name, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        destinationLabel(channel.destinationType),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    StateBadge(channel.destinationState)
    Spacer(Modifier.height(24.dp))

    SectionHeader("Triggers")
    TriggerLine("Member starts fronting", channel.triggerOnStart)
    TriggerLine("Member stops fronting", channel.triggerOnStop)
    TriggerLine("Co-fronter set changes", channel.triggerOnCofrontChange)

    Spacer(Modifier.height(24.dp))

    if (isPending) {
        PendingActivationPanel(
            reissuedUrl = reissuedUrl,
            isReissuing = isReissuing,
            onReissue = onReissue,
            onDismissReissuedUrl = onDismissReissuedUrl,
            context = context,
        )
    }

    if (isActive || isDisabled) {
        SectionHeader("Delivery")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (isDisabled) "Currently paused" else "Receiving events",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = !isDisabled,
                onCheckedChange = { onToggleEnabled() },
            )
        }
        channel.lastDeliveredAt?.let { at ->
            Text(
                "Last delivered: ${at.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    OutlinedButton(
        onClick = onDelete,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Delete channel") }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun PendingActivationPanel(
    reissuedUrl: String?,
    isReissuing: Boolean,
    onReissue: () -> Unit,
    onDismissReissuedUrl: () -> Unit,
    context: Context,
) {
    SectionHeader("Activation link")
    Text(
        "The recipient opens this link on their device to activate. The link " +
            "is one-time and time-limited. You can re-issue a fresh one any time.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (reissuedUrl != null) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(
                reissuedUrl,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Sheaf invite", reissuedUrl))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, reissuedUrl)
                        putExtra(Intent.EXTRA_SUBJECT, "Subscribe to my front updates")
                    }
                    context.startActivity(Intent.createChooser(send, "Share invite link"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onDismissReissuedUrl,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Hide link") }
    } else {
        Button(
            onClick = onReissue,
            enabled = !isReissuing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isReissuing) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Get a fresh activation link")
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun StateBadge(state: String) {
    val (text, color) = when (state.lowercase()) {
        "active" -> "Active" to MaterialTheme.colorScheme.primary
        "pending_registration" -> "Pending activation" to MaterialTheme.colorScheme.tertiary
        "disabled" -> "Disabled" to MaterialTheme.colorScheme.error
        else -> state to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun TriggerLine(label: String, on: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (on) Icons.Outlined.Check else Icons.Outlined.Refresh,
            contentDescription = null,
            tint = if (on) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (on) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
        )
    }
}

private fun destinationLabel(type: String): String = when (type.lowercase()) {
    "web_push" -> "Web push (browser)"
    "mobile_push" -> "Mobile push"
    // Legacy values: backend migration rewrites them to mobile_push at
    // the row level, but keep these mappings in case a stale local cache
    // or read-back of an un-migrated snapshot still surfaces them.
    "fcm" -> "Mobile push"
    "apns_dev", "apns_prod" -> "Mobile push"
    "email" -> "Email"
    "webhook" -> "Webhook"
    "ntfy" -> "ntfy"
    "pushover" -> "Pushover"
    "discord" -> "Discord"
    else -> type
}
