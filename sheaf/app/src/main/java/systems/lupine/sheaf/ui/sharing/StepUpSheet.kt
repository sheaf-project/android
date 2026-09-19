package systems.lupine.sheaf.ui.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Re-auth before an exposing action.
 *
 * Same two fields and same tier as the System Safety screen's confirm dialog,
 * because it is the same gate: `verify_destructive_auth` against the system's
 * auth tier. Tier `none` never reaches here.
 */
@Composable
fun StepUpSheet(
    authTier: String,
    totpEnabled: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onConfirm: (password: String?, totpCode: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val needsPassword = authTier == "password" || authTier == "both"
    val needsTotp = (authTier == "totp" || authTier == "both") && totpEnabled
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Confirm before publishing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This makes something visible to people outside your system, " +
                        "so it needs your credentials first.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (needsPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !isBusy,
                    )
                }
                if (needsTotp) {
                    OutlinedTextField(
                        value = totp,
                        onValueChange = { totp = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Authenticator code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        enabled = !isBusy,
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
            Button(
                onClick = {
                    onConfirm(
                        password.takeIf { needsPassword },
                        totp.takeIf { needsTotp },
                    )
                },
                enabled = !isBusy &&
                    (!needsPassword || password.isNotBlank()) &&
                    (!needsTotp || totp.length == 6),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancel") }
        },
    )
}

/**
 * The 18+ self-declaration.
 *
 * A bare timestamp, no date of birth and no document: verifying age is itself
 * a privacy harm for the people this app is for. It gates creating a share
 * grant and nothing else, and there is no way to take it back (revoking a
 * grant is what un-publishes, and that is always available).
 */
@Composable
fun AdultAttestationDialog(
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Before you publish") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Creating a share link or public profile needs you to confirm your age " +
                        "once. Nothing is stored but the fact that you confirmed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        enabled = !isBusy,
                    )
                    Text("I am 18 or older", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = checked && !isBusy) {
                if (isBusy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancel") } },
    )
}
