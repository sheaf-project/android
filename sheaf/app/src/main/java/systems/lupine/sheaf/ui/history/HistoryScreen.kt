@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ")

package systems.lupine.sheaf.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var frontToDelete by remember { mutableStateOf<FrontRead?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    // Infinite scroll: load more when near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 5 && !state.isLoadingMore && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    frontToDelete?.let { front ->
        val members = front.memberIds.mapNotNull { state.members[it] }
        val names = if (members.isEmpty()) "this front entry" else members.joinToString(", ") { it.displayNameOrName }
        AlertDialog(
            onDismissRequest = { frontToDelete = null },
            title = { Text("Delete front entry?") },
            text = { Text("This will permanently delete the front entry for $names. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteFront(front.id); frontToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { frontToDelete = null }) { Text("Cancel") }
            },
        )
    }
    if (state.deleteError != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Delete failed") },
            text = { Text(state.deleteError!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.loadInitial() }) { Text("OK") }
            },
        )
    }

    if (showAddSheet) {
        AddFrontEntrySheet(
            allMembers = state.allMembers,
            onDismiss = { showAddSheet = false },
            onConfirm = { memberIds, startedAt, endedAt ->
                viewModel.addFrontEntry(memberIds, startedAt, endedAt)
                showAddSheet = false
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Front History") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add front entry")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(state.error!!)
                Button(onClick = { viewModel.loadInitial() }) { Text("Retry") }
            }
            state.fronts.isEmpty() -> EmptyState(
                icon = Icons.Default.History,
                title = "No front history",
                subtitle = "Past and present fronts will appear here.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.fronts) { front ->
                    val frontMembers = front.memberIds.mapNotNull { state.members[it] }
                    FrontHistoryCard(
                        front = front,
                        members = frontMembers,
                        onLongClick = { frontToDelete = front },
                    )
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFrontEntrySheet(
    allMembers: List<MemberRead>,
    onDismiss: () -> Unit,
    onConfirm: (memberIds: List<String>, startedAt: String, endedAt: String?) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var stillOngoing by remember { mutableStateOf(false) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var endTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        endDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add Front Entry", style = MaterialTheme.typography.titleLarge)

            // Member selection
            Text("Members", style = MaterialTheme.typography.labelLarge)
            if (allMembers.isEmpty()) {
                Text("No members found", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                allMembers.forEach { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = member.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + member.id else selectedIds - member.id
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        MemberAvatar(member = member, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(member.displayNameOrName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            HorizontalDivider()

            // Start time
            Text("Started", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(startDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                }
                OutlinedButton(
                    onClick = { /* time picker not shown inline; user types */ },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(startTime.format(DateTimeFormatter.ofPattern("h:mm a")))
                }
            }
            // Simple time input row for start
            TimeInputRow(time = startTime, onTimeChange = { startTime = it })

            HorizontalDivider()

            // End time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Still Ongoing", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = stillOngoing, onCheckedChange = { stillOngoing = it })
            }

            if (!stillOngoing) {
                Text("Ended", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(endDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                    }
                }
                TimeInputRow(time = endTime, onTimeChange = { endTime = it })
            }

            // Confirm button
            Button(
                onClick = {
                    val zone = ZoneId.systemDefault()
                    val startInstant = LocalDateTime.of(startDate, startTime).atZone(zone).toInstant()
                    val endInstant = if (stillOngoing) null
                    else LocalDateTime.of(endDate, endTime).atZone(zone).toInstant()
                    onConfirm(
                        selectedIds.toList(),
                        startInstant.toString(),
                        endInstant?.toString(),
                    )
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add Entry") }
        }
    }
}

@Composable
private fun TimeInputRow(time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    var hourText by remember(time) { mutableStateOf(time.format(DateTimeFormatter.ofPattern("h"))) }
    var minuteText by remember(time) { mutableStateOf(time.format(DateTimeFormatter.ofPattern("mm"))) }
    var isPm by remember(time) { mutableStateOf(time.hour >= 12) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = hourText,
            onValueChange = { v ->
                if (v.length <= 2 && v.all { it.isDigit() }) {
                    hourText = v
                    val h = v.toIntOrNull() ?: return@OutlinedTextField
                    if (h in 1..12) {
                        val hour24 = if (isPm) { if (h == 12) 12 else h + 12 } else { if (h == 12) 0 else h }
                        onTimeChange(time.withHour(hour24).withMinute(minuteText.toIntOrNull() ?: time.minute))
                    }
                }
            },
            label = { Text("Hour") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Text(":", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = minuteText,
            onValueChange = { v ->
                if (v.length <= 2 && v.all { it.isDigit() }) {
                    minuteText = v
                    val m = v.toIntOrNull() ?: return@OutlinedTextField
                    if (m in 0..59) onTimeChange(time.withMinute(m))
                }
            },
            label = { Text("Min") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            SegmentedButton(
                selected = !isPm,
                onClick = {
                    isPm = false
                    val h = if (time.hour >= 12) time.hour - 12 else time.hour
                    onTimeChange(time.withHour(if (h == 0) 0 else h))
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("AM") }
            SegmentedButton(
                selected = isPm,
                onClick = {
                    isPm = true
                    val h = if (time.hour < 12) time.hour + 12 else time.hour
                    onTimeChange(time.withHour(h))
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("PM") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrontHistoryCard(front: FrontRead, members: List<MemberRead>, onLongClick: () -> Unit) {
    val isActive = front.endedAt == null
    ElevatedCard(modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = onLongClick) {}) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Stacked avatars
                members.take(4).forEachIndexed { index, member ->
                    Box(Modifier.offset(x = (-8 * index).dp)) {
                        MemberAvatar(member = member, size = 36.dp)
                    }
                }
                if (members.size > 4) {
                    Spacer(Modifier.width(4.dp))
                    Text("+${members.size - 4}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (isActive) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Text(
                        formatDuration(front.startedAt, front.endedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (members.isEmpty()) "Unknown members"
                else members.joinToString(", ") { it.displayNameOrName },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                buildString {
                    append(formatTimestamp(front.startedAt))
                    if (front.endedAt != null) {
                        append("  →  ")
                        append(formatTimestamp(front.endedAt))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

private fun formatTimestamp(iso: String): String =
    runCatching { timeFormatter.format(Instant.parse(iso)) }.getOrDefault(iso)

private fun formatDuration(startIso: String, endIso: String?): String =
    runCatching {
        val start = Instant.parse(startIso)
        val end = if (endIso != null) Instant.parse(endIso) else Instant.now()
        val d = Duration.between(start, end)
        when {
            d.toMinutes() < 1  -> "<1m"
            d.toMinutes() < 60 -> "${d.toMinutes()}m"
            d.toHours()   < 24 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
            else               -> "${d.toDays()}d ${d.toHours() % 24}h"
        }
    }.getOrDefault("—")
