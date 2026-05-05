package systems.lupine.sheaf.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.data.model.FileRead
import systems.lupine.sheaf.ui.settings.OrphanFilesDeleteSafety

@Composable
fun FileDeleteDialog(
    file: FileRead,
    safety: OrphanFilesDeleteSafety,
    isDeleting: Boolean,
    errorMessage: String?,
    onConfirm: (password: String?, totpCode: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val needsPassword = safety.authTier == "password" || safety.authTier == "both"
    val needsTotp = (safety.authTier == "totp" || safety.authTier == "both") && safety.totpEnabled
    // Per-file delete is image-safeguarded only when the file is an image.
    val isImage = file.contentType.startsWith("image/")
    val willQueue = isImage && safety.appliesToImages && safety.gracePeriodDays > 0

    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    val displayName = file.key.substringAfterLast('/').ifBlank { file.key }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(if (willQueue) "Queue file deletion?" else "Delete file?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (willQueue) {
                        "$displayName (${formatBytes(file.sizeBytes)}) will be queued " +
                            "for deletion in ${safety.gracePeriodDays} " +
                            "${if (safety.gracePeriodDays == 1) "day" else "days"}. " +
                            "You can cancel from System Safety before then."
                    } else {
                        "Permanently delete $displayName (${formatBytes(file.sizeBytes)})? " +
                            "Anything that referenced it (member avatars, embedded images) " +
                            "will break."
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
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
