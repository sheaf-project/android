package systems.lupine.sheaf.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import systems.lupine.sheaf.MainActivity
import java.time.Duration
import java.time.Instant

// Recent fronts ledger: a vertical list of "<members> · <relative-time>"
// rows. Tap launches the app so the user can drill into history. Each
// entry is encoded as `startedAt~names-pipe-joined` so a single
// stringPreferencesKey holds the whole render-ready feed.
class RecentFrontsWidget : GlanceAppWidget() {

    companion object {
        val KEY_ENTRIES   = stringPreferencesKey("recent_entries")
        val KEY_LOADING   = booleanPreferencesKey("recent_loading")
        val KEY_ERROR     = booleanPreferencesKey("recent_error")

        // Per-row format: startedAt \t names-joined-by-comma \t endedAt-or-empty.
        // Rows joined by newlines. Tabs and newlines are excluded from both
        // ISO-8601 timestamps and member display names so the encoding parses
        // unambiguously without escapes.
        internal const val ROW_SEP = "\n"
        internal const val FIELD_SEP = "\t"
        internal const val NAME_SEP = ", "

        internal const val MAX_VISIBLE = 6

        private val SMALL  = DpSize(180.dp, 90.dp)
        private val MEDIUM = DpSize(240.dp, 150.dp)
        private val LARGE  = DpSize(280.dp, 230.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        val raw = prefs[KEY_ENTRIES].orEmpty()
        val isLoading = prefs[KEY_LOADING] ?: true
        val isError   = prefs[KEY_ERROR]   ?: false
        val entries = parseEntries(raw)

        val height = LocalSize.current.height
        val maxRows = when {
            height < 100.dp -> 2
            height < 170.dp -> 4
            else -> MAX_VISIBLE
        }

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            ) {
                when {
                    isLoading -> Centered("Refreshing...", isError = false)
                    isError -> Centered("Tap to retry", isError = true)
                    entries.isEmpty() -> Centered("No history yet", isError = false)
                    else -> EntryList(entries, maxRows)
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String, isError: Boolean) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(
                color = if (isError) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun EntryList(entries: List<RecentEntry>, maxRows: Int) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = "Recent fronts",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        entries.take(maxRows).forEachIndexed { i, e ->
            EntryRow(e, isCurrent = i == 0 && e.endedAt == null)
        }
        if (entries.size > maxRows) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "+${entries.size - maxRows} more",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun EntryRow(entry: RecentEntry, isCurrent: Boolean) {
    val nameColor = if (isCurrent) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isCurrent) "● " else "○ ",
            style = TextStyle(color = nameColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = entry.names.ifEmpty { "(no one)" },
            style = TextStyle(
                color = nameColor,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = formatTimeAgo(entry.startedAt),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )
    }
}

internal data class RecentEntry(
    val startedAt: String,
    val endedAt: String?,
    val names: String,
)

internal fun parseEntries(raw: String): List<RecentEntry> {
    if (raw.isBlank()) return emptyList()
    return raw.split(RecentFrontsWidget.ROW_SEP).mapNotNull { row ->
        val parts = row.split(RecentFrontsWidget.FIELD_SEP)
        if (parts.size < 2) return@mapNotNull null
        val started = parts[0]
        val names = parts[1]
        val ended = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        RecentEntry(started, ended, names)
    }
}

internal fun encodeEntries(entries: List<RecentEntry>): String =
    entries.joinToString(RecentFrontsWidget.ROW_SEP) { e ->
        listOf(e.startedAt, e.names, e.endedAt.orEmpty())
            .joinToString(RecentFrontsWidget.FIELD_SEP)
    }

private fun formatTimeAgo(iso: String): String = runCatching {
    val duration = Duration.between(Instant.parse(iso), Instant.now())
    when {
        duration.isNegative -> "soon"
        duration.toMinutes() < 1  -> "now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m"
        duration.toHours()   < 24 -> "${duration.toHours()}h"
        else                      -> "${duration.toDays()}d"
    }
}.getOrDefault("—")
