package systems.lupine.sheaf.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.auth.AuthViewModel
import systems.lupine.sheaf.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSystemEdit: () -> Unit,
    onNavigateToSpImport: () -> Unit,
    onNavigateToSheafImport: () -> Unit,
    onNavigateToCustomFields: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.state.collectAsState()
    val savedBaseUrl by settingsViewModel.baseUrl.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val frontNotificationEnabled by settingsViewModel.frontNotificationEnabled.collectAsState()
    val context = LocalContext.current

    var urlDraft by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showTotpSheet by remember { mutableStateOf(false) }
    var showDisableTotpDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            pendingExportJson?.let { json ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
        pendingExportJson = null
        settingsViewModel.clearExport()
    }

    LaunchedEffect(state.exportJson) {
        state.exportJson?.let { json ->
            pendingExportJson = json
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            saveFileLauncher.launch("sheaf-export-$timestamp.json")
        }
    }

    LaunchedEffect(state.accountDeletionRequested) {
        if (state.accountDeletionRequested) {
            showDeleteAccountDialog = false
            settingsViewModel.clearAccountDeletionRequested()
            authViewModel.logout()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { SheafTopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Account card ─────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.user != null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (state.system?.name ?: state.user!!.email)
                                        .firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.system?.name ?: "Your System", style = MaterialTheme.typography.titleMedium)
                            Text(state.user!!.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.user!!.tier.isNotBlank()) {
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(formatTier(state.user!!.tier), style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (state.accountDeletionRequested) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        "Account deletion requested. Your account will be permanently deleted after the grace period. Check your email for confirmation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (state.user?.emailVerified == false) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.verificationEmailSent) "Verification email sent — check your inbox."
                            else "Your email address is not verified.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        if (!state.verificationEmailSent) {
                            TextButton(
                                onClick = { settingsViewModel.resendVerificationEmail() },
                                enabled = !state.isResendingVerification,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                if (state.isResendingVerification) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Resend verification email", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // ── Appearance ───────────────────────────────────────────────────
            SectionHeader("Appearance")
            val themeModes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
            val themeIcons = mapOf("system" to Icons.Outlined.BrightnessAuto, "light" to Icons.Outlined.LightMode, "dark" to Icons.Outlined.DarkMode)
            themeModes.forEach { (mode, label) ->
                Surface(
                    onClick = { settingsViewModel.saveTheme(mode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            Icon(
                                themeIcons[mode] ?: Icons.Outlined.BrightnessAuto,
                                contentDescription = null,
                                tint = if (themeMode == mode) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = if (themeMode == mode) ({
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }) else null,
                    )
                }
                if (mode != "dark") HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }

            // ── Notifications ────────────────────────────────────────────────
            SectionHeader("Notifications")
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) settingsViewModel.toggleFrontNotification(true)
            }
            ListItem(
                headlineContent = { Text("Fronting Notification") },
                supportingContent = { Text("Persistent silent notification showing who's fronting") },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = frontNotificationEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) settingsViewModel.toggleFrontNotification(true)
                                    else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    settingsViewModel.toggleFrontNotification(true)
                                }
                            } else {
                                settingsViewModel.toggleFrontNotification(false)
                            }
                        },
                    )
                },
            )

            // ── Security ─────────────────────────────────────────────────────
            SectionHeader("Security")
            val totpEnabled = state.user?.totpEnabled == true
            ListItem(
                headlineContent = { Text("Two-Factor Authentication") },
                supportingContent = { Text(if (totpEnabled) "Enabled" else "Disabled") },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (totpEnabled) MaterialTheme.colorScheme.tertiary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            if (totpEnabled) {
                SettingItem(
                    icon = Icons.Outlined.LockOpen,
                    title = "Disable 2FA",
                    subtitle = null,
                    onClick = { showDisableTotpDialog = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                SettingItem(
                    icon = Icons.Outlined.AddCircle,
                    title = "Set Up 2FA",
                    subtitle = null,
                    onClick = { settingsViewModel.startTotpSetup(); showTotpSheet = true },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Shield,
                title = "Deletion Protection",
                subtitle = formatDeleteConfirmation(state.system?.deleteConfirmation),
                onClick = { showDeleteConfirmationDialog = true },
            )

            // ── System ───────────────────────────────────────────────────────
            SectionHeader("System")
            SettingItem(
                icon = Icons.Outlined.Edit,
                title = "Edit System",
                subtitle = state.system?.name ?: "—",
                onClick = onNavigateToSystemEdit,
            )

            // ── Data ─────────────────────────────────────────────────────────
            SectionHeader("Data")
            SettingItem(
                icon = Icons.Outlined.List,
                title = "Custom Fields",
                subtitle = null,
                onClick = onNavigateToCustomFields,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Download,
                title = "Export All Data",
                subtitle = "Download a full JSON backup",
                onClick = { settingsViewModel.exportData() },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Upload,
                title = "Import from Simply Plural",
                subtitle = "Import members, groups, and history",
                onClick = onNavigateToSpImport,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Upload,
                title = "Import from Sheaf Export",
                subtitle = "Restore from a Sheaf JSON backup",
                onClick = onNavigateToSheafImport,
            )

            // ── Server ───────────────────────────────────────────────────────
            SectionHeader("Server")
            SettingItem(
                icon = Icons.Outlined.Storage,
                title = "API Server",
                subtitle = savedBaseUrl.ifBlank { "Not configured" },
                onClick = { urlDraft = savedBaseUrl; showUrlDialog = true },
            )

            // ── Account ──────────────────────────────────────────────────────
            SectionHeader("Account")
            SettingItem(
                icon = Icons.Outlined.Key,
                title = "API Keys",
                subtitle = "Manage API keys for scripts and integrations",
                onClick = onNavigateToApiKeys,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Devices,
                title = "Active Sessions",
                subtitle = "View and revoke signed-in devices",
                onClick = onNavigateToSessions,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            if (state.user?.isAdmin == true) {
                SettingItem(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = "Admin Panel",
                    subtitle = null,
                    onClick = onNavigateToAdminPanel,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }
            SettingItem(
                icon = Icons.Outlined.Logout,
                title = "Sign Out",
                subtitle = null,
                onClick = { showLogoutDialog = true },
                tint = MaterialTheme.colorScheme.error,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete Account",
                subtitle = "Permanently delete your account and all data",
                onClick = { showDeleteAccountDialog = true },
                tint = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── TOTP Setup Sheet ──────────────────────────────────────────────────────

    if (showTotpSheet) {
        TotpSetupSheet(
            state = state,
            onAdvanceToVerify = { settingsViewModel.advanceTotpToVerify() },
            onVerify = { code -> settingsViewModel.verifyTotp(code) },
            onAdvanceToDone = { settingsViewModel.advanceTotpToDone() },
            onDismiss = {
                showTotpSheet = false
                settingsViewModel.resetTotpSetup()
            },
        )
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("API Server") },
            text = {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.sheaf.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { settingsViewModel.saveBaseUrl(urlDraft.trim()); showUrlDialog = false }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") } },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to use Sheaf.") },
            confirmButton = {
                TextButton(
                    onClick = { authViewModel.logout(); showLogoutDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Sign Out") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } },
        )
    }

    if (showDisableTotpDialog) {
        var disablePassword by remember { mutableStateOf("") }
        var disableTotpCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDisableTotpDialog = false },
            title = { Text("Disable 2FA") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter your password and a current authenticator code to confirm.")
                    OutlinedTextField(
                        value = disablePassword,
                        onValueChange = { disablePassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = disableTotpCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) disableTotpCode = it },
                        label = { Text("Authenticator code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.totpError != null) {
                        Text(state.totpError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.disableTotp(disablePassword, disableTotpCode)
                    },
                    enabled = disablePassword.isNotBlank() && disableTotpCode.length == 6 && !state.totpIsDisabling,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (state.totpIsDisabling) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Disable")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showDisableTotpDialog = false; settingsViewModel.clearTotpError() }) { Text("Cancel") } },
        )
        LaunchedEffect(state.user?.totpEnabled) {
            if (state.user?.totpEnabled == false) showDisableTotpDialog = false
        }
    }

    // ── Delete Account Dialog ─────────────────────────────────────────────────

    if (showDeleteAccountDialog) {
        val totpEnabled = state.user?.totpEnabled == true
        var deletePassword by remember { mutableStateOf("") }
        var deleteTotpCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                if (!state.isDeletingAccount) {
                    showDeleteAccountDialog = false
                    settingsViewModel.clearDeletionError()
                }
            },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This will permanently delete your account and all associated data. This action cannot be undone.")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (totpEnabled) {
                        OutlinedTextField(
                            value = deleteTotpCode,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) deleteTotpCode = it },
                            label = { Text("Authenticator code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.deletionError != null) {
                        Text(state.deletionError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.requestAccountDeletion(
                            deletePassword,
                            deleteTotpCode.takeIf { totpEnabled },
                        )
                    },
                    enabled = deletePassword.isNotBlank() &&
                        (!totpEnabled || deleteTotpCode.length == 6) &&
                        !state.isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (state.isDeletingAccount) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Account")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false; settingsViewModel.clearDeletionError() },
                    enabled = !state.isDeletingAccount,
                ) { Text("Cancel") }
            },
        )
    }

    // ── Delete Confirmation Level Dialog ──────────────────────────────────────

    if (showDeleteConfirmationDialog) {
        val totpEnabled = state.user?.totpEnabled == true
        val currentLevel = state.system?.deleteConfirmation ?: "password"
        var selectedLevel by remember(currentLevel) { mutableStateOf(currentLevel) }
        var confirmPassword by remember { mutableStateOf("") }
        var confirmTotpCode by remember { mutableStateOf("") }
        val levels = listOf(
            "none" to "None",
            "password" to "Password",
            "totp" to "Authenticator Code",
            "both" to "Password + Authenticator Code",
        )
        AlertDialog(
            onDismissRequest = {
                if (!state.isUpdatingDeleteConfirmation) {
                    showDeleteConfirmationDialog = false
                    settingsViewModel.clearDeleteConfirmationError()
                }
            },
            title = { Text("Deletion Protection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose how much confirmation is required before your account can be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    levels.forEach { (value, label) ->
                        val requiresTotp = value == "totp" || value == "both"
                        if (requiresTotp && !totpEnabled) return@forEach
                        Surface(
                            onClick = { selectedLevel = value },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedLevel == value,
                                    onClick = { selectedLevel = value },
                                )
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Confirm with your credentials:", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (totpEnabled) {
                        OutlinedTextField(
                            value = confirmTotpCode,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmTotpCode = it },
                            label = { Text("Authenticator code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.deleteConfirmationError != null) {
                        Text(state.deleteConfirmationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.updateDeleteConfirmation(
                            selectedLevel,
                            confirmPassword,
                            confirmTotpCode.takeIf { totpEnabled },
                        )
                    },
                    enabled = confirmPassword.isNotBlank() &&
                        (!totpEnabled || confirmTotpCode.length == 6) &&
                        !state.isUpdatingDeleteConfirmation,
                ) {
                    if (state.isUpdatingDeleteConfirmation) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmationDialog = false; settingsViewModel.clearDeleteConfirmationError() },
                    enabled = !state.isUpdatingDeleteConfirmation,
                ) { Text("Cancel") }
            },
        )
        LaunchedEffect(state.isUpdatingDeleteConfirmation) {
            if (!state.isUpdatingDeleteConfirmation && state.deleteConfirmationError == null && confirmPassword.isNotBlank()) {
                showDeleteConfirmationDialog = false
            }
        }
    }
}

// ── TOTP Setup Sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotpSetupSheet(
    state: SettingsUiState,
    onAdvanceToVerify: () -> Unit,
    onVerify: (String) -> Unit,
    onAdvanceToDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var code by remember { mutableStateOf("") }
    var copiedSecret by remember { mutableStateOf(false) }
    var copiedCodes by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.totpStep) {
                TotpStep.LOADING -> {
                    Text("Set Up 2FA", style = MaterialTheme.typography.titleLarge)
                    CircularProgressIndicator()
                    Text("Generating your secret…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TotpStep.SECRET -> {
                    Text("Scan with Authenticator", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Add this to your authenticator app (Aegis, 1Password, Google Authenticator). Tap the secret to copy it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.totpSetupResponse != null) {
                        Surface(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(state.totpSetupResponse.secret))
                                copiedSecret = true
                            },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    state.totpSetupResponse.secret,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    if (copiedSecret) Icons.Default.Check else Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = if (copiedSecret) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (state.totpError != null) {
                        ErrorBanner(state.totpError)
                    }
                    Button(
                        onClick = onAdvanceToVerify,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("I've added it — Next") }
                }

                TotpStep.VERIFY -> {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Confirm Code", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Enter the 6-digit code from your authenticator app to confirm setup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                        label = { Text("6-digit code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = state.totpError != null,
                        supportingText = state.totpError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onVerify(code) },
                        enabled = code.length == 6 && !state.totpIsVerifying,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.totpIsVerifying) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Verify & Enable")
                        }
                    }
                }

                TotpStep.RECOVERY_CODES -> {
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text("Save Your Recovery Codes", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "These one-time codes are your backup if you lose your authenticator. Store them somewhere safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.totpSetupResponse?.recoveryCodes?.let { codes ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                codes.forEachIndexed { i, recoveryCode ->
                                    Text(
                                        "${i + 1}. $recoveryCode",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(codes.joinToString("\n")))
                                copiedCodes = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                if (copiedCodes) Icons.Default.Check else Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (copiedCodes) "Copied!" else "Copy All Codes")
                        }
                    }
                    Button(
                        onClick = onAdvanceToDone,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("I've saved them — Done") }
                }

                TotpStep.DONE -> {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Text("2FA Enabled!", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your account is now protected. You'll be asked for a code each time you sign in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Close") }
                }
            }
        }
    }
}

// ── Setting item ──────────────────────────────────────────────────────────────

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
}

private fun formatTier(tier: String): String = when (tier) {
    "saas"        -> "SaaS"
    "self_hosted" -> "Self-hosted"
    else          -> tier.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

private fun formatDeleteConfirmation(level: String?): String = when (level) {
    "none"     -> "None"
    "password" -> "Password required"
    "totp"     -> "Authenticator code required"
    "both"     -> "Password + authenticator code"
    else       -> "—"
}

// ── System edit screen ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemEditScreen(
    onNavigateUp: () -> Unit,
    viewModel: SystemEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form  by viewModel.form.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateUp()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Edit System") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.error != null) ErrorBanner(state.error!!)

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.updateForm { copy(name = it) } },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.tag,
                onValueChange = { viewModel.updateForm { copy(tag = it) } },
                label = { Text("Tag") },
                placeholder = { Text("e.g. mysystem") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.avatarUrl,
                onValueChange = { viewModel.updateForm { copy(avatarUrl = it) } },
                label = { Text("Avatar URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ColorPicker(
                hex = form.color,
                onColorChange = { viewModel.updateForm { copy(color = it) } },
            )

            SectionHeader("Privacy")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("public", "friends", "private").forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = form.privacy == level,
                        onClick = { viewModel.updateForm { copy(privacy = level) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) { Text(level.replaceFirstChar { it.uppercase() }) }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Save Changes")
            }
        }
    }
}

