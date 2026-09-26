package systems.lupine.sheaf.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.AdminUserUpdate
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

/**
 * Accounts on this instance: search, moderate, hand off to the detail screen.
 *
 * Its own screen rather than a section of the admin panel because the list is
 * unbounded. On an instance with any real number of accounts it pushed invite
 * codes, announcements and maintenance so far down the panel that they read as
 * absent, and the search field scrolled away from the results it filtered.
 * Here the field stays put and the list is the only thing under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    onNavigateUp: () -> Unit,
    onNavigateToUserDetail: (String) -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Users") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            state.message?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(msg, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            OutlinedTextField(
                value = state.search,
                onValueChange = { viewModel.setSearch(it) },
                label = { Text("Search users") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                // Only a first load blanks the screen. A search reuses the rows
                // already on screen until the new ones arrive, so typing doesn't
                // flash the list in and out on every pause.
                state.isLoading && state.users.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.users.isEmpty() -> {
                    Text(
                        if (state.search.isBlank()) "No accounts" else "No accounts match \"${state.search}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.users, key = { it.id }) { user ->
                            UserListItem(
                                user = user,
                                onUpdate = { update -> viewModel.updateUser(user.id, update) },
                                onResetPassword = { reason, newPw -> viewModel.resetPassword(user.id, reason, newPw) },
                                onChangeEmail = { reason, newEmail -> viewModel.changeEmail(user.id, reason, newEmail) },
                                onDisableTotp = { reason -> viewModel.disableTotp(user.id, reason) },
                                onVerifyEmail = { reason -> viewModel.verifyEmail(user.id, reason) },
                                onCancelDeletion = { reason -> viewModel.cancelDeletion(user.id, reason) },
                                onSuspend = { reason, days -> viewModel.suspendUser(user.id, reason, days) },
                                onUnsuspend = { reason -> viewModel.unsuspendUser(user.id, reason) },
                                onBan = { reason -> viewModel.banUser(user.id, reason) },
                                onUnban = { reason -> viewModel.unbanUser(user.id, reason) },
                                onViewDetail = { onNavigateToUserDetail(user.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: systems.lupine.sheaf.data.model.AdminUserRead,
    onUpdate: (AdminUserUpdate) -> Unit,
    onResetPassword: (String, String?) -> Unit,
    onChangeEmail: (String, String) -> Unit,
    onDisableTotp: (String) -> Unit,
    onVerifyEmail: (String) -> Unit,
    onCancelDeletion: (String) -> Unit,
    onSuspend: (String, Int?) -> Unit,
    onUnsuspend: (String) -> Unit,
    onBan: (String) -> Unit,
    onUnban: (String) -> Unit,
    onViewDetail: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val suspended = user.accountStatus.equals("suspended", ignoreCase = true)
    val banned = user.accountStatus.equals("banned", ignoreCase = true)

    Surface(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(user.email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.tier, style = MaterialTheme.typography.bodySmall)
                    Text("·", style = MaterialTheme.typography.bodySmall)
                    Text(
                        user.accountStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (suspended || banned) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (user.isAdmin) {
                        Text("·", style = MaterialTheme.typography.bodySmall)
                        Text("admin", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onViewDetail) {
                    Icon(Icons.Outlined.Info, contentDescription = "Account detail")
                }
            },
        )
    }

    if (showDialog) {
        UserEditDialog(
            user = user,
            onDismiss = { showDialog = false },
            onSave = { update -> onUpdate(update); showDialog = false },
            onResetPassword = { reason, newPw -> onResetPassword(reason, newPw); showDialog = false },
            onChangeEmail = { reason, newEmail -> onChangeEmail(reason, newEmail); showDialog = false },
            onDisableTotp = { reason -> onDisableTotp(reason); showDialog = false },
            onVerifyEmail = { reason -> onVerifyEmail(reason); showDialog = false },
            onCancelDeletion = { reason -> onCancelDeletion(reason); showDialog = false },
            onSuspend = { reason, days -> onSuspend(reason, days); showDialog = false },
            onUnsuspend = { reason -> onUnsuspend(reason); showDialog = false },
            onBan = { reason -> onBan(reason); showDialog = false },
            onUnban = { reason -> onUnban(reason); showDialog = false },
        )
    }
}

@Composable
private fun UserEditDialog(
    user: systems.lupine.sheaf.data.model.AdminUserRead,
    onDismiss: () -> Unit,
    onSave: (AdminUserUpdate) -> Unit,
    onResetPassword: (String, String?) -> Unit,
    onChangeEmail: (String, String) -> Unit,
    onDisableTotp: (String) -> Unit,
    onVerifyEmail: (String) -> Unit,
    onCancelDeletion: (String) -> Unit,
    onSuspend: (String, Int?) -> Unit,
    onUnsuspend: (String) -> Unit,
    onBan: (String) -> Unit,
    onUnban: (String) -> Unit,
) {
    var tier by remember { mutableStateOf(user.tier) }
    var isAdmin by remember { mutableStateOf(user.isAdmin) }
    var memberLimitText by remember { mutableStateOf(user.memberLimit?.toString() ?: "") }

    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var showChangeEmailDialog by remember { mutableStateOf(false) }
    var confirmDisableTotp by remember { mutableStateOf(false) }
    var confirmVerifyEmail by remember { mutableStateOf(false) }
    var confirmCancelDeletion by remember { mutableStateOf(false) }
    var showSuspend by remember { mutableStateOf(false) }
    var showUnsuspend by remember { mutableStateOf(false) }
    var showBan by remember { mutableStateOf(false) }
    var showUnban by remember { mutableStateOf(false) }

    val suspended = user.accountStatus.equals("suspended", ignoreCase = true)
    val banned = user.accountStatus.equals("banned", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.email, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text("Tier", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("free", "plus", "self_hosted").forEachIndexed { index, t ->
                        SegmentedButton(
                            selected = tier == t,
                            onClick = { tier = t },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) { Text(t.replace('_', ' '), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                    Text("Admin", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = memberLimitText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) memberLimitText = it },
                    label = { Text("Member limit override") },
                    placeholder = { Text("Leave empty for default") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()
                Text(
                    "Recovery Tools",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showResetPasswordDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset Password") }
                OutlinedButton(
                    onClick = { showChangeEmailDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Change Email") }
                if (!user.emailVerified) {
                    OutlinedButton(
                        onClick = { confirmVerifyEmail = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Mark Email Verified") }
                }
                if (user.totpEnabled) {
                    OutlinedButton(
                        onClick = { confirmDisableTotp = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Disable TOTP") }
                }
                if (user.accountStatus.contains("delet", ignoreCase = true)) {
                    OutlinedButton(
                        onClick = { confirmCancelDeletion = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel Account Deletion") }
                }

                // Moderation is hidden for admin accounts: the backend rejects
                // suspend/ban against admins, so don't offer a button that 403s.
                if (!user.isAdmin) {
                    HorizontalDivider()
                    Text(
                        "Moderation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (suspended) {
                        user.suspendedReason?.let {
                            Text(
                                "Suspended: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedButton(
                            onClick = { showUnsuspend = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Lift Suspension") }
                    } else if (!banned) {
                        OutlinedButton(
                            onClick = { showSuspend = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Suspend") }
                    }
                    if (banned) {
                        OutlinedButton(
                            onClick = { showUnban = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Lift Ban") }
                    } else {
                        OutlinedButton(
                            onClick = { showBan = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Ban Permanently") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(AdminUserUpdate(
                    tier = tier.takeIf { it != user.tier },
                    isAdmin = isAdmin.takeIf { it != user.isAdmin },
                    memberLimit = memberLimitText.toIntOrNull(),
                    clearMemberLimit = if (memberLimitText.isBlank() && user.memberLimit != null) true else null,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showResetPasswordDialog) {
        ResetPasswordDialog(
            onConfirm = { reason, newPw -> onResetPassword(reason, newPw); showResetPasswordDialog = false },
            onDismiss = { showResetPasswordDialog = false },
        )
    }

    if (showChangeEmailDialog) {
        ChangeEmailDialog(
            onConfirm = { reason, newEmail -> onChangeEmail(reason, newEmail); showChangeEmailDialog = false },
            onDismiss = { showChangeEmailDialog = false },
        )
    }

    if (confirmDisableTotp) {
        AdminReasonDialog(
            title = "Disable TOTP?",
            message = "Removes two-factor authentication from the account. The user must re-enroll to restore it.",
            confirmLabel = "Disable",
            destructive = true,
            onConfirm = { reason, _ -> onDisableTotp(reason); confirmDisableTotp = false },
            onDismiss = { confirmDisableTotp = false },
        )
    }

    if (confirmVerifyEmail) {
        AdminReasonDialog(
            title = "Verify email?",
            message = "Mark ${user.email} as verified without the user clicking a verification link.",
            confirmLabel = "Verify",
            onConfirm = { reason, _ -> onVerifyEmail(reason); confirmVerifyEmail = false },
            onDismiss = { confirmVerifyEmail = false },
        )
    }

    if (confirmCancelDeletion) {
        AdminReasonDialog(
            title = "Cancel deletion?",
            message = "Restore ${user.email} and cancel the scheduled account deletion.",
            confirmLabel = "Cancel deletion",
            onConfirm = { reason, _ -> onCancelDeletion(reason); confirmCancelDeletion = false },
            onDismiss = { confirmCancelDeletion = false },
        )
    }

    if (showSuspend) {
        AdminReasonDialog(
            title = "Suspend account?",
            message = "Soft-bans ${user.email} and revokes their sessions. Leave duration blank for an indefinite suspension.",
            confirmLabel = "Suspend",
            destructive = true,
            includeDuration = true,
            onConfirm = { reason, days -> onSuspend(reason, days); showSuspend = false },
            onDismiss = { showSuspend = false },
        )
    }

    if (showUnsuspend) {
        AdminReasonDialog(
            title = "Lift suspension?",
            message = "Restores ${user.email} to active.",
            confirmLabel = "Lift",
            onConfirm = { reason, _ -> onUnsuspend(reason); showUnsuspend = false },
            onDismiss = { showUnsuspend = false },
        )
    }

    if (showBan) {
        AdminReasonDialog(
            title = "Ban permanently?",
            message = "Permanently bans ${user.email} and revokes their sessions. This does not auto-expire.",
            confirmLabel = "Ban",
            destructive = true,
            onConfirm = { reason, _ -> onBan(reason); showBan = false },
            onDismiss = { showBan = false },
        )
    }

    if (showUnban) {
        AdminReasonDialog(
            title = "Lift ban?",
            message = "Restores ${user.email} to active.",
            confirmLabel = "Lift",
            onConfirm = { reason, _ -> onUnban(reason); showUnban = false },
            onDismiss = { showUnban = false },
        )
    }
}


@Composable
private fun ResetPasswordDialog(
    onConfirm: (reason: String, newPassword: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Leave blank to generate a random password (the user will need to use \"Forgot Password\" to regain access).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Reason (recorded in the audit log)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim(), newPassword.ifBlank { null }) },
                enabled = reason.isNotBlank(),
            ) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangeEmailDialog(
    onConfirm: (reason: String, newEmail: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Reason (recorded in the audit log)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("New email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim(), newEmail) },
                enabled = newEmail.contains('@') && reason.isNotBlank(),
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
