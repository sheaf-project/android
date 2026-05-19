package systems.lupine.sheaf.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.push.PushNotificationChannels

/**
 * Reached via the magic-link deep link or after a logged-in user
 * arrives with a pending activation code. Calls
 * POST /v1/notifications/redeem on launch and shows the channel
 * summary on success. Triggers the POST_NOTIFICATIONS prompt at
 * this moment because this is when the user is explicitly opting
 * in to receiving notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemNotificationScreen(
    activationCode: String,
    instanceUrl: String? = null,
    onDone: () -> Unit,
) {
    val viewModel: RedeemNotificationViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(activationCode, instanceUrl) {
        viewModel.redeem(activationCode, instanceUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscribe to notifications") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is RedeemUiState.Loading -> CircularProgressIndicator()
                is RedeemUiState.Success -> SuccessContent(
                    channelName = s.channelName,
                    systemLabel = s.systemLabel,
                    onDone = onDone,
                )
                is RedeemUiState.Error -> ErrorContent(
                    message = s.message,
                    onRetry = { viewModel.redeem(activationCode) },
                    onCancel = onDone,
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    channelName: String,
    systemLabel: String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED

    val channelDisabled = !needsPermission && !areTargetChannelEnabled(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result observed via the disabled flag on next recomposition */ }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Subscribed to \"$channelName\"",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (!systemLabel.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "from $systemLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
        when {
            needsPermission -> {
                Text(
                    "Allow notifications so this device can receive them.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Enable notifications") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip for now") }
            }
            channelDisabled -> {
                Text(
                    "The Front change channel is disabled. Open system settings to re-enable it.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Done") }
            }
            else -> {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Outlined.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.height(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Try again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cancel") }
    }
}

private fun areTargetChannelEnabled(context: android.content.Context): Boolean {
    val mgr = context.getSystemService(android.app.NotificationManager::class.java)
        ?: return true
    val channel = mgr.getNotificationChannel(PushNotificationChannels.CHANNEL_FRONT_CHANGE)
        ?: return true
    return channel.importance != android.app.NotificationManager.IMPORTANCE_NONE
}
