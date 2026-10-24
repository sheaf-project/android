package systems.lupine.sheaf.ui.passkeys

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.PasskeyRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeysScreen(
    onNavigateUp: () -> Unit,
    viewModel: PasskeysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PasskeyRead?>(null) }
    var deleteTarget by remember { mutableStateOf<PasskeyRead?>(null) }

    LaunchedEffect(state.addCompleted) { showAdd = false }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Passkeys") },
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
            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !state.available -> Text(
                    "This server doesn't offer passkeys.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> {
                    Text(
                        "Sign in with your fingerprint, face, screen lock, or a hardware key " +
                            "instead of typing your password. Your password keeps working, and " +
                            "deleting a member or publishing your profile will still ask for it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    state.passkeys.forEach { passkey ->
                        PasskeyListItem(
                            passkey = passkey,
                            onRename = { renameTarget = passkey },
                            onDelete = { deleteTarget = passkey },
                        )
                        HorizontalDivider()
                    }
                    if (state.passkeys.isEmpty()) {
                        Text(
                            "No passkeys yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.clearAddError(); showAdd = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add a passkey")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) {
        AddPasskeyDialog(
            totpEnabled = state.totpEnabled,
            isAdding = state.isAdding,
            error = state.addError,
            onAdd = { activity, password, code, nickname ->
                viewModel.addPasskey(activity, password, code, nickname)
            },
            onDismiss = { showAdd = false },
        )
    }

    renameTarget?.let { passkey ->
        var nickname by remember(passkey.id) { mutableStateOf(passkey.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename passkey") },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(128) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(passkey.id, nickname)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { passkey ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove passkey?") },
            text = {
                Text(
                    "${passkey.nickname ?: "This passkey"} will no longer sign in to your account. " +
                        "Your password is not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.delete(passkey.id); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddPasskeyDialog(
    totpEnabled: Boolean,
    isAdding: Boolean,
    error: String?,
    onAdd: (activity: android.content.Context, password: String, code: String?, nickname: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    val canSubmit = password.isNotBlank() && (!totpEnabled || code.isNotBlank()) && !isAdding

    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = { Text("Add a passkey") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (totpEnabled) "Confirm it's you with your password and two-factor code."
                    else "Confirm it's you with your password.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (error != null) ErrorBanner(error)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (totpEnabled) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.trim() },
                        label = { Text("Two-factor or recovery code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(128) },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g. My phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(context, password, code.takeIf { totpEnabled }, nickname) },
                enabled = canSubmit,
            ) {
                if (isAdding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAdding) { Text("Cancel") }
        },
    )
}

@Composable
private fun PasskeyListItem(
    passkey: PasskeyRead,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(passkey.nickname ?: "Passkey", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val small = MaterialTheme.typography.bodySmall
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    if (passkey.backupEligible) "Synced passkey" else "Single-device key",
                    style = small,
                    color = muted,
                )
                Text(
                    "Added ${passkey.createdAt.take(10)} · " +
                        (passkey.lastUsedAt?.let { "last used ${it.take(10)}" } ?: "never used"),
                    style = small,
                    color = muted,
                )
                if (!passkey.usable) {
                    Text(
                        "Created for ${passkey.rpId}, a previous address of this server, so it " +
                            "no longer works. Remove it and add a new one.",
                        style = small,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                if (passkey.usable) Icons.Outlined.Key else Icons.Outlined.KeyOff,
                contentDescription = null,
                tint = if (passkey.usable) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
