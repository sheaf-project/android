package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp

/**
 * Collects an audit reason (required by the backend on destructive / sensitive
 * admin actions) and, optionally, a duration in days for suspensions. Confirm
 * is disabled until a reason is entered. Shared by the admin panel and the
 * per-user detail screen.
 */
@Composable
internal fun AdminReasonDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    includeDuration: Boolean = false,
    onConfirm: (reason: String, durationDays: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Reason (recorded in the audit log)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (includeDuration) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) durationText = it },
                        label = { Text("Duration in days (blank = indefinite)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim(), durationText.toIntOrNull()) },
                enabled = reason.isNotBlank(),
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
