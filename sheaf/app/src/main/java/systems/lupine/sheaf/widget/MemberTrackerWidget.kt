package systems.lupine.sheaf.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import systems.lupine.sheaf.MainActivity

// Tracks a hand-picked set of members and shows whether each is currently
// fronting. Configured per-widget-instance via MemberTrackerConfigActivity;
// data stored in the per-widget Preferences provided by Glance.
class MemberTrackerWidget : GlanceAppWidget() {

    companion object {
        // ids and names are stored in the order the user picked them; the
        // matching "fronting?" flag set is recomputed on each refresh.
        val KEY_TRACKED_IDS    = stringPreferencesKey("tracker_ids")
        val KEY_TRACKED_NAMES  = stringPreferencesKey("tracker_names")
        val KEY_TRACKED_COLORS = stringPreferencesKey("tracker_colors")
        val KEY_FRONTING_IDS   = stringPreferencesKey("tracker_fronting_ids")
        val KEY_LOADING        = booleanPreferencesKey("tracker_loading")
        val KEY_ERROR          = booleanPreferencesKey("tracker_error")
        val KEY_UNCONFIGURED   = booleanPreferencesKey("tracker_unconfigured")

    }

    // See QuickSwitchWidget for the rationale on switching off
    // SizeMode.Responsive — we want continuous size feedback, not three
    // discrete buckets that leave space blank between them.
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        val ids    = prefs[KEY_TRACKED_IDS]?.split("|")?.filter { it.isNotEmpty() }    ?: emptyList()
        val names  = prefs[KEY_TRACKED_NAMES]?.split("|")?.filter { it.isNotEmpty() }  ?: emptyList()
        val colors = prefs[KEY_TRACKED_COLORS]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        val fronting = (prefs[KEY_FRONTING_IDS]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()).toSet()
        val isLoading = prefs[KEY_LOADING] ?: true
        val isError   = prefs[KEY_ERROR]   ?: false
        val unconfigured = prefs[KEY_UNCONFIGURED] ?: false

        val avatars: List<Bitmap?> = remember(ids) {
            ids.map { loadWidgetAvatar(context, it) }
        }
        val height = LocalSize.current.height
        val compact = height < 100.dp
        val rowHeight = if (compact) 30.dp else 44.dp
        val chrome = if (compact) 20.dp else 38.dp
        val avail = (height - chrome).coerceAtLeast(rowHeight)
        val maxRows = (avail.value / rowHeight.value).toInt().coerceAtLeast(1)

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(if (compact) 10.dp else 14.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            ) {
                when {
                    unconfigured -> StateLabel("Tap to set up watched members", isError = false)
                    isLoading -> StateLabel("Refreshing...", isError = false)
                    isError -> StateLabel("Tap to retry", isError = true)
                    names.isEmpty() -> StateLabel("No tracked members", isError = false)
                    else -> TrackerGrid(names, colors, ids, fronting, avatars, compact, maxRows)
                }
            }
        }
    }
}

@Composable
private fun StateLabel(text: String, isError: Boolean) {
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
private fun TrackerGrid(
    names: List<String>,
    colors: List<String>,
    ids: List<String>,
    fronting: Set<String>,
    avatars: List<Bitmap?>,
    compact: Boolean,
    maxRows: Int,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (!compact) {
            Text(
                text = "Watching",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
        names.take(maxRows).forEachIndexed { i, name ->
            TrackerRow(
                name = name,
                bitmap = avatars.getOrNull(i),
                colorHex = colors.getOrNull(i),
                isFronting = (ids.getOrNull(i) ?: "") in fronting,
                compact = compact,
            )
        }
        if (names.size > maxRows) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "+${names.size - maxRows} more",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

@Composable
private fun TrackerRow(
    name: String,
    bitmap: Bitmap?,
    colorHex: String?,
    isFronting: Boolean,
    compact: Boolean,
) {
    val avatarSize = if (compact) 22.dp else 28.dp
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = name,
                modifier = GlanceModifier.size(avatarSize),
            )
        } else {
            val avatarColor = parseTrackerColor(colorHex ?: "#534AB7")
            Box(
                modifier = GlanceModifier
                    .size(avatarSize)
                    .cornerRadius(avatarSize / 2)
                    .background(ColorProvider(day = avatarColor, night = avatarColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 13.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!compact) {
                Text(
                    text = if (isFronting) "Fronting now" else "Not fronting",
                    style = TextStyle(
                        color = if (isFronting) GlanceTheme.colors.primary
                                else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (isFronting) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

private fun parseTrackerColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrDefault(Color(0xFF534AB7))
}
