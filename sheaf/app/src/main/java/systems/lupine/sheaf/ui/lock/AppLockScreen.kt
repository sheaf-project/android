package systems.lupine.sheaf.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.android.awaitFrame

private val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var promptKey by remember { mutableStateOf(0) }

    LaunchedEffect(promptKey) {
        if (activity == null) return@LaunchedEffect
        // Wait one frame so the prompt is hosted by a settled activity.
        awaitFrame()
        statusMessage = null
        showPrompt(
            activity = activity,
            onSuccess = onUnlock,
            onError = { statusMessage = it },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Box(modifier = Modifier.height(24.dp))

            Text(
                "Sheaf is locked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.height(8.dp))
            Text(
                "Authenticate with your biometrics or device passcode to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            statusMessage?.let { message ->
                Box(modifier = Modifier.height(16.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Box(modifier = Modifier.height(32.dp))

            Button(onClick = { promptKey += 1 }) {
                Text("Unlock")
            }
        }
    }
}

private fun showPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val manager = BiometricManager.from(activity)
    when (manager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onError("This device doesn't support biometric or passcode authentication.")
            return
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            onError("Set up a screen lock or biometric in your device settings to unlock Sheaf.")
            return
        }
        else -> {
            onError("Authentication is unavailable right now.")
            return
        }
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // User-cancel codes shouldn't surface a scary error — they just want to retry.
            val isUserCancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            if (!isUserCancelled) onError(errString.toString())
        }
    }

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Sheaf")
        .setSubtitle("Use your biometrics or device passcode")
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(info)
}
