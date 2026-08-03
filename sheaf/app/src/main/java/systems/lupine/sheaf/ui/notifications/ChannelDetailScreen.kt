package systems.lupine.sheaf.ui.notifications

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.GroupRuleSpec
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.MemberRuleSpec
import systems.lupine.sheaf.data.model.NotificationChannelRead
import systems.lupine.sheaf.data.model.QuietHoursSpec
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    channelId: String,
    onNavigateUp: () -> Unit,
    onChannelDuplicated: (String) -> Unit = {},
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(channelId) { viewModel.load(channelId) }

    // Surface test-dispatch results in the snackbar. The viewmodel clears
    // testResult after we've shown it once so reopening the screen doesn't
    // re-fire the toast.
    LaunchedEffect(state.testResult) {
        state.testResult?.let { result ->
            scope.launch {
                snackbar.showSnackbar(result.message)
                viewModel.dismissTestResult()
            }
        }
    }

    // Pick up the result of a Duplicate action: nav to the new channel,
    // and if the backend handed us a fresh activation link surface that
    // via the pending-activation panel on landing. We do this in a
    // LaunchedEffect so it survives recomposition cleanly.
    LaunchedEffect(state.duplicateResponse) {
        state.duplicateResponse?.let { resp ->
            viewModel.consumeDuplicateResponse()
            onChannelDuplicated(resp.channel.id)
        }
    }

    val channel = state.channel
    val draft = state.draft
    val merged = channel?.merged(draft)
    val isPending = merged?.destinationState?.equals("pending_registration", ignoreCase = true) == true
    val isActive = merged?.destinationState?.equals("active", ignoreCase = true) == true
    val isDisabled = merged?.destinationState?.equals("disabled", ignoreCase = true) == true
    val dirty = draft.isDirty()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SheafTopAppBar(
                title = { Text("Channel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (channel != null) {
                        Box {
                            IconButton(onClick = { showActions = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showActions,
                                onDismissRequest = { showActions = false },
                            ) {
                                if (isActive || isDisabled) {
                                    DropdownMenuItem(
                                        text = { Text(if (isActive) "Pause" else "Resume") },
                                        leadingIcon = {
                                            Icon(
                                                if (isActive) Icons.Outlined.Pause
                                                else Icons.Outlined.PlayArrow,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            viewModel.toggleEnabled()
                                            showActions = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Send test") },
                                    leadingIcon = { Icon(Icons.Outlined.Send, contentDescription = null) },
                                    enabled = isActive,
                                    onClick = {
                                        viewModel.sendTest()
                                        showActions = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        viewModel.duplicate()
                                        showActions = false
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        deleteConfirm = true
                                        showActions = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (channel != null && dirty) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { viewModel.discardDraft() }) {
                            Text("Discard")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.save() },
                            enabled = !state.isSaving,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Saving…")
                            } else {
                                Text("Save changes")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(vertical = 8.dp))
            }
            when {
                state.isLoading || merged == null -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                else -> EditorBody(
                    merged = merged,
                    members = state.members,
                    groups = state.groups,
                    isPending = isPending,
                    isReissuing = state.isReissuing,
                    reissuedUrl = state.reissuedActivationUrl,
                    onReissue = { viewModel.reissueActivation(merged.id) },
                    onDismissReissuedUrl = { viewModel.dismissReissuedUrl() },
                    onPatch = viewModel::updateDraft,
                    context = context,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (deleteConfirm && channel != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete channel?") },
            text = {
                Text(
                    "\"${channel.name}\" will be removed and stop delivering. " +
                        "Any link you've shared will become invalid."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(channel.id, onDeleted = onNavigateUp)
                        deleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Editor body ──────────────────────────────────────────────────────────────

@Composable
private fun EditorBody(
    merged: NotificationChannelRead,
    members: List<MemberRead>,
    groups: List<GroupRead>,
    isPending: Boolean,
    isReissuing: Boolean,
    reissuedUrl: String?,
    onReissue: () -> Unit,
    onDismissReissuedUrl: () -> Unit,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
    context: Context,
) {
    Spacer(Modifier.height(8.dp))
    HeaderRow(merged)
    Spacer(Modifier.height(16.dp))

    if (isPending) {
        PendingActivationPanel(
            reissuedUrl = reissuedUrl,
            isReissuing = isReissuing,
            onReissue = onReissue,
            onDismissReissuedUrl = onDismissReissuedUrl,
            context = context,
        )
    }

    NameCard(merged.name, onChange = { v -> onPatch { it.copy(name = v) } })
    Spacer(Modifier.height(12.dp))
    TriggersCard(merged, onPatch)
    Spacer(Modifier.height(12.dp))
    BaseSetCard(merged, onPatch)
    Spacer(Modifier.height(12.dp))
    GroupRulesCard(merged, groups, onPatch)
    Spacer(Modifier.height(12.dp))
    MemberRulesCard(merged, members, onPatch)
    Spacer(Modifier.height(12.dp))
    DeliveryCard(merged, onPatch)
}

@Composable
private fun HeaderRow(channel: NotificationChannelRead) {
    Text(channel.name, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        destinationLabel(channel.destinationType),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    StateBadge(channel.destinationState)
}

@Composable
private fun StateBadge(state: String) {
    val (text, color) = when (state.lowercase()) {
        "active" -> "Active" to MaterialTheme.colorScheme.primary
        "pending_registration" -> "Pending activation" to MaterialTheme.colorScheme.tertiary
        "disabled" -> "Disabled" to MaterialTheme.colorScheme.error
        else -> state to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

// ── Cards ────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun NameCard(name: String, onChange: (String) -> Unit) {
    SectionCard("Name") {
        OutlinedTextField(
            value = name,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TriggersCard(
    channel: NotificationChannelRead,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
) {
    SectionCard("Triggers") {
        ToggleRow(
            label = "Member starts fronting",
            checked = channel.triggerOnStart,
            onChange = { v -> onPatch { it.copy(triggerOnStart = v) } },
        )
        ToggleRow(
            label = "Member stops fronting",
            checked = channel.triggerOnStop,
            onChange = { v -> onPatch { it.copy(triggerOnStop = v) } },
        )
        ToggleRow(
            label = "Co-front composition changes",
            description = "Someone joins or leaves alongside a watched member.",
            checked = channel.triggerOnCofrontChange,
            onChange = { v -> onPatch { it.copy(triggerOnCofrontChange = v) } },
        )
        // Redaction governs how an excluded or private member fronting
        // alongside a visible one appears in the message. It applies to start
        // and stop notifications too, not only co-front ones, so it was wrong
        // to nest it under that trigger: a channel with just "starts fronting"
        // on could not reach the setting at all. Shown whenever this channel
        // can emit a name-bearing notification, i.e. some trigger is on and
        // the payload isn't already hiding names.
        val anyTrigger = channel.triggerOnStart ||
            channel.triggerOnStop ||
            channel.triggerOnCofrontChange
        if (anyTrigger && channel.payloadSensitivity == "full") {
            Spacer(Modifier.height(12.dp))
            Text(
                "Hidden co-fronters",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "When an excluded or private member is fronting alongside " +
                    "someone this channel can see, how should they appear? " +
                    "Applies to start, stop, and co-front notifications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            EnumDropdown(
                label = "Hidden co-fronters",
                value = channel.cofrontRedaction,
                options = listOf(
                    "count" to "Count only (\"and 1 other\")",
                    "someone" to "Vaguer (\"someone\")",
                    "suppress" to "Send nothing if anyone is hidden",
                ),
                onChange = { v -> onPatch { it.copy(cofrontRedaction = v) } },
            )
        }
    }
}

@Composable
private fun BaseSetCard(
    channel: NotificationChannelRead,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
) {
    SectionCard("Base set") {
        RadioRow(
            label = "All members",
            selected = channel.baseAllMembers,
            onSelect = { onPatch { it.copy(baseAllMembers = true) } },
        )
        RadioRow(
            label = "No-one (use group / member rules to include)",
            selected = !channel.baseAllMembers,
            onSelect = {
                // Clearing base also nulls include-private; private only
                // makes sense alongside an "all members" base.
                onPatch { it.copy(baseAllMembers = false, baseIncludePrivate = false) }
            },
        )
        if (channel.baseAllMembers) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = channel.baseIncludePrivate,
                    onCheckedChange = { v -> onPatch { it.copy(baseIncludePrivate = v) } },
                )
                Spacer(Modifier.width(8.dp))
                Text("Also include private members")
            }
        }
    }
}

@Composable
private fun GroupRulesCard(
    channel: NotificationChannelRead,
    groups: List<GroupRead>,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
) {
    SectionCard("Group rules") {
        val usedIds = channel.groupRules.map { it.groupId }.toSet()
        val available = groups.filter { it.id !in usedIds }
        if (channel.groupRules.isEmpty()) {
            Text(
                "No group rules yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        channel.groupRules.forEach { rule ->
            val name = groups.firstOrNull { it.id == rule.groupId }?.name ?: "(deleted group)"
            GroupRuleRow(
                name = name,
                rule = rule,
                onChange = { updated ->
                    onPatch {
                        it.copy(
                            groupRulesSet = true,
                            groupRules = channel.groupRules.map { r ->
                                if (r.groupId == rule.groupId) updated else r
                            },
                        )
                    }
                },
                onRemove = {
                    onPatch {
                        it.copy(
                            groupRulesSet = true,
                            groupRules = channel.groupRules.filterNot { r -> r.groupId == rule.groupId },
                        )
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (available.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            AddPickerDropdown(
                label = "Add group",
                placeholder = "Choose a group…",
                options = available.map { it.id to it.name },
                onPick = { groupId ->
                    onPatch {
                        it.copy(
                            groupRulesSet = true,
                            groupRules = channel.groupRules + GroupRuleSpec(
                                groupId = groupId,
                                rule = "include",
                                includePrivate = "inherit",
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun GroupRuleRow(
    name: String,
    rule: GroupRuleSpec,
    onChange: (GroupRuleSpec) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove rule",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            EnumDropdown(
                label = "Rule",
                value = rule.rule,
                options = listOf("include" to "Include", "exclude" to "Exclude"),
                onChange = { v -> onChange(rule.copy(rule = v)) },
            )
            if (rule.rule == "include") {
                Spacer(Modifier.height(6.dp))
                EnumDropdown(
                    label = "Privacy",
                    value = rule.includePrivate,
                    options = listOf(
                        "inherit" to "Inherit Layer 1",
                        "yes" to "Include private",
                        "no" to "Exclude private",
                    ),
                    onChange = { v -> onChange(rule.copy(includePrivate = v)) },
                )
            }
        }
    }
}

@Composable
private fun MemberRulesCard(
    channel: NotificationChannelRead,
    members: List<MemberRead>,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
) {
    SectionCard("Member rules (overrides)") {
        val usedIds = channel.memberRules.map { it.memberId }.toSet()
        val available = members.filter { it.id !in usedIds }
        if (channel.memberRules.isEmpty()) {
            Text(
                "No member overrides.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        channel.memberRules.forEach { rule ->
            val name = members.firstOrNull { it.id == rule.memberId }?.displayNameOrName
                ?: "(deleted member)"
            MemberRuleRow(
                name = name,
                rule = rule,
                onChange = { updated ->
                    onPatch {
                        it.copy(
                            memberRulesSet = true,
                            memberRules = channel.memberRules.map { r ->
                                if (r.memberId == rule.memberId) updated else r
                            },
                        )
                    }
                },
                onRemove = {
                    onPatch {
                        it.copy(
                            memberRulesSet = true,
                            memberRules = channel.memberRules.filterNot { r -> r.memberId == rule.memberId },
                        )
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (available.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            AddPickerDropdown(
                label = "Add member",
                placeholder = "Choose a member…",
                options = available.map { it.id to it.displayNameOrName },
                onPick = { memberId ->
                    onPatch {
                        it.copy(
                            memberRulesSet = true,
                            memberRules = channel.memberRules + MemberRuleSpec(
                                memberId = memberId,
                                rule = "include",
                            ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun MemberRuleRow(
    name: String,
    rule: MemberRuleSpec,
    onChange: (MemberRuleSpec) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                EnumDropdown(
                    label = "Rule",
                    value = rule.rule,
                    options = listOf("include" to "Include", "exclude" to "Exclude"),
                    onChange = { v -> onChange(rule.copy(rule = v)) },
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove rule",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeliveryCard(
    channel: NotificationChannelRead,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
) {
    SectionCard("Delivery") {
        EnumDropdown(
            label = "Payload sensitivity",
            value = channel.payloadSensitivity,
            options = listOf(
                "full" to "Full — include member names",
                "minimal" to "Minimal — \"someone started fronting\"",
                "bare" to "Bare — \"a front changed\"",
            ),
            onChange = { v -> onPatch { it.copy(payloadSensitivity = v) } },
        )
        Spacer(Modifier.height(8.dp))
        IntField(
            label = "Debounce (seconds)",
            value = channel.debounceSeconds,
            min = 0,
            max = 86400,
            helper = "Minimum gap between deliveries on this channel. Changes that " +
                "arrive inside the gap are held and sent afterward, not dropped.",
            onChange = { v -> onPatch { it.copy(debounceSeconds = v) } },
        )
        Spacer(Modifier.height(8.dp))
        IntField(
            label = "Aggregation window (seconds)",
            value = channel.aggregationWindowSeconds,
            min = 0,
            max = 86400,
            helper = "0 = off (each change is delivered on its own). Above 0, front " +
                "changes in this many seconds are batched and sent as one " +
                "notification when the window closes, so a quick series (like a " +
                "co-front swap) arrives as a single message. Adds up to this much " +
                "delay.",
            onChange = { v -> onPatch { it.copy(aggregationWindowSeconds = v) } },
        )
        Spacer(Modifier.height(12.dp))
        QuietHoursControls(channel.quietHours, onPatch)
    }
}

@Composable
private fun QuietHoursControls(
    quiet: QuietHoursSpec?,
    onPatch: ((ChannelEditDraft) -> ChannelEditDraft) -> Unit,
) {
    val enabled = quiet != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = enabled,
            onCheckedChange = { v ->
                onPatch {
                    it.copy(
                        quietHoursSet = true,
                        // Conservative defaults when enabling — caller
                        // can adjust before saving. Disable clears via
                        // an explicit null which the backend accepts
                        // for this field specifically.
                        quietHours = if (v) {
                            quiet ?: QuietHoursSpec(start = "22:00", end = "07:00", tz = "UTC")
                        } else {
                            null
                        },
                    )
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Text("Quiet hours", style = MaterialTheme.typography.bodyMedium)
    }
    if (quiet != null) {
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(start = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = quiet.start,
                onValueChange = { v ->
                    onPatch {
                        it.copy(quietHoursSet = true, quietHours = quiet.copy(start = v))
                    }
                },
                label = { Text("Start (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quiet.end,
                onValueChange = { v ->
                    onPatch {
                        it.copy(quietHoursSet = true, quietHours = quiet.copy(end = v))
                    }
                },
                label = { Text("End (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quiet.tz,
                onValueChange = { v ->
                    onPatch {
                        it.copy(quietHoursSet = true, quietHours = quiet.copy(tz = v))
                    }
                },
                label = { Text("Timezone (IANA, e.g. Europe/Berlin)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Events landing inside the window are deferred to the end time. " +
                    "DST transitions in the chosen timezone are honoured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Pending activation ───────────────────────────────────────────────────────

@Composable
private fun PendingActivationPanel(
    reissuedUrl: String?,
    isReissuing: Boolean,
    onReissue: () -> Unit,
    onDismissReissuedUrl: () -> Unit,
    context: Context,
) {
    SectionCard("Activation link") {
        Text(
            "The recipient opens this link on their device to activate. The link " +
                "is one-time and time-limited. You can re-issue a fresh one any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (reissuedUrl != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    reissuedUrl,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clip.setPrimaryClip(ClipData.newPlainText("Sheaf invite", reissuedUrl))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reissuedUrl)
                            putExtra(Intent.EXTRA_SUBJECT, "Subscribe to my front updates")
                        }
                        context.startActivity(Intent.createChooser(send, "Share invite link"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismissReissuedUrl,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Hide link") }
        } else {
            Button(
                onClick = onReissue,
                enabled = !isReissuing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isReissuing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Get a fresh activation link")
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ── Reusable controls ────────────────────────────────────────────────────────

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (key, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = {
                        onChange(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPickerDropdown(
    label: String,
    placeholder: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (id, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = {
                        onPick(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    helper: String?,
    onChange: (Int) -> Unit,
) {
    // Hold the literal text so transient empty / non-numeric input is
    // possible while the user retypes; commit only valid clamped ints.
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.toIntOrNull()?.let { parsed ->
                val clamped = parsed.coerceIn(min, max)
                if (clamped != value) onChange(clamped)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Number,
        ),
        supportingText = if (helper != null) {
            { Text(helper) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Labels (shared with list screen) ─────────────────────────────────────────

private fun destinationLabel(type: String): String = when (type.lowercase()) {
    "web_push" -> "Web push (browser)"
    "mobile_push" -> "Mobile push"
    "fcm" -> "Mobile push"
    "apns_dev", "apns_prod" -> "Mobile push"
    "email" -> "Email"
    "webhook" -> "Webhook"
    "ntfy" -> "ntfy"
    "pushover" -> "Pushover"
    "discord" -> "Discord"
    else -> type
}
