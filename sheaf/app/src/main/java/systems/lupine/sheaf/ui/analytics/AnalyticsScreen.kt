package systems.lupine.sheaf.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.MemberFrontingStats
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.MemberAvatar
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import systems.lupine.sheaf.ui.components.parseColor
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateUp: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.analytics == null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.analytics == null -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ErrorBanner(state.error!!)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.retry() }) { Text("Retry") }
                }
            }
            else -> AnalyticsBody(
                state = state,
                onWindowSelected = { viewModel.setWindow(it) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AnalyticsBody(
    state: AnalyticsUiState,
    onWindowSelected: (AnalyticsWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = state.analytics ?: return
    val memberById = remember(state.members) { state.members.associateBy { it.id } }

    // Members sorted by total time desc, then by name as a stable tiebreaker.
    // Members with zero fronting time fall to the bottom naturally.
    val ranked = remember(analytics.members, memberById) {
        analytics.members
            .filter { memberById.containsKey(it.memberId) }
            .sortedWith(
                compareByDescending<MemberFrontingStats> { it.totalSeconds }
                    .thenBy { memberById[it.memberId]?.displayNameOrName ?: "" }
            )
    }

    val activeCount = ranked.count { it.totalSeconds > 0 }
    val totalSeconds = ranked.sumOf { it.totalSeconds }

    // Hour-of-day bars aggregate across all members. Co-fronting double-
    // counts here too (same semantics as totalSeconds) which means the
    // bar tops can exceed window_seconds / 24 when multiple people front
    // simultaneously; the chart is comparative so absolute values aren't
    // surfaced, just the shape of the day.
    val hourTotals = remember(ranked) {
        LongArray(24).also { arr ->
            ranked.forEach { stats ->
                stats.hourOfDaySeconds.forEachIndexed { hour, secs ->
                    if (hour in 0..23) arr[hour] += secs
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            WindowPicker(selected = state.window, onSelected = onWindowSelected)
        }
        item {
            TotalsCard(
                totalSeconds = totalSeconds,
                activeMembers = activeCount,
                window = state.window,
            )
        }
        item {
            SectionHeader("By member")
        }
        items(ranked, key = { it.memberId }) { stats ->
            val member = memberById[stats.memberId] ?: return@items
            MemberRow(
                member = member,
                stats = stats,
                windowSeconds = max(analytics.windowSeconds, 1),
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Hour of day")
            Spacer(Modifier.height(8.dp))
            HourOfDayChart(hourTotals = hourTotals)
            Text(
                text = "All members, in your local time (${analytics.tz}). " +
                    "Co-fronting periods count toward both members, so totals " +
                    "can exceed the window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun WindowPicker(
    selected: AnalyticsWindow,
    onSelected: (AnalyticsWindow) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnalyticsWindow.values().forEach { window ->
            val isSelected = window == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(window) },
                label = { Text(window.label) },
            )
        }
    }
}

@Composable
private fun TotalsCard(
    totalSeconds: Long,
    activeMembers: Int,
    window: AnalyticsWindow,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Total front time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatDuration(totalSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "across ${window.label.lowercase(Locale.getDefault())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "Active members",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    activeMembers.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (activeMembers == 1) "fronted in window" else "fronted in window",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun MemberRow(
    member: MemberRead,
    stats: MemberFrontingStats,
    windowSeconds: Long,
) {
    val color = member.color?.let { parseColor(it) } ?: MaterialTheme.colorScheme.primary
    val fraction = (stats.totalSeconds.toFloat() / windowSeconds.toFloat()).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(member = member, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        member.displayNameOrName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val pct = String.format(Locale.getDefault(), "%.1f%%", stats.percentOfWindow * 100)
                    Text(
                        "$pct of window",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    formatDuration(stats.totalSeconds),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Horizontal "% of window" bar, member-coloured. The Box's
            // background lays the track; the foreground Box fills the
            // bar to `fraction` of width.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
            if (stats.sessionCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${stats.sessionCount} sessions · " +
                        "longest ${formatDuration(stats.longestSessionSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 24-bar vertical chart, hand-rolled on Canvas. Each bar's height is
 * proportional to its share of the largest hour, so the chart reads
 * comparatively — the absolute seconds don't matter, only the shape of
 * the day. Hour labels every 6 hours (0/6/12/18) along the X axis.
 */
@Composable
private fun HourOfDayChart(hourTotals: LongArray) {
    val maxHour = hourTotals.maxOrNull() ?: 0L
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val w = size.width
                val h = size.height
                val barCount = 24
                val gap = 2f
                val barWidth = (w - gap * (barCount - 1)) / barCount
                for (i in 0 until barCount) {
                    val ratio = if (maxHour == 0L) 0f
                        else (hourTotals[i].toFloat() / maxHour.toFloat()).coerceIn(0f, 1f)
                    val barHeight = h * ratio
                    val x = i * (barWidth + gap)
                    // Track behind every bar so empty hours are still
                    // visibly slotted, not gaps in a uniform row.
                    drawRect(
                        color = track,
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, h),
                    )
                    if (barHeight > 0f) {
                        drawRect(
                            color = accent,
                            topLeft = androidx.compose.ui.geometry.Offset(x, h - barHeight),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("0", "6", "12", "18", "23").forEach { label ->
                    Text(label, style = labelStyle, color = labelColor)
                }
            }
        }
    }
}

/** Compact duration formatter: "2h 14m", "47m", or "12s" for tiny windows. */
internal fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0m"
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}
