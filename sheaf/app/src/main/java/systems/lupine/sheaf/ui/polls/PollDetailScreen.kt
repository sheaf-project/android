package systems.lupine.sheaf.ui.polls

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.PollOptionRead
import systems.lupine.sheaf.data.model.PollRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SectionHeader
import systems.lupine.sheaf.ui.components.SheafTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: PollDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) {
        // We refresh the poll after vote, so 'saved' just toggles. No nav.
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Poll") },
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
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.poll != null -> Content(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(state: PollDetailUiState, vm: PollDetailViewModel) {
    val poll = state.poll!!
    Spacer(Modifier.height(8.dp))
    Text(poll.question, style = MaterialTheme.typography.headlineSmall)
    if (!poll.description.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            poll.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        pollMeta(poll),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    if (!poll.isClosed) {
        VotingPanel(state, vm)
    } else {
        ResultsPanel(poll)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VotingPanel(state: PollDetailUiState, vm: PollDetailViewModel) {
    val poll = state.poll!!
    SectionHeader("Vote")
    if (poll.restrictVotingToFronters) {
        // Always surface the restriction at the top of the voting
        // panel, even when the picked voter is currently in front —
        // so the user knows the rule and isn't surprised when an
        // out-of-front member can't vote later.
        AssistChip(
            onClick = {},
            label = { Text("Only fronting members may vote") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.People,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
    }
    MemberDropdown(state, vm)
    if (state.voterBlockedByRestriction) {
        // Voter-specific explanation. Visible only when the picked
        // member isn't in the current front and the poll restricts.
        Spacer(Modifier.height(4.dp))
        Text(
            "This member isn't currently fronting — voting is restricted to fronters on this poll.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(12.dp))
    val isSingle = poll.kind == "single_choice"
    if (isSingle) {
        Column(Modifier.selectableGroup()) {
            poll.options.forEach { opt ->
                SingleChoiceOption(
                    option = opt,
                    selected = opt.id in state.selectedOptionIds,
                    onSelect = { vm.toggleOption(opt.id) },
                )
            }
        }
    } else {
        poll.options.forEach { opt ->
            MultiChoiceOption(
                option = opt,
                selected = opt.id in state.selectedOptionIds,
                onToggle = { vm.toggleOption(opt.id) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { vm.submitVote() },
        enabled = !state.isVoting
            && state.selectedOptionIds.isNotEmpty()
            && state.votedAsMemberId != null
            && !state.voterBlockedByRestriction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isVoting) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Submit vote")
        }
    }
    // Withdraw available when this member already has a vote on record.
    val hasExistingVote = poll.votes?.any { it.votedAsMemberId == state.votedAsMemberId } == true
    if (hasExistingVote) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.withdrawVote() },
            enabled = !state.isVoting,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Withdraw vote") }
    }

    // Owner-side preview of current tally when live, hidden otherwise.
    if (poll.tally != null) {
        Spacer(Modifier.height(24.dp))
        SectionHeader("Current tally")
        ResultsContent(poll)
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun ResultsPanel(poll: PollRead) {
    SectionHeader("Results")
    if (poll.tally == null) {
        Text(
            "Results are hidden until the poll closes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        ResultsContent(poll)
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun ResultsContent(poll: PollRead) {
    val total = poll.tally?.sumOf { it.count } ?: 0
    poll.options.forEach { opt ->
        val count = poll.tally?.firstOrNull { it.optionId == opt.id }?.count ?: 0
        val frac = if (total > 0) count.toFloat() / total else 0f
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(opt.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "$total ${if (total == 1) "vote" else "votes"} total",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDropdown(state: PollDetailUiState, vm: PollDetailViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val name = state.members.firstOrNull { it.id == state.votedAsMemberId }?.displayNameOrName
        ?: "Pick a member"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = name,
            onValueChange = {},
            label = { Text("Voting as") },
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
                    text = { Text(m.displayNameOrName) },
                    onClick = {
                        vm.setVotedAsMember(m.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceOption(
    option: PollOptionRead,
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
        Text(option.text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MultiChoiceOption(
    option: PollOptionRead,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onToggle, role = Role.Checkbox)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(option.text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun pollMeta(poll: PollRead): String {
    val kind = when (poll.kind) {
        "single_choice" -> "Pick one"
        "multi_choice" -> "Pick any"
        else -> poll.kind
    }
    val state = if (poll.isClosed) "Closed" else "Closes ${poll.closesAt.take(16).replace('T', ' ')}"
    return "$kind · $state · ${poll.totalVotes} votes"
}
