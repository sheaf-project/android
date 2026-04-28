package systems.lupine.sheaf.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Auth tier values from the backend's DeleteConfirmation enum. */
data class MemberDeleteSafety(
    val authTier: String = "none",
    val totpEnabled: Boolean = false,
    val appliesToMembers: Boolean = false,
    val gracePeriodDays: Int = 0,
)

@Composable
fun MemberDeleteDialog(
    memberLabel: String,
    safety: MemberDeleteSafety,
    isDeleting: Boolean,
    errorMessage: String?,
    onConfirm: (password: String?, totpCode: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val needsPassword = safety.authTier == "password" || safety.authTier == "both"
    val needsTotp = (safety.authTier == "totp" || safety.authTier == "both") && safety.totpEnabled
    val willQueue = safety.appliesToMembers && safety.gracePeriodDays > 0

    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(if (willQueue) "Queue deletion?" else "Delete member?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (willQueue) {
                        "$memberLabel will be queued for deletion in ${safety.gracePeriodDays} " +
                            "${if (safety.gracePeriodDays == 1) "day" else "days"}. " +
                            "You can cancel from System Safety before then."
                    } else {
                        "This will permanently delete $memberLabel. This cannot be undone."
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
                enabled = !isDeleting &&
                    (!needsPassword || password.isNotBlank()) &&
                    (!needsTotp || totpCode.length == 6),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (willQueue) "Queue" else "Delete")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        },
    )
}
