package systems.lupine.sheaf.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.ui.auth.AuthViewModel
import androidx.lifecycle.viewModelScope
import systems.lupine.sheaf.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToFronting: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToServer: () -> Unit,
    onNavigateToSystem: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToSafety: () -> Unit,
    onNavigateToDanger: () -> Unit,
    onNavigateToAdminPanel: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToDebug: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.state.collectAsState()
    val authConfig by authViewModel.authConfig.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Settings") },
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
            // Account / system summary card up top.
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
                        if (state.system?.avatarUrl != null) {
                            AsyncImage(
                                model = state.system!!.avatarUrl,
                                contentDescription = state.system!!.name,
                                modifier = Modifier.size(52.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
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

            if (state.system != null) {
                SystemStatsRow(
                    frontingCount = state.frontingCount,
                    memberCount = state.memberCount,
                    groupCount = state.groupCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state.error != null) {
                ErrorBanner(state.error!!, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Pending-deletion banner stays on the landing so it's impossible
            // to miss; the full cancel UI lives in Danger zone.
            if (state.user?.accountStatus == "pending_deletion" || state.accountDeletionRequested) {
                val timeRemaining = formatDeletionTimeRemaining(
                    state.user?.deletionRequestedAt,
                    authConfig?.accountDeletionGraceDays,
                )
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            buildString {
                                append("Account deletion requested.")
                                if (timeRemaining != null) append(" $timeRemaining remaining.")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            "Open Danger Zone to cancel.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            if (state.user?.emailVerified == false) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Text(
                        "Your email is not verified. Open Account to resend the verification email.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // Category list. Order roughly matches frequency-of-use.
            Spacer(Modifier.height(8.dp))
            SettingItem(
                icon = Icons.Outlined.Edit,
                title = "Profile",
                subtitle = state.system?.name ?: "System name, description, avatar",
                onClick = onNavigateToProfile,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Person,
                title = "Account",
                subtitle = "Two-factor auth, API keys, sessions, watch pairing",
                onClick = onNavigateToAccount,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Palette,
                title = "Appearance",
                subtitle = "Theme, palette, navigation bar, timezone",
                onClick = onNavigateToAppearance,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.SwitchAccount,
                title = "Fronting",
                subtitle = "What a quick-switch tap does with open fronts",
                onClick = onNavigateToFronting,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.AutoMirrored.Outlined.List,
                title = "System",
                subtitle = "Tags, custom fields, archived members",
                onClick = onNavigateToSystem,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Shield,
                title = "Safety",
                subtitle = formatSafetySubtitle(state.system?.deleteConfirmation),
                onClick = onNavigateToSafety,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Folder,
                title = "Data",
                subtitle = "Storage, files, export, import",
                onClick = onNavigateToData,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Notifications,
                title = "Notifications & Lock",
                subtitle = "Subscriptions, channels, devices, fronting notification, app lock",
                onClick = onNavigateToNotifications,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Storage,
                title = "Server",
                subtitle = settingsViewModel.baseUrl.collectAsState().value.ifBlank { "Not configured" },
                onClick = onNavigateToServer,
            )

            if (state.user?.isAdmin == true) {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingItem(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = "Admin Panel",
                    subtitle = null,
                    onClick = onNavigateToAdminPanel,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.HelpOutline,
                title = "Support",
                subtitle = "Contact, status, and policies",
                onClick = onNavigateToSupport,
            )

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingItem(
                icon = Icons.Outlined.Warning,
                title = "Danger Zone",
                subtitle = "Sign out, delete account",
                onClick = onNavigateToDanger,
                tint = MaterialTheme.colorScheme.error,
            )

            if (BuildConfig.DEBUG) {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingItem(
                    icon = Icons.Outlined.BugReport,
                    title = "Debug Menu",
                    subtitle = "Developer tools",
                    onClick = onNavigateToDebug,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            // About row. Stamps the actual binary identity onto the screen so
            // "is this build the one I just built" is a glance away — no
            // dumpsys, no aapt2, no guessing about whether Studio installed
            // what you think it did.
            SettingItem(
                icon = Icons.Outlined.Info,
                title = "About",
                subtitle = buildString {
                    append("Sheaf ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    append(" · ${BuildConfig.GIT_COMMIT}")
                    append(" · built ${BuildConfig.BUILD_TIME}")
                    append(" · ${BuildConfig.FLAVOR}")
                    if (BuildConfig.DEBUG) append(" · debug")
                },
                onClick = {},
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}


// ── TOTP Setup Sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TotpSetupSheet(
    state: SettingsUiState,
    onAdvanceToVerify: () -> Unit,
    onVerify: (String) -> Unit,
    onAdvanceToDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
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
                    Text("Add to Authenticator", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Add this to your authenticator app (Aegis, 1Password, Google Authenticator). Tap the secret to copy it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.totpSetupResponse != null) {
                        Surface(
                            onClick = {
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", state.totpSetupResponse.secret))) }
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
                    ) { Text("I've added it, next") }
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
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", codes.joinToString("\n")))) }
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
                    ) { Text("I've saved them, done") }
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
internal fun SettingItem(
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

internal fun formatTier(tier: String): String = when (tier) {
    "saas"        -> "SaaS"
    "self_hosted" -> "Self-hosted"
    else          -> tier.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "${"%.1f".format(bytes / 1_024.0)} KB"
    bytes < 1_073_741_824 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    else -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
}

internal fun formatSafetySubtitle(level: String?): String = when (level) {
    "none" -> "Revision retention \u00b7 re-auth: none"
    "password" -> "Revision retention \u00b7 re-auth: password"
    "totp" -> "Revision retention \u00b7 re-auth: authenticator code"
    "both" -> "Revision retention \u00b7 re-auth: password + authenticator"
    else -> "Revision retention, grace period, re-auth for destructive actions"
}

// ── System stats row ──────────────────────────────────────────────────────────

@Composable
private fun SystemStatsRow(
    frontingCount: Int,
    memberCount: Int,
    groupCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(
            icon = Icons.Outlined.SwitchAccount,
            count = frontingCount,
            label = if (frontingCount == 1) "fronter" else "fronters",
            modifier = Modifier.weight(1f),
        )
        StatChip(
            icon = Icons.Filled.People,
            count = memberCount,
            label = if (memberCount == 1) "member" else "members",
            modifier = Modifier.weight(1f),
        )
        StatChip(
            icon = Icons.Outlined.Group,
            count = groupCount,
            label = if (groupCount == 1) "group" else "groups",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
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
    var showAvatarMenu by remember { mutableStateOf(false) }
    // See MemberDetailScreen for the picker-then-crop pattern. Same
    // shape here for the system avatar.
    var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pendingCropUri = uri }

    pendingCropUri?.let { uri ->
        systems.lupine.sheaf.ui.avatar.AvatarCropDialog(
            sourceUri = uri,
            onCancel = { pendingCropUri = null },
            onConfirm = { bytes ->
                pendingCropUri = null
                viewModel.uploadAvatarBytes(bytes)
            },
        )
    }

    val descriptionImagePicker = rememberMarkdownImagePicker(
        viewModel.markdownImages,
        viewModel.viewModelScope,
    )

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

            // Avatar picker
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    if (form.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = form.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { showAvatarMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit avatar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    DropdownMenu(
                        expanded = showAvatarMenu,
                        onDismissRequest = { showAvatarMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Choose photo") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showAvatarMenu = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                        if (form.avatarUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Remove avatar", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showAvatarMenu = false
                                    viewModel.removeAvatar()
                                },
                            )
                        }
                    }
                }

                if (state.isUploadingAvatar) {
                    CircularProgressIndicator(modifier = Modifier.size(88.dp), strokeWidth = 3.dp)
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.updateForm { copy(name = it) } },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            MarkdownBodyEditor(
                value = form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = "Description",
                minLines = 3,
                imagePicker = descriptionImagePicker,
            )

            OutlinedTextField(
                value = form.note,
                onValueChange = { viewModel.updateForm { copy(note = it) } },
                label = { Text("Scratchpad notes") },
                placeholder = { Text("Anything you want to keep handy about your system") },
                minLines = 3,
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
                value = if (form.avatarUrl.contains("/v1/files/")) "" else form.avatarUrl,
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

            SectionHeader("Display")
            // Part of this form rather than an instant-apply toggle, so it
            // saves with the Save Changes button like everything else here.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.updateForm {
                            copy(showMemberCreatedDate = !showMemberCreatedDate)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show member created dates", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Show when each member was added, on their profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.showMemberCreatedDate,
                    onCheckedChange = { viewModel.updateForm { copy(showMemberCreatedDate = it) } },
                )
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

