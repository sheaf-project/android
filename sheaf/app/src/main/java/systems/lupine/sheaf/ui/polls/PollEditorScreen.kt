package systems.lupine.sheaf.ui.polls

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollEditorScreen(
    onNavigateUp: () -> Unit,
    onSaved: () -> Unit,
    viewModel: PollEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("New poll") },
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.question,
                onValueChange = { v -> viewModel.update { copy(question = v) } },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.description,
                onValueChange = { v -> viewModel.update { copy(description = v) } },
                label = { Text("Description (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader("Vote style")
            Column(Modifier.selectableGroup()) {
                EditorRadioRow(
                    title = "Pick one",
                    subtitle = "Each voter picks a single option",
                    selected = state.kind == "single_choice",
                    onSelect = { viewModel.update { copy(kind = "single_choice") } },
                )
                EditorRadioRow(
                    title = "Pick any",
                    subtitle = "Each voter picks zero or more options",
                    selected = state.kind == "multi_choice",
                    onSelect = { viewModel.update { copy(kind = "multi_choice") } },
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Results")
            Column(Modifier.selectableGroup()) {
                EditorRadioRow(
                    title = "Live",
                    subtitle = "Tally is visible as votes come in",
                    selected = state.resultsVisibility == "live",
                    onSelect = { viewModel.update { copy(resultsVisibility = "live") } },
                )
                EditorRadioRow(
                    title = "Hidden until close",
                    subtitle = "Results revealed once the poll closes",
                    selected = state.resultsVisibility == "end_only",
                    onSelect = { viewModel.update { copy(resultsVisibility = "end_only") } },
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Closes")
            CloseDateField(state.closesAtIso) { iso ->
                viewModel.update { copy(closesAtIso = iso) }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Who can vote")
            // Voting restriction. Surfaced as a switch row so it's
            // legible at a glance — radio rows above carry one
            // selected-at-a-time choices, this one's a toggle on top
            // of those.
            ListItem(
                headlineContent = {
                    Text(
                        "Only currently-fronting members",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        if (state.restrictVotingToFronters)
                            "Members can only cast or withdraw a vote while they're in the active front."
                        else
                            "Any system member can vote regardless of whether they're fronting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.restrictVotingToFronters,
                        onCheckedChange = { v ->
                            viewModel.update { copy(restrictVotingToFronters = v) }
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 0.dp),
            )

            Spacer(Modifier.height(20.dp))
            SectionHeader("Options")
            state.options.forEachIndexed { index, opt ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    OutlinedTextField(
                        value = opt,
                        onValueChange = { v -> viewModel.setOption(index, v) },
                        label = { Text("Option ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.options.size > 2) {
                        IconButton(onClick = { viewModel.removeOption(index) }) {
                            Icon(
                                Icons.Outlined.RemoveCircleOutline,
                                contentDescription = "Remove option",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (state.options.size < 20) {
                TextButton(onClick = { viewModel.addOption() }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add option")
                }
            }

            Spacer(Modifier.height(24.dp))
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
                    Text("Create poll")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun EditorRadioRow(
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

@Composable
private fun CloseDateField(currentIso: String, onChange: (String) -> Unit) {
    // Simple ISO-display read-only field with +1/+7/+30 day quick chips.
    // A proper date+time picker is a richer follow-up; for now the default
    // is "now + 7 days" and the chips let users coarsely retarget.
    val current = remember(currentIso) {
        runCatching {
            java.time.OffsetDateTime.parse(currentIso)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrDefault(currentIso)
    }
    OutlinedTextField(
        value = current,
        onValueChange = {},
        readOnly = true,
        label = { Text("Closes at") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickDateChip("+1 day", 1, onChange)
        QuickDateChip("+1 week", 7, onChange)
        QuickDateChip("+1 month", 30, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickDateChip(label: String, days: Long, onChange: (String) -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = {
            val target = java.time.Instant.now()
                .plusSeconds(days * 24 * 3600)
                .atZone(ZoneId.systemDefault())
            onChange(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(target))
        },
        label = { Text(label) },
    )
}
