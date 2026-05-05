@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.retention

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.RetentionResponse
import systems.lupine.sheaf.data.model.RetentionTrimNoticeRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun RetentionScreen(
    onNavigateUp: () -> Unit,
    viewModel: RetentionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Revision retention") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (state.error != null) ErrorBanner(state.error!!)
            if (state.resultMessage != null) {
                Text(
                    state.resultMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val data = state.data
            if (data != null) {
                RetentionExplainer()
                EffectiveCapsCard(data)
                if (data.trimNotice != null) {
                    TrimNoticeCard(
                        notice = data.trimNotice!!,
                        isCancelling = state.isCancellingTrim,
                        onCancel = { viewModel.cancelTrimNotice() },
                    )
                }
                OverrideForm(
                    data = data,
                    isSaving = state.isSaving,
                    saveError = state.saveError,
                    onSave = viewModel::proposeUpdate,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    state.pendingLooseningUpdate?.let {
        LooseningStepUpDialog(
            authTier = state.authTier,
            totpEnabled = state.totpEnabled,
            gracePeriodDays = state.gracePeriodDays,
            isSaving = state.isSaving,
            errorMessage = state.saveError,
            onConfirm = { p, t -> viewModel.confirmLoosening(p, t) },
            onDismiss = { viewModel.cancelPendingUpdate() },
        )
    }
}

@Composable
private fun RetentionExplainer() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("How retention works", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Each edit to a member bio or journal entry creates a revision. Older revisions are pruned " +
                    "either by count (older than the Nth most recent) or by age (older than N days), whichever " +
                    "limit hits first. 0 means \"no limit\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Lowering a limit prunes existing revisions over the new threshold, so it goes through " +
                    "the System Safety grace period (with re-auth) before applying.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EffectiveCapsCard(data: RetentionResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Currently keeping", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                buildString {
                    append("Up to ${capLabel(data.effectiveMaxRevisions)} revisions")
                    append(" or ${capLabel(data.effectiveMaxDays)} day${if (data.effectiveMaxDays == 1) "" else "s"}")
                    append(", whichever is shorter.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Your plan allows up to ${capLabel(data.tierMaxRevisions)} revisions / " +
                    "${capLabel(data.tierMaxDays)} day${if (data.tierMaxDays == 1) "" else "s"}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverrideForm(
    data: RetentionResponse,
    isSaving: Boolean,
    saveError: String?,
    onSave: (Int?, Int?) -> Unit,
) {
    var revText by remember(data) {
        mutableStateOf((data.overrideRevisions ?: data.tierMaxRevisions).toString())
    }
    var daysText by remember(data) {
        mutableStateOf((data.overrideDays ?: data.tierMaxDays).toString())
    }
    var revIsTierDefault by remember(data) { mutableStateOf(data.overrideRevisions == null) }
    var daysIsTierDefault by remember(data) { mutableStateOf(data.overrideDays == null) }

    val revInt = revText.toIntOrNull()
    val daysInt = daysText.toIntOrNull()
    val revAboveTier = revInt != null && data.tierMaxRevisions > 0 && revInt > data.tierMaxRevisions
    val daysAboveTier = daysInt != null && data.tierMaxDays > 0 && daysInt > data.tierMaxDays
    val canSave = !isSaving &&
        (revIsTierDefault || (revInt != null && revInt >= 0 && !revAboveTier)) &&
        (daysIsTierDefault || (daysInt != null && daysInt >= 0 && !daysAboveTier))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set your own caps", style = MaterialTheme.typography.titleSmall)

            CapField(
                label = "Max revisions per item",
                tierMax = data.tierMaxRevisions,
                value = revText,
                isTierDefault = revIsTierDefault,
                aboveTier = revAboveTier,
                onValueChange = { revText = it; revIsTierDefault = false },
                onUseTierDefault = {
                    revIsTierDefault = true
                    revText = data.tierMaxRevisions.toString()
                },
            )
            CapField(
                label = "Max age (days)",
                tierMax = data.tierMaxDays,
                value = daysText,
                isTierDefault = daysIsTierDefault,
                aboveTier = daysAboveTier,
                onValueChange = { daysText = it; daysIsTierDefault = false },
                onUseTierDefault = {
                    daysIsTierDefault = true
                    daysText = data.tierMaxDays.toString()
                },
            )

            if (saveError != null) {
                Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val rev = if (revIsTierDefault) null else revInt
                    val days = if (daysIsTierDefault) null else daysInt
                    onSave(rev, days)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Save")
            }
        }
    }
}

@Composable
private fun CapField(
    label: String,
    tierMax: Int,
    value: String,
    isTierDefault: Boolean,
    aboveTier: Boolean,
    onValueChange: (String) -> Unit,
    onUseTierDefault: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { c -> c.isDigit() } || it.isEmpty()) onValueChange(it) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = aboveTier,
            supportingText = {
                Text(
                    when {
                        aboveTier -> "Plan limit is ${capLabel(tierMax)}; lower the value or use the tier default."
                        isTierDefault -> "Using plan limit (${capLabel(tierMax)})."
                        value == "0" -> "0 means no limit on this dimension."
                        else -> "Plan limit: ${capLabel(tierMax)}."
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isTierDefault, onCheckedChange = { if (it) onUseTierDefault() })
            Text("Use plan default", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrimNoticeCard(
    notice: RetentionTrimNoticeRead,
    isCancelling: Boolean,
    onCancel: () -> Unit,
) {
    val effectiveAt = formatDate(notice.effectiveAt) ?: notice.effectiveAt
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Plan downgrade trim pending",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                "Your plan changed from ${notice.fromTier} to ${notice.toTier}. Revisions over the new " +
                    "tier limits will be pruned on $effectiveAt unless you upgrade or cancel below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(
                onClick = onCancel,
                enabled = !isCancelling,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCancelling) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Cancel trim")
            }
        }
    }
}

@Composable
private fun LooseningStepUpDialog(
    authTier: String,
    totpEnabled: Boolean,
    gracePeriodDays: Int,
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
        title = { Text("Confirm retention reduction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (gracePeriodDays > 0) {
                        "Lowering retention will prune older revisions in $gracePeriodDays " +
                            "${if (gracePeriodDays == 1) "day" else "days"}. Cancel from System Safety " +
                            "before then to back out."
                    } else {
                        "Lowering retention will prune older revisions immediately."
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
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (gracePeriodDays > 0) "Queue" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        },
    )
}

private fun capLabel(value: Int): String = if (value <= 0) "unlimited" else value.toString()

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun formatDate(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).toLocalDate().format(dateFormatter)
}.getOrNull()
