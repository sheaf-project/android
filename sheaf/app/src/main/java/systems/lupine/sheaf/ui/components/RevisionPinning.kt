package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * System-safety state needed to decide whether unpinning a revision should
 * require re-auth and queue for the grace period.
 */
data class RevisionSafety(
    val authTier: String = "none",
    val totpEnabled: Boolean = false,
    val appliesToRevisions: Boolean = false,
    val gracePeriodDays: Int = 0,
) {
    /** Backend will defer the unpin and queue a pending action when this is true. */
    val willQueueUnpin: Boolean get() = appliesToRevisions && gracePeriodDays > 0
    val needsPassword: Boolean get() = willQueueUnpin && (authTier == "password" || authTier == "both")
    val needsTotp: Boolean get() = willQueueUnpin &&
        (authTier == "totp" || authTier == "both") && totpEnabled
}

@Composable
fun RevisionUnpinDialog(
    safety: RevisionSafety,
    isUnpinning: Boolean,
    errorMessage: String?,
    onConfirm: (password: String?, totpCode: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    val needsPassword = safety.needsPassword
    val needsTotp = safety.needsTotp
    val willQueue = safety.willQueueUnpin

    AlertDialog(
        onDismissRequest = { if (!isUnpinning) onDismiss() },
        icon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
        title = { Text(if (willQueue) "Queue unpin?" else "Unpin revision?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (willQueue) {
                        "Unpinning will be queued for ${safety.gracePeriodDays} " +
                            "${if (safety.gracePeriodDays == 1) "day" else "days"} before " +
                            "the revision becomes eligible for the rolling history sweep. " +
                            "You can cancel from System Safety before then."
                    } else {
                        "This revision will become eligible for the rolling history " +
                            "sweep and may be pruned by future edits."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (needsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (needsTotp) {
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                        label = { Text("Authenticator code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        password.takeIf { needsPassword },
                        totpCode.takeIf { needsTotp },
                    )
                },
                enabled = !isUnpinning &&
                    (!needsPassword || password.isNotBlank()) &&
                    (!needsTotp || totpCode.length == 6),
            ) {
                if (isUnpinning) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (willQueue) "Queue" else "Unpin")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUnpinning) { Text("Cancel") }
        },
    )
}
