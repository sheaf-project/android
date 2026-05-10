package systems.lupine.sheaf.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Android 13 (API 33) introduced runtime permission for POST_NOTIFICATIONS.
 * Below 33 it's auto-granted; above we have to ask. The first system prompt
 * appearance, if denied, becomes a hard "no" that requires a settings trip
 * to undo, so callers should ask only when there's a clear reason (e.g.
 * the user has just subscribed to a notification channel).
 */
fun areNotificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Compose helper that returns a launcher for the POST_NOTIFICATIONS prompt.
 * The result callback receives true when granted (and on pre-33 devices
 * where the launcher fires synchronously with auto-granted=true).
 *
 * Usage:
 * ```
 * val launcher = rememberNotificationPermissionLauncher { granted ->
 *     if (granted) registerForPush()
 * }
 * Button(onClick = { launcher.launch(Unit) }) { Text("Enable notifications") }
 * ```
 */
@Composable
fun rememberNotificationPermissionLauncher(
    onResult: (granted: Boolean) -> Unit,
): NotificationPermissionLauncher {
    val systemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult,
    )
    return remember(systemLauncher) {
        NotificationPermissionLauncher {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                systemLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult(true)
            }
        }
    }
}

fun interface NotificationPermissionLauncher {
    fun launch()
}
