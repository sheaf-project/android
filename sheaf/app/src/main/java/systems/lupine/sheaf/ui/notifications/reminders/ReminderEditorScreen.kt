package systems.lupine.sheaf.ui.notifications.reminders

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorScreen(
    reminderId: String?,
    onNavigateUp: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReminderEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(reminderId) { viewModel.load(reminderId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text(if (reminderId == null) "New reminder" else "Edit reminder") },
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
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            EditorBasics(state, viewModel)
            Spacer(Modifier.height(20.dp))
            TriggerTypePicker(state, viewModel)
            Spacer(Modifier.height(20.dp))
            when (state.triggerType) {
                "automated" -> AutomatedFields(state, viewModel)
                "repeated" -> RepeatedFields(state, viewModel)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { viewModel.submit() },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (reminderId == null) "Create reminder" else "Save changes")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBasics(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { v -> vm.update { copy(name = v) } },
        label = { Text("Name (for your reference)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.title,
        onValueChange = { v -> vm.update { copy(title = v) } },
        label = { Text("Notification title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.body,
        onValueChange = { v -> vm.update { copy(body = v) } },
        label = { Text("Notification body (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    ChannelDropdown(state, vm)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = state.enabled,
            onCheckedChange = { v -> vm.update { copy(enabled = v) } },
        )
        Spacer(Modifier.width(12.dp))
        Text("Enabled")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDropdown(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.channels.firstOrNull { it.id == state.channelId }?.name
        ?: "Pick a channel"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedName,
            onValueChange = {},
            label = { Text("Deliver via") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.channels.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    onClick = {
                        vm.update { copy(channelId = c.id) }
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TriggerTypePicker(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    Text("When should this fire?", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.selectableGroup()) {
        TriggerTypeOption(
            value = "automated",
            title = "When a member fronts or stops",
            subtitle = "Fires after a delay following the event",
            selected = state.triggerType == "automated",
            onSelect = { vm.update { copy(triggerType = "automated") } },
        )
        TriggerTypeOption(
            value = "repeated",
            title = "On a schedule",
            subtitle = "Daily, weekly, or monthly at a fixed time",
            selected = state.triggerType == "repeated",
            onSelect = { vm.update { copy(triggerType = "repeated") } },
        )
    }
}

@Composable
private fun TriggerTypeOption(
    value: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomatedFields(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    Text("Trigger", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    MemberDropdown(state, vm)
    Spacer(Modifier.height(12.dp))
    Text("Event", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EventChip("start", "Starts fronting", state.triggerEvent == "start") {
            vm.update { copy(triggerEvent = "start") }
        }
        EventChip("stop", "Stops fronting", state.triggerEvent == "stop") {
            vm.update { copy(triggerEvent = "stop") }
        }
        EventChip("any", "Either", state.triggerEvent == "any") {
            vm.update { copy(triggerEvent = "any") }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Delay after event", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DelayChip(0, "Now", state.delaySeconds, vm)
        DelayChip(5 * 60, "5 min", state.delaySeconds, vm)
        DelayChip(30 * 60, "30 min", state.delaySeconds, vm)
        DelayChip(60 * 60, "1 hr", state.delaySeconds, vm)
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DelayChip(4 * 60 * 60, "4 hr", state.delaySeconds, vm)
        DelayChip(12 * 60 * 60, "12 hr", state.delaySeconds, vm)
        DelayChip(24 * 60 * 60, "1 day", state.delaySeconds, vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDropdown(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.members.firstOrNull { it.id == state.triggerMemberId }?.name
        ?: "Pick a member"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedName,
            onValueChange = {},
            label = { Text("Member") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.members.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name) },
                    onClick = {
                        vm.update { copy(triggerMemberId = m.id) }
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventChip(value: String, label: String, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(selected = selected, onClick = onSelect, label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelayChip(seconds: Int, label: String, current: Int, vm: ReminderEditorViewModel) {
    FilterChip(
        selected = current == seconds,
        onClick = { vm.update { copy(delaySeconds = seconds) } },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatedFields(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    Text("Frequency", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FrequencyChip("daily", "Daily", state.scheduleKind == "daily") {
            vm.update { copy(scheduleKind = "daily") }
        }
        FrequencyChip("weekly", "Weekly", state.scheduleKind == "weekly") {
            vm.update { copy(scheduleKind = "weekly") }
        }
        FrequencyChip("monthly", "Monthly", state.scheduleKind == "monthly") {
            vm.update { copy(scheduleKind = "monthly") }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.scheduleTime,
        onValueChange = { v -> vm.update { copy(scheduleTime = v) } },
        label = { Text("Time (HH:MM)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    when (state.scheduleKind) {
        "weekly" -> WeekdayPicker(state, vm)
        "monthly" -> OutlinedTextField(
            value = state.scheduleDom.toString(),
            onValueChange = { v ->
                val n = v.toIntOrNull()?.coerceIn(1, 31) ?: return@OutlinedTextField
                vm.update { copy(scheduleDom = n) }
            },
            label = { Text("Day of month (1-31)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        else -> {}
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Timezone: ${state.scheduleTz}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencyChip(value: String, label: String, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(selected = selected, onClick = onSelect, label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekdayPicker(state: ReminderEditorState, vm: ReminderEditorViewModel) {
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { idx, label ->
            val bit = 1 shl idx
            val on = (state.scheduleDowMask and bit) != 0
            FilterChip(
                selected = on,
                onClick = {
                    vm.update {
                        copy(scheduleDowMask = scheduleDowMask xor bit)
                    }
                },
                label = { Text(label) },
            )
        }
    }
}
