@file:Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package systems.lupine.sheaf.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.time.format.TextStyle as JTimeTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var frontToDelete by remember { mutableStateOf<FrontRead?>(null) }
    var frontToEdit by remember { mutableStateOf<FrontRead?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadInitial()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
        FrontEntrySheet(
            allMembers = state.allMembers,
            initialFront = null,
            onDismiss = { showAddSheet = false },
            onConfirm = { memberIds, startedAt, endedAt ->
                viewModel.addFrontEntry(memberIds, startedAt, endedAt)
                showAddSheet = false
            },
        )
    }

    frontToEdit?.let { front ->
        FrontEntrySheet(
            allMembers = state.allMembers,
            initialFront = front,
            onDismiss = { frontToEdit = null },
            onConfirm = { memberIds, startedAt, endedAt ->
                viewModel.updateFrontEntry(front.id, memberIds, startedAt, endedAt)
                frontToEdit = null
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SheafLargeFlexibleTopAppBar(
                title = { Text("History") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add entry")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.fronts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ErrorBanner(state.error!!)
                    Button(onClick = { viewModel.loadInitial() }) { Text("Retry") }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "timeline") {
                        TimelineSection(
                            fronts = state.fronts,
                            members = state.members,
                        )
                    }
                    if (state.fronts.isEmpty() && !state.isLoading) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Default.History,
                                title = "No front history",
                                subtitle = "Tap + to add an entry.",
                            )
                        }
                    } else {
                        item(key = "log_header") {
                            Text(
                                "LOG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        items(state.fronts, key = { it.id }) { front ->
                            val frontMembers = front.memberIds.mapNotNull { state.members[it] }
                            FrontHistoryCard(
                                front = front,
                                members = frontMembers,
                                onClick = { frontToEdit = front },
                                onLongClick = { frontToDelete = front },
                            )
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Timeline section ──────────────────────────────────────────────────────────

@Composable
private fun TimelineSection(
    fronts: List<FrontRead>,
    members: Map<String, MemberRead>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var periodDays by rememberSaveable { mutableIntStateOf(7) }

    val now = remember { Instant.now() }
    val periodStartMs = remember(periodDays) {
        now.minus(periodDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
    }
    val nowMs = remember { now.toEpochMilli() }
    val totalMs = periodDays * 86_400_000L

    val periodFronts = remember(fronts, periodStartMs, nowMs) {
        fronts.filter { front ->
            val s = runCatching { Instant.parse(front.startedAt).toEpochMilli() }.getOrNull() ?: return@filter false
            val e = runCatching { front.endedAt?.let { Instant.parse(it).toEpochMilli() } ?: nowMs }.getOrNull() ?: return@filter false
            s < nowMs && e > periodStartMs
        }
    }

    val activeMembers = remember(periodFronts, members) {
        val ids = periodFronts.flatMap { it.memberIds }.toSet()
        members.values.filter { it.id in ids }.sortedBy { it.displayNameOrName }
    }

    // X-axis column labels: (fraction 0..1, text)
    val labels: List<Pair<Float, String>> = remember(periodDays, periodStartMs) {
        val step = when (periodDays) { 7 -> 1; 14 -> 2; 30 -> 5; else -> 15 }
        (0 until periodDays step step).map { day ->
            val frac = day.toFloat() / periodDays
            val zdt = Instant.ofEpochMilli(periodStartMs + day * 86_400_000L).atZone(ZoneId.systemDefault())
            val label = if (periodDays <= 14) {
                zdt.dayOfWeek.getDisplayName(JTimeTextStyle.SHORT, Locale.getDefault())
            } else {
                zdt.format(DateTimeFormatter.ofPattern("M/d"))
            }
            frac to label
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Timeline", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                HorizontalDivider()

                // Period selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when (periodDays) {
                            7 -> "Last 7 Days"; 14 -> "Last 14 Days"; 30 -> "Last 30 Days"; else -> "Last 90 Days"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    SingleChoiceSegmentedButtonRow {
                        listOf(7 to "7D", 14 to "14D", 30 to "30D", 90 to "90D").forEachIndexed { index, (days, label) ->
                            SegmentedButton(
                                selected = periodDays == days,
                                onClick = { periodDays = days },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

                if (activeMembers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No fronting data in this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    // Member legend
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        activeMembers.forEach { member ->
                            val dotColor = member.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.primary
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Canvas(modifier = Modifier.size(8.dp)) { drawCircle(dotColor) }
                                Text(member.displayNameOrName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Grid
                    TimelineGrid(
                        members = activeMembers,
                        periodFronts = periodFronts,
                        labels = labels,
                        periodStartMs = periodStartMs,
                        totalMs = totalMs,
                        nowMs = nowMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineGrid(
    members: List<MemberRead>,
    periodFronts: List<FrontRead>,
    labels: List<Pair<Float, String>>,
    periodStartMs: Long,
    totalMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val labelColumnWidth = 32.dp
    val rowHeight = 22.dp
    val numCols = labels.size

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridLineColor = onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = onSurfaceVariant.copy(alpha = 0.6f)
    val primaryColor = MaterialTheme.colorScheme.primary

    // Pre-compute per-member colors in composable scope (can't access MaterialTheme in DrawScope)
    val memberColors = members.map { member ->
        member.color?.let { parseColor(it) } ?: primaryColor
    }

    // Pre-compute per-member bar data
    val memberBars: List<List<Pair<Float, Float>>> = remember(members, periodFronts, periodStartMs, totalMs, nowMs) {
        members.map { member ->
            periodFronts
                .filter { member.id in it.memberIds }
                .mapNotNull { front ->
                    val s = runCatching { Instant.parse(front.startedAt).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
                    val e = runCatching { front.endedAt?.let { Instant.parse(it).toEpochMilli() } ?: nowMs }.getOrNull() ?: return@mapNotNull null
                    val startFrac = ((s - periodStartMs).toFloat() / totalMs).coerceIn(0f, 1f)
                    val endFrac = ((e - periodStartMs).toFloat() / totalMs).coerceIn(0f, 1f)
                    startFrac to endFrac
                }
        }
    }

    Column(modifier = modifier) {
        // X-axis label row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Spacer(Modifier.width(labelColumnWidth))
            labels.forEach { (_, text) ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Member rows
        members.forEachIndexed { index, member ->
            val bars = memberBars[index]
            val memberColor = memberColors[index]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    member.initials,
                    style = MaterialTheme.typography.labelSmall,
                    color = memberColor,
                    modifier = Modifier.width(labelColumnWidth),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val barH = size.height * 0.55f
                    val barTop = (size.height - barH) / 2f
                    val corner = CornerRadius(2.dp.toPx())

                    // Vertical grid lines
                    repeat(numCols - 1) { i ->
                        val x = (i + 1).toFloat() / numCols * size.width
                        drawLine(
                            color = gridLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f,
                        )
                    }

                    // Front bars
                    bars.forEach { (startFrac, endFrac) ->
                        val left = startFrac * size.width
                        val right = endFrac * size.width
                        val w = right - left
                        if (w >= 1.5f) {
                            drawRoundRect(
                                color = memberColor,
                                topLeft = Offset(left, barTop),
                                size = Size(w, barH),
                                cornerRadius = corner,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Front history card ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrontHistoryCard(
    front: FrontRead,
    members: List<MemberRead>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isActive = front.endedAt == null
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                members.take(4).forEachIndexed { index, member ->
                    Box(Modifier.offset(x = (-8 * index).dp)) {
                        MemberAvatar(member = member, size = 36.dp)
                    }
                }
                if (members.size > 4) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "+${members.size - 4}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

// ── Front entry sheet (add / edit) ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrontEntrySheet(
    allMembers: List<MemberRead>,
    initialFront: FrontRead?,
    onDismiss: () -> Unit,
    onConfirm: (memberIds: List<String>, startedAt: String, endedAt: String?) -> Unit,
) {
    val isEditing = initialFront != null
    val initialStart = remember(initialFront) {
        initialFront?.startedAt?.let {
            runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull()
        }
    }
    val initialEnd = remember(initialFront) {
        initialFront?.endedAt?.let {
            runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull()
        }
    }
    var selectedIds by remember { mutableStateOf(initialFront?.memberIds?.toSet() ?: emptySet()) }
    var startDate by remember { mutableStateOf(initialStart?.toLocalDate() ?: LocalDate.now()) }
    var startTime by remember { mutableStateOf(initialStart?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.now().withSecond(0).withNano(0)) }
    var stillOngoing by remember { mutableStateOf(if (isEditing) initialFront?.endedAt == null else false) }
    var endDate by remember { mutableStateOf(initialEnd?.toLocalDate() ?: LocalDate.now()) }
    var endTime by remember { mutableStateOf(initialEnd?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.now().withSecond(0).withNano(0)) }

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
            Text(
                if (isEditing) "Edit Front Entry" else "Add Front Entry",
                style = MaterialTheme.typography.titleLarge,
            )

            Text("Members", style = MaterialTheme.typography.labelLarge)
            if (allMembers.isEmpty()) {
                Text(
                    "No members found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            Text("Started", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(startDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                }
            }
            TimeInputRow(time = startTime, onTimeChange = { startTime = it })

            HorizontalDivider()

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

            Button(
                onClick = {
                    val zone = ZoneId.systemDefault()
                    val startInstant = LocalDateTime.of(startDate, startTime).atZone(zone).toInstant()
                    val endInstant = if (stillOngoing) null
                    else LocalDateTime.of(endDate, endTime).atZone(zone).toInstant()
                    onConfirm(selectedIds.toList(), startInstant.toString(), endInstant?.toString())
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isEditing) "Save Changes" else "Add Entry") }
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

// ── Helpers ───────────────────────────────────────────────────────────────────

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
