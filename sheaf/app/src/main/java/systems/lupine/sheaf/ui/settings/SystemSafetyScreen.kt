package systems.lupine.sheaf.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import systems.lupine.sheaf.data.model.PendingActionRead
import systems.lupine.sheaf.data.model.SafetyChangeRequestRead
import systems.lupine.sheaf.data.model.SystemSafetySettings
import systems.lupine.sheaf.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSafetyScreen(
    onNavigateUp: () -> Unit,
    viewModel: SystemSafetyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val totpEnabled = state.totpEnabled

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("System Safety") },
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

        if (state.loadError != null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ErrorBanner(state.loadError!!)
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
            }
            return@Scaffold
        }

        val draft = state.draft ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IntroBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (state.lastApplied.isNotEmpty() || state.lastDeferred.isNotEmpty()) {
                UpdateResultBanner(
                    applied = state.lastApplied,
                    deferred = state.lastDeferred,
                    onDismiss = { viewModel.clearLastUpdate() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            SectionHeader("Grace period")
            GracePeriodCard(
                value = draft.gracePeriodDays,
                onChange = { viewModel.updateDraft { copy(gracePeriodDays = it) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionHeader("Auth tier")
            AuthTierSelector(
                selected = draft.authTier,
                totpEnabled = totpEnabled,
                onSelect = { viewModel.updateDraft { copy(authTier = it) } },
            )

            SectionHeader("Apply safety to")
            CategoryToggles(
                draft = draft,
                onMembers = { viewModel.updateDraft { copy(appliesToMembers = it) } },
                onGroups = { viewModel.updateDraft { copy(appliesToGroups = it) } },
                onTags = { viewModel.updateDraft { copy(appliesToTags = it) } },
                onFields = { viewModel.updateDraft { copy(appliesToFields = it) } },
                onFronts = { viewModel.updateDraft { copy(appliesToFronts = it) } },
            )

            if (state.saveError != null) {
                ErrorBanner(state.saveError!!, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.revertDraft() },
                    enabled = state.isDirty && !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Discard") }
                Button(
                    onClick = {
                        if (viewModel.draftRequiresReauth()) {
                            viewModel.requestReauth()
                        } else {
                            viewModel.save(null, null)
                        }
                    },
                    enabled = state.isDirty && !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save")
                    }
                }
            }

            if (state.cancelError != null) {
                ErrorBanner(state.cancelError!!, modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (state.pendingActions.isNotEmpty()) {
                SectionHeader("Pending deletions")
                state.pendingActions.forEach { action ->
                    PendingActionRow(
                        action = action,
                        cancelling = action.id in state.cancellingActionIds,
                        onCancel = { viewModel.cancelPendingAction(action.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (state.pendingChanges.isNotEmpty()) {
                SectionHeader("Pending setting changes")
                state.pendingChanges.forEach { change ->
                    PendingChangeRow(
                        change = change,
                        cancelling = change.id in state.cancellingChangeIds,
                        onCancel = { viewModel.cancelPendingChange(change.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    if (state.needsReauth) {
        ReauthDialog(
            authTier = state.settings?.authTier ?: "password",
            totpEnabled = totpEnabled,
            isSaving = state.isSaving,
            errorMessage = state.saveError,
            onConfirm = { password, totpCode -> viewModel.save(password, totpCode) },
            onDismiss = { viewModel.dismissReauth() },
        )
    }
}

@Composable
private fun IntroBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Safety adds a grace period and optional re-auth before destructive actions take effect. " +
                "Tightening applies immediately; loosening waits the same grace period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateResultBanner(
    applied: List<String>,
    deferred: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (applied.isNotEmpty()) {
                Text(
                    "Applied immediately: " + applied.joinToString(", ") { formatField(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (deferred.isNotEmpty()) {
                Text(
                    "Queued for grace period: " + deferred.joinToString(", ") { formatField(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
            ) { Text("Dismiss") }
        }
    }
}

@Composable
private fun GracePeriodCard(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (value == 0) "Off" else "$value ${if (value == 1) "day" else "days"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = value.toString(),
                    onValueChange = { input ->
                        val n = input.filter { it.isDigit() }.toIntOrNull() ?: 0
                        onChange(n.coerceIn(0, 365))
                    },
                    label = { Text("Days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = 0f..30f,
                steps = 29,
            )
            Text(
                "0 disables safety. Slider goes up to 30; the field accepts up to 365.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuthTierSelector(
    selected: String,
    totpEnabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "none" to "None",
        "password" to "Password",
        "totp" to "Authenticator code",
        "both" to "Password + authenticator code",
    )
    Column {
        options.forEachIndexed { index, (value, label) ->
            val needsTotp = value == "totp" || value == "both"
            val enabled = !needsTotp || totpEnabled
            Surface(
                onClick = { if (enabled) onSelect(value) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = if (needsTotp && !totpEnabled) {
                        { Text("Enable 2FA in Security to use this option") }
                    } else null,
                    leadingContent = {
                        RadioButton(
                            selected = selected == value,
                            onClick = { if (enabled) onSelect(value) },
                            enabled = enabled,
                        )
                    },
                )
            }
            if (index != options.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun CategoryToggles(
    draft: SystemSafetySettings,
    onMembers: (Boolean) -> Unit,
    onGroups: (Boolean) -> Unit,
    onTags: (Boolean) -> Unit,
    onFields: (Boolean) -> Unit,
    onFronts: (Boolean) -> Unit,
) {
    val items = listOf(
        Triple("Members", draft.appliesToMembers, onMembers),
        Triple("Groups", draft.appliesToGroups, onGroups),
        Triple("Tags", draft.appliesToTags, onTags),
        Triple("Custom fields", draft.appliesToFields, onFields),
        Triple("Fronts", draft.appliesToFronts, onFronts),
    )
    Column {
        items.forEachIndexed { index, (label, value, onChange) ->
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = {
                    Switch(checked = value, onCheckedChange = onChange)
                },
            )
            if (index != items.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun PendingActionRow(
    action: PendingActionRead,
    cancelling: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${formatActionType(action.actionType)}: ${action.targetLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Finalizes ${formatRelativeTime(action.finalizeAfter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (action.frontingMemberNames.isEmpty()) {
                        "Fronting: no one"
                    } else {
                        "Fronting: ${action.frontingMemberNames.joinToString(", ")}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel, enabled = !cancelling) {
                if (cancelling) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun PendingChangeRow(
    change: SafetyChangeRequestRead,
    cancelling: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(formatChangesSummary(change.changes), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Applies ${formatRelativeTime(change.finalizeAfter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel, enabled = !cancelling) {
                if (cancelling) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ReauthDialog(
    authTier: String,
    totpEnabled: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onConfirm: (password: String?, totpCode: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val needsPassword = authTier == "password" || authTier == "both"
    val needsTotp = (authTier == "totp" || authTier == "both") && totpEnabled
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        title = { Text("Confirm change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Loosening safety settings waits the current grace period before taking effect. Re-authenticate to queue the change.",
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
                enabled = !isSaving &&
                    (!needsPassword || password.isNotBlank()) &&
                    (!needsTotp || totpCode.length == 6),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        },
    )
}

private fun formatField(field: String): String = when (field) {
    "grace_period_days" -> "grace period"
    "auth_tier" -> "auth tier"
    "applies_to_members" -> "members"
    "applies_to_groups" -> "groups"
    "applies_to_tags" -> "tags"
    "applies_to_fields" -> "fields"
    "applies_to_fronts" -> "fronts"
    else -> field
}

private fun formatActionType(type: String): String = when (type) {
    "member_delete" -> "Delete member"
    "group_delete" -> "Delete group"
    "tag_delete" -> "Delete tag"
    "field_delete" -> "Delete field"
    "front_delete" -> "Delete front"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun formatChangesSummary(changes: Map<String, Any?>): String {
    if (changes.isEmpty()) return "Pending change"
    return changes.entries.joinToString("; ") { (k, v) ->
        "${formatField(k)} → ${formatChangeValue(k, v)}"
    }
}

private fun formatChangeValue(field: String, value: Any?): String = when {
    value is Boolean -> if (value) "on" else "off"
    field == "auth_tier" -> when (value?.toString()) {
        "none" -> "None"
        "password" -> "Password"
        "totp" -> "Authenticator"
        "both" -> "Password + Authenticator"
        else -> value?.toString() ?: "—"
    }
    field == "grace_period_days" -> "${(value as? Number)?.toInt() ?: value} days"
    else -> value?.toString() ?: "—"
}

private fun formatRelativeTime(iso: String): String {
    val target = parseIso(iso) ?: return iso
    val now = OffsetDateTime.now()
    val duration = Duration.between(now, target)
    if (duration.isNegative) return "now"
    val days = duration.toDays()
    val hours = duration.minusDays(days).toHours()
    return when {
        days >= 1 -> "in $days ${if (days == 1L) "day" else "days"}"
        hours >= 1 -> "in $hours ${if (hours == 1L) "hour" else "hours"}"
        else -> "in ${duration.toMinutes().coerceAtLeast(1)} min"
    }
}

private fun parseIso(s: String): OffsetDateTime? = try {
    OffsetDateTime.parse(s)
} catch (_: DateTimeParseException) {
    try {
        OffsetDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME)
    } catch (_: DateTimeParseException) {
        null
    }
}
