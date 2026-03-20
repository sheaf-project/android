package systems.lupine.sheaf.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
    onNavigateToCustomFields: () -> Unit,
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

    LaunchedEffect(state.exportJson) {
        state.exportJson?.let { json ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "Sheaf data export")
            }
            context.startActivity(Intent.createChooser(intent, "Share export"))
            settingsViewModel.clearExport()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
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
                            shape = MaterialTheme.shapes.extraLarge,
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
            SettingItem(
                icon = if (totpEnabled) Icons.Outlined.ManageAccounts else Icons.Outlined.AddCircle,
                title = if (totpEnabled) "Manage 2FA" else "Set Up 2FA",
                subtitle = null,
                onClick = { settingsViewModel.startTotpSetup(); showTotpSheet = true },
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
                icon = Icons.Outlined.Logout,
                title = "Sign Out",
                subtitle = null,
                onClick = { showLogoutDialog = true },
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
        topBar = {
            TopAppBar(
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Colour", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorSwatch(hex = form.color, size = 36.dp)
                Text(form.color, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SystemColorPalette(selected = form.color, onSelect = { viewModel.updateForm { copy(color = it) } })

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
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Save Changes")
            }
        }
    }
}

private val systemPalette = listOf(
    "#7F77DD", "#534AB7", "#1D9E75", "#E24B4A",
    "#BA7517", "#185FA5", "#D4537E", "#888780",
    "#639922", "#D85A30",
)

@Composable
private fun SystemColorPalette(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        systemPalette.forEach { hex ->
            val color = parseColor(hex) ?: return@forEach
            val isSelected = hex.equals(selected, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(hex) },
                label = {},
                leadingIcon = { Box(Modifier.size(16.dp).padding(1.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = color,
                    selectedContainerColor = color,
                ),
                border = if (isSelected) FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = true,
                    borderColor = MaterialTheme.colorScheme.primary,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                    selectedBorderWidth = 2.dp,
                ) else FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
