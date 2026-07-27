@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import systems.lupine.sheaf.ui.theme.SheafPalette
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.COMMON_ZONES
import systems.lupine.sheaf.ui.components.TZ_AUTO
import systems.lupine.sheaf.ui.components.allTimeZoneIds
import systems.lupine.sheaf.ui.components.friendlyZoneLabel
import systems.lupine.sheaf.BuildConfig
import systems.lupine.sheaf.data.repository.baseUrlError
import systems.lupine.sheaf.ui.auth.AuthViewModel
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.components.StorageQuotaCard
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Common scaffold: every category detail screen wears the same TopAppBar +
// scroll-column shell, only the content varies.
@Composable
private fun CategoryScaffold(
    title: String,
    onNavigateUp: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text(title) },
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
            content = content,
        )
    }
}

// ── Appearance ──────────────────────────────────────────────────────────────

@Composable
fun AppearanceSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val themePalette by viewModel.themePalette.collectAsState()
    val themeSynced by viewModel.themeSynced.collectAsState()
    CategoryScaffold(title = "Appearance", onNavigateUp = onNavigateUp) {
        val themeModes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
        val themeIcons = mapOf(
            "system" to Icons.Outlined.BrightnessAuto,
            "light"  to Icons.Outlined.LightMode,
            "dark"   to Icons.Outlined.DarkMode,
        )
        themeModes.forEach { (mode, label) ->
            Surface(
                onClick = { viewModel.saveTheme(mode) },
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
        HorizontalDivider()
        PaletteSection(
            selectedId = themePalette,
            themeMode = themeMode,
            onSelected = { viewModel.savePalette(it) },
        )
        HorizontalDivider()
        ThemeSyncRow(
            synced = themeSynced,
            onChange = { viewModel.setThemeSynced(it) },
        )
        HorizontalDivider()
        TimezoneSection()
    }
}

// ── Timezone ──────────────────────────────────────────────────────────────────

/**
 * Two-tier display-timezone control, mirroring web's system-profile-card:
 * the account default (synced) and a per-device override. See
 * [systems.lupine.sheaf.ui.components.resolveDisplayZoneId].
 */
@Composable
private fun TimezoneSection(viewModel: TimezoneViewModel = hiltViewModel()) {
    val account by viewModel.accountTimezone.collectAsState()
    val override by viewModel.deviceOverride.collectAsState()
    val effective by viewModel.effectiveZone.collectAsState()
    val state by viewModel.state.collectAsState()

    var showAccountPicker by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }

    Text(
        "Timezone",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )

    Surface(onClick = { showAccountPicker = true }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text("Account timezone") },
            supportingContent = {
                Text(account?.let { friendlyZoneLabel(it) } ?: "Automatic (each device's own clock)")
            },
            trailingContent = if (state.isSaving) ({
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }) else null,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
    Surface(onClick = { showDevicePicker = true }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    Icons.Outlined.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            headlineContent = { Text("On this device") },
            supportingContent = { Text(deviceOverrideLabel(override)) },
        )
    }
    Text(
        "Showing times in ${friendlyZoneLabel(effective.id)}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
    state.error?.let { err ->
        Text(
            err,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
    }

    if (showAccountPicker) {
        TimeZonePickerDialog(
            title = "Account timezone",
            special = listOf(null to "Automatic (each device's own clock)"),
            current = account,
            onDismiss = { showAccountPicker = false },
            onPick = {
                viewModel.setAccountTimezone(it)
                showAccountPicker = false
            },
        )
    }
    if (showDevicePicker) {
        TimeZonePickerDialog(
            title = "On this device",
            special = listOf(
                null to "Follow account",
                TZ_AUTO to "This device's own clock",
            ),
            current = override,
            onDismiss = { showDevicePicker = false },
            onPick = {
                viewModel.setDeviceOverride(it)
                showDevicePicker = false
            },
        )
    }
}

private fun deviceOverrideLabel(override: String?): String = when (override) {
    null -> "Follow account"
    TZ_AUTO -> "This device's own clock"
    else -> friendlyZoneLabel(override)
}

/**
 * Searchable IANA zone picker, mirroring web's timezone-select: [special]
 * entries (e.g. "Automatic", "Follow account") at the top, then a "Common"
 * group of friendly DST-compensated names, then the full "All time zones" list.
 * Typing filters the Common group (by label or id) and the full list.
 */
@Composable
private fun TimeZonePickerDialog(
    title: String,
    special: List<Pair<String?, String>>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val allZones = remember { allTimeZoneIds() }
    val q = query.trim()
    val commonFiltered = remember(q) {
        if (q.isEmpty()) COMMON_ZONES
        else COMMON_ZONES.filter { it.label.contains(q, ignoreCase = true) || it.zone.contains(q, ignoreCase = true) }
    }
    val allFiltered = remember(q) {
        if (q.isEmpty()) allZones else allZones.filter { it.contains(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (q.isEmpty()) {
                        items(special) { (value, label) ->
                            ZonePickerRow(label, selected = value == current, onClick = { onPick(value) })
                        }
                    }
                    if (commonFiltered.isNotEmpty()) {
                        item { ZonePickerSectionHeader("Common") }
                        items(commonFiltered) { c ->
                            ZonePickerRow(c.label, selected = c.zone == current, onClick = { onPick(c.zone) })
                        }
                    }
                    if (allFiltered.isNotEmpty()) {
                        item { ZonePickerSectionHeader("All time zones") }
                        items(allFiltered) { zone ->
                            ZonePickerRow(zone, selected = zone == current, onClick = { onPick(zone) })
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ZonePickerSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun ZonePickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Sync-across-Android-devices toggle. Mirrors web's equivalent but
 * lives in a separate client_settings blob (`client_id = "android"`)
 * so palettes that only exist on Android (Material You) don't have to
 * round-trip through web's settings shape.
 */
@Composable
private fun ThemeSyncRow(
    synced: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text("Sync theme across my Android devices") },
        supportingContent = {
            Text(
                if (synced) {
                    "Your mode and palette follow your account. Changes here apply " +
                        "to every Android device logged in and synced."
                } else {
                    "This device keeps its own mode and palette. Other Android " +
                        "devices follow their own picks (or your last synced choice)."
                },
            )
        },
        leadingContent = {
            Icon(
                if (synced) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (synced) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = synced, onCheckedChange = onChange)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteSection(
    selectedId: String,
    themeMode: String,
    onSelected: (String) -> Unit,
) {
    // Resolve the user's themeMode (light / dark / system) to a
    // concrete "is the app currently dark" flag so swatches preview
    // the variant the user is actually looking at. Without this, the
    // swatches always rendered dark regardless of mode and gave a
    // misleading preview to anyone on light or system-light.
    val isDarkPreview = when (themeMode) {
        "dark"  -> true
        "light" -> false
        else    -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Palette",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SheafPalette.all.forEach { palette ->
                PaletteCard(
                    palette = palette,
                    selected = palette.id == selectedId,
                    isDarkPreview = isDarkPreview,
                    onClick = { onSelected(palette.id) },
                )
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: SheafPalette,
    selected: Boolean,
    isDarkPreview: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // Material You's colours are derived from the system wallpaper at
    // render time, so its swatch can only show real values on Android
    // 12+. On older devices we fall through to the palette's static
    // sentinel scheme, which signals "this is unavailable here"
    // naturally enough.
    val scheme = if (
        palette.id == SheafPalette.MATERIAL_YOU_ID &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (isDarkPreview) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        if (isDarkPreview) palette.dark else palette.light
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val borderWidth = if (selected) 2.dp else 1.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = scheme.background,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .width(110.dp)
            .height(96.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Three colour dots give a glance read of the palette's
            // accents without rendering a fake mini-screen — primary
            // covers buttons / FAB, secondary covers chips, tertiary
            // is the success / status hue.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SwatchDot(scheme.primary)
                SwatchDot(scheme.secondary)
                SwatchDot(scheme.tertiary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color),
    )
}

// ── Notifications ───────────────────────────────────────────────────────────

@Composable
fun NotificationSettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToReceiving: () -> Unit,
    onNavigateToYourDevices: () -> Unit,
    onNavigateToChannelsYouOwn: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val frontNotificationEnabled by viewModel.frontNotificationEnabled.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val context = LocalContext.current
    var appLockError by remember { mutableStateOf<String?>(null) }
    var showDisableAppLockDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleFrontNotification(true)
    }

    CategoryScaffold(title = "Notifications & Lock", onNavigateUp = onNavigateUp) {
        SettingItem(
            icon = Icons.Outlined.NotificationsActive,
            title = "Receiving",
            subtitle = "Subscriptions delivering to this account",
            onClick = onNavigateToReceiving,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.AutoMirrored.Outlined.Send,
            title = "Channels you own",
            subtitle = "Invite people to receive notifications from your system",
            onClick = onNavigateToChannelsYouOwn,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.DevicesOther,
            title = "Your devices",
            subtitle = "Phones and tablets registered to receive push",
            onClick = onNavigateToYourDevices,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        ListItem(
            headlineContent = { Text("Fronting Notification") },
            supportingContent = { Text("Persistent silent notification showing who's fronting") },
            leadingContent = {
                Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                if (granted) viewModel.toggleFrontNotification(true)
                                else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleFrontNotification(true)
                            }
                        } else viewModel.toggleFrontNotification(false)
                    },
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        ListItem(
            headlineContent = { Text("App Lock") },
            supportingContent = {
                Text(
                    appLockError ?: "Require biometrics or your device passcode to open Sheaf",
                    color = if (appLockError != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = if (appLockEnabled) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val canAuth = BiometricManager.from(context).canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                            )
                            when (canAuth) {
                                BiometricManager.BIOMETRIC_SUCCESS -> {
                                    appLockError = null
                                    viewModel.toggleAppLock(true)
                                }
                                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                    appLockError = "Set up a screen lock or biometric in your device settings first."
                                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                                    appLockError = "This device doesn't support biometric or passcode unlock."
                                else ->
                                    appLockError = "App lock is unavailable on this device right now."
                            }
                        } else {
                            appLockError = null
                            showDisableAppLockDialog = true
                        }
                    },
                )
            },
        )
    }

    if (showDisableAppLockDialog) {
        AlertDialog(
            onDismissRequest = { showDisableAppLockDialog = false },
            title = { Text("Disable App Lock?") },
            text = { Text("Sheaf will open without requiring your biometrics or device passcode.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.toggleAppLock(false); showDisableAppLockDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableAppLockDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Server ─────────────────────────────────────────────────────────────────

@Composable
fun ServerSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedBaseUrl by viewModel.baseUrl.collectAsState()
    var urlDraft by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    CategoryScaffold(title = "Server", onNavigateUp = onNavigateUp) {
        SettingItem(
            icon = Icons.Outlined.Storage,
            title = "API Server",
            subtitle = savedBaseUrl.ifBlank { "Not configured" },
            onClick = { urlDraft = savedBaseUrl; urlError = null; showUrlDialog = true },
        )
    }
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("API Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it; urlError = null },
                        label = { Text("Base URL or domain") },
                        placeholder = { Text("app.sheaf.sh") },
                        singleLine = true,
                        isError = urlError != null,
                        supportingText = urlError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "https:// is added automatically. Servers must use https; a path is " +
                            "fine (e.g. example.org/sheaf).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = urlDraft.trim()
                    val problem = baseUrlError(candidate, BuildConfig.DEBUG)
                    urlError = problem
                    if (problem == null) {
                        viewModel.saveBaseUrl(candidate)
                        showUrlDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") } },
        )
    }
}

// ── System (custom fields, future: tags, front prefs) ──────────────────────

@Composable
fun SystemCategoryScreen(
    onNavigateUp: () -> Unit,
    onNavigateToCustomFields: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToArchivedMembers: () -> Unit,
) {
    CategoryScaffold(title = "System", onNavigateUp = onNavigateUp) {
        SettingItem(
            icon = Icons.Outlined.LocalOffer,
            title = "Tags",
            subtitle = "Labels you can apply to members",
            onClick = onNavigateToTags,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.AutoMirrored.Outlined.List,
            title = "Custom Fields",
            subtitle = "Define additional fields for member profiles",
            onClick = onNavigateToCustomFields,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Archive,
            title = "Archived members",
            subtitle = "View and restore archived members",
            onClick = onNavigateToArchivedMembers,
        )
    }
}

// ── Safety ─────────────────────────────────────────────────────────────────

@Composable
fun SafetyCategoryScreen(
    onNavigateUp: () -> Unit,
    onNavigateToSystemSafety: () -> Unit,
    onNavigateToRetention: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    CategoryScaffold(title = "Safety", onNavigateUp = onNavigateUp) {
        SettingItem(
            icon = Icons.Outlined.Shield,
            title = "System Safety",
            subtitle = formatSafetySubtitle(state.system?.deleteConfirmation),
            onClick = onNavigateToSystemSafety,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.History,
            title = "Revision retention",
            subtitle = "How long edits to bios and journals are kept",
            onClick = onNavigateToRetention,
        )
    }
}

// ── Data ───────────────────────────────────────────────────────────────────

@Composable
fun DataSettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToExportData: () -> Unit,
    onNavigateToSpImport: () -> Unit,
    onNavigateToSheafImport: () -> Unit,
    onNavigateToPkFileImport: () -> Unit,
    onNavigateToPkApiImport: () -> Unit,
    onNavigateToTupperboxImport: () -> Unit,
    onNavigateToPluralSpaceImport: () -> Unit,
    onNavigateToPrismImport: () -> Unit,
    onNavigateToOpenPluralImport: () -> Unit,
    onNavigateToAmpersandImport: () -> Unit,
    onNavigateToImportHistory: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteOrphansDialog by remember { mutableStateOf(false) }

    // Full export UI (format selector, JSON vs full-backup-with-images, recent
    // backups) lives on its own ExportDataScreen; this screen just links to it.

    LaunchedEffect(state.orphanedFiles) {
        if (state.orphanedFiles != null && state.orphanedFiles!!.isNotEmpty()) {
            showDeleteOrphansDialog = true
        } else if (state.orphanedFiles?.isEmpty() == true) {
            showDeleteOrphansDialog = false
        }
    }

    CategoryScaffold(title = "Data", onNavigateUp = onNavigateUp) {
        StorageQuotaCard(
            usage = state.fileUsage,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SettingItem(
            icon = Icons.Outlined.PhotoLibrary,
            title = "Uploaded files",
            subtitle = "Browse, preview, and delete uploads",
            onClick = onNavigateToFiles,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = if (state.isCheckingFiles) Icons.Outlined.HourglassEmpty else Icons.Outlined.DeleteSweep,
            title = if (state.isCheckingFiles) "Checking…" else "Delete unused files",
            subtitle = when {
                state.orphanedFiles?.isEmpty() == true -> "No unused files found"
                state.orphanDeleteResultMessage != null -> state.orphanDeleteResultMessage!!
                else -> "Find and delete uploads no member or system still references"
            },
            onClick = { if (!state.isCheckingFiles) viewModel.checkOrphanedFiles() },
        )
        if (state.fileError != null) {
            ErrorBanner(state.fileError!!, modifier = Modifier.padding(horizontal = 16.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Download,
            title = "Export data",
            subtitle = "JSON or full backup, in Sheaf or OpenPlural format",
            onClick = onNavigateToExportData,
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
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Upload,
            title = "Import from PluralKit (file)",
            subtitle = "Use a PK export JSON from `pk;export`",
            onClick = onNavigateToPkFileImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.CloudDownload,
            title = "Import from PluralKit (API)",
            subtitle = "Connect with your PK token to import live",
            onClick = onNavigateToPkApiImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Upload,
            title = "Import from Tupperbox",
            subtitle = "Use a Tupperbox export JSON from `tul!export`",
            onClick = onNavigateToTupperboxImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Upload,
            title = "Import from PluralSpace",
            subtitle = "Use a PluralSpace .zip data export",
            onClick = onNavigateToPluralSpaceImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Upload,
            title = "Import from Prism",
            subtitle = "Use an encrypted .prism export and its passphrase",
            onClick = onNavigateToPrismImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Upload,
            title = "Import from OpenPlural",
            subtitle = "Use an OpenPlural .json or .openplural.zip export",
            onClick = onNavigateToOpenPluralImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.Upload,
            title = "Import from Ampersand",
            subtitle = "Use an Ampersand .json data export",
            onClick = onNavigateToAmpersandImport,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        SettingItem(
            icon = Icons.Outlined.History,
            title = "Import history",
            subtitle = "Past and pending imports — status, counts, and warnings",
            onClick = onNavigateToImportHistory,
        )
    }

    if (showDeleteOrphansDialog) {
        val orphans = state.orphanedFiles ?: emptyList()
        OrphanFilesDeleteDialog(
            fileCount = orphans.size,
            totalBytesLabel = formatBytes(orphans.sumOf { it.sizeBytes }),
            safety = state.orphanDeleteSafety,
            isDeleting = state.isDeletingOrphans,
            errorMessage = state.fileError,
            onConfirm = { password, totpCode ->
                viewModel.deleteOrphanedFiles(password, totpCode)
                showDeleteOrphansDialog = false
            },
            onDismiss = {
                showDeleteOrphansDialog = false
                viewModel.clearOrphanedFiles()
            },
        )
    }
}

// ── Account ────────────────────────────────────────────────────────────────

@Composable
fun AccountSettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToAdminActivity: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showTotpSheet by remember { mutableStateOf(false) }
    var showDisableTotpDialog by remember { mutableStateOf(false) }

    CategoryScaffold(title = "Account", onNavigateUp = onNavigateUp) {
        if (state.user?.emailVerified == false) {
            EmailVerificationBanner(
                sent = state.verificationEmailSent,
                isResending = state.isResendingVerification,
                onResend = { viewModel.resendVerificationEmail() },
            )
        }

        AccountInfoCard(
            email = state.user?.email,
            emailVerified = state.user?.emailVerified ?: false,
            tier = state.user?.tier,
            accountStatus = state.user?.accountStatus,
            createdAt = state.user?.createdAt,
            lastLoginAt = state.user?.lastLoginAt,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

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
                onClick = { viewModel.startTotpSetup(); showTotpSheet = true },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
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
        SettingItem(
            icon = Icons.Outlined.History,
            title = "Admin activity",
            subtitle = "Actions administrators have taken on your account",
            onClick = onNavigateToAdminActivity,
        )
        HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        var showWatchRepairDialog by remember { mutableStateOf(false) }
        SettingItem(
            icon = Icons.Outlined.Watch,
            title = "Re-pair watch",
            subtitle = when {
                state.watchRepairing -> "Refreshing watch credentials…"
                state.watchRepairCompleted -> "Watch credentials refreshed"
                state.watchRepairError != null -> state.watchRepairError!!
                else -> "Force-refresh the watch session if pairing seems stuck"
            },
            onClick = { showWatchRepairDialog = true },
        )
        if (showWatchRepairDialog) {
            AlertDialog(
                onDismissRequest = { showWatchRepairDialog = false },
                title = { Text("Re-pair watch?") },
                text = {
                    Text(
                        "Drops the cached watch session and asks the server " +
                            "for a fresh one, then pushes the new credentials " +
                            "to the watch. Use this if the watch is stuck on " +
                            "\"Open Sheaf on phone\" despite being signed in here.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showWatchRepairDialog = false
                        viewModel.clearWatchRepair()
                        viewModel.repairWatchPairing()
                    }) { Text("Re-pair") }
                },
                dismissButton = {
                    TextButton(onClick = { showWatchRepairDialog = false }) { Text("Cancel") }
                },
            )
        }
    }

    if (showTotpSheet) {
        TotpSetupSheet(
            state = state,
            onAdvanceToVerify = { viewModel.advanceTotpToVerify() },
            onVerify = { code -> viewModel.verifyTotp(code) },
            onAdvanceToDone = { viewModel.advanceTotpToDone() },
            onDismiss = {
                showTotpSheet = false
                viewModel.resetTotpSetup()
            },
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
                    onClick = { viewModel.disableTotp(disablePassword, disableTotpCode) },
                    enabled = disablePassword.isNotBlank() && disableTotpCode.length == 6 && !state.totpIsDisabling,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (state.totpIsDisabling) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableTotpDialog = false; viewModel.clearTotpError() }) { Text("Cancel") }
            },
        )
        LaunchedEffect(state.user?.totpEnabled) {
            if (state.user?.totpEnabled == false) showDisableTotpDialog = false
        }
    }
}

@Composable
private fun AccountInfoCard(
    email: String?,
    emailVerified: Boolean,
    tier: String?,
    accountStatus: String?,
    createdAt: String?,
    lastLoginAt: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Email row with a small verification badge.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        email ?: "...",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (email != null) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            colors = AssistChipDefaults.assistChipColors(
                                disabledLabelColor = if (emailVerified) MaterialTheme.colorScheme.tertiary
                                                    else MaterialTheme.colorScheme.error,
                            ),
                            label = {
                                Text(
                                    if (emailVerified) "verified" else "unverified",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            // Tier + status side-by-side.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Plan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        tier?.takeIf { it.isNotBlank() }?.let { formatTier(it) } ?: "...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when (accountStatus) {
                            "pending_deletion" -> "Pending deletion"
                            "active", null -> "Active"
                            else -> accountStatus.replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (accountStatus == "pending_deletion") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            HorizontalDivider()

            // Member since + last login.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Member since", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatIsoDate(createdAt) ?: "...", style = MaterialTheme.typography.bodyMedium)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Last login", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatIsoDate(lastLoginAt) ?: "...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val accountDateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun formatIsoDate(iso: String?): String? = iso?.let {
    runCatching { java.time.OffsetDateTime.parse(it).toLocalDate().format(accountDateFormatter) }.getOrNull()
}

@Composable
private fun EmailVerificationBanner(
    sent: Boolean,
    isResending: Boolean,
    onResend: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (sent) "Verification email sent. Check your inbox."
                else "Your email address is not verified.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (!sent) {
                TextButton(
                    onClick = onResend,
                    enabled = !isResending,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    if (isResending) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Resend verification email", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ── Danger zone ────────────────────────────────────────────────────────────

@Composable
fun DangerZoneScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val authConfig by authViewModel.authConfig.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.accountDeletionRequested) {
        if (state.accountDeletionRequested) {
            showDeleteAccountDialog = false
            viewModel.clearAccountDeletionRequested()
            authViewModel.logout()
        }
    }

    CategoryScaffold(title = "Danger Zone", onNavigateUp = onNavigateUp) {
        if (state.user?.accountStatus == "pending_deletion" || state.accountDeletionRequested) {
            val timeRemaining = systems.lupine.sheaf.ui.components.formatDeletionTimeRemaining(
                state.user?.deletionRequestedAt,
                authConfig?.accountDeletionGraceDays,
            )
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        buildString {
                            append("Account deletion requested.")
                            if (timeRemaining != null) append(" $timeRemaining remaining.")
                            append(" Your account will be permanently deleted after the grace period.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    state.cancelDeletionError?.let { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = { viewModel.cancelAccountDeletion() },
                        enabled = !state.isCancellingDeletion,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isCancellingDeletion) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Cancel Deletion")
                        }
                    }
                }
            }
        }

        SettingItem(
            icon = Icons.AutoMirrored.Outlined.Logout,
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

    if (showDeleteAccountDialog) {
        val totpEnabled = state.user?.totpEnabled == true
        var deletePassword by remember { mutableStateOf("") }
        var deleteTotpCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                if (!state.isDeletingAccount) {
                    showDeleteAccountDialog = false
                    viewModel.clearDeletionError()
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
                        viewModel.requestAccountDeletion(
                            deletePassword,
                            deleteTotpCode.takeIf { totpEnabled },
                        )
                    },
                    enabled = deletePassword.isNotBlank() &&
                        (!totpEnabled || deleteTotpCode.length == 6) &&
                        !state.isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (state.isDeletingAccount) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false; viewModel.clearDeletionError() },
                    enabled = !state.isDeletingAccount,
                ) { Text("Cancel") }
            },
        )
    }
}
