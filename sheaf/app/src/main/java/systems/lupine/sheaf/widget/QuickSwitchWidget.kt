package systems.lupine.sheaf.widget

import android.appwidget.AppWidgetManager
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
import androidx.glance.appwidget.GlanceAppWidgetManager
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

class QuickSwitchWidget : GlanceAppWidget() {

    companion object {
        val KEY_PICKED_IDS    = stringPreferencesKey("qs_picked_ids")
        val KEY_PICKED_NAMES  = stringPreferencesKey("qs_picked_names")
        val KEY_PICKED_COLORS = stringPreferencesKey("qs_picked_colors")
        val KEY_LOADING       = booleanPreferencesKey("qs_loading")
        val KEY_ERROR         = booleanPreferencesKey("qs_error")
        val KEY_UNCONFIGURED  = booleanPreferencesKey("qs_unconfigured")
        // Display mode stored as the enum.name string. Falls through
        // to AVATARS_AND_NAMES when missing so the upgrade path
        // doesn't surprise anyone with a renderer change.
        val KEY_DISPLAY_MODE  = stringPreferencesKey("qs_display_mode")

    }

    // Exact (not Responsive) so the composable reads the host's actual
    // resized dimensions. Previously SizeMode.Responsive snapped LocalSize
    // to one of three buckets — if the user resized between buckets the
    // row count stayed pinned to the smallest one and most of the
    // widget rendered blank.
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Map this Glance id to its AppWidget id once so each per-member
        // trampoline intent can be tagged with the widget id (used to refresh
        // post-switch). Looking it up inside the composable would force a
        // suspend boundary on every recomposition.
        val widgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        provideContent { Content(widgetId) }
    }

    @Composable
    private fun Content(widgetId: Int) {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        val ids    = prefs[KEY_PICKED_IDS]?.split("|")?.filter { it.isNotEmpty() }    ?: emptyList()
        val names  = prefs[KEY_PICKED_NAMES]?.split("|")?.filter { it.isNotEmpty() }  ?: emptyList()
        val colors = prefs[KEY_PICKED_COLORS]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        val isLoading = prefs[KEY_LOADING] ?: true
        val isError   = prefs[KEY_ERROR]   ?: false
        val unconfigured = prefs[KEY_UNCONFIGURED] ?: false
        val displayMode = WidgetDisplayMode.fromStored(prefs[KEY_DISPLAY_MODE])

        val avatars: List<Bitmap?> = remember(ids) {
            ids.map { loadWidgetAvatar(context, it) }
        }
        val height = LocalSize.current.height
        val compact = height < 100.dp
        // Per-row height budget: rough but consistent across the three
        // list widgets. Subtract the title + padding chrome from the
        // visible height, divide by an empirical row height.
        val rowHeight = if (compact) 34.dp else 40.dp
        val chrome = if (compact) 16.dp else 36.dp  // padding + title
        val avail = (height - chrome).coerceAtLeast(rowHeight)
        val maxRows = (avail.value / rowHeight.value).toInt().coerceAtLeast(1)

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(if (compact) 8.dp else 12.dp),
            ) {
                when {
                    unconfigured -> Status("Tap to set up quick switch", isError = false)
                    isLoading -> Status("Refreshing...", isError = false)
                    isError -> Status("Tap to retry", isError = true)
                    names.isEmpty() -> Status("No members picked", isError = false)
                    else -> SwitchTiles(
                        context = context,
                        widgetId = widgetId,
                        ids = ids,
                        names = names,
                        colors = colors,
                        avatars = avatars,
                        compact = compact,
                        maxRows = maxRows,
                        displayMode = displayMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun Status(text: String, isError: Boolean) {
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
private fun SwitchTiles(
    context: Context,
    widgetId: Int,
    ids: List<String>,
    names: List<String>,
    colors: List<String>,
    avatars: List<Bitmap?>,
    compact: Boolean,
    maxRows: Int,
    displayMode: WidgetDisplayMode,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (!compact) {
            Text(
                text = "Quick switch",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
        ids.take(maxRows).forEachIndexed { i, memberId ->
            val name = names.getOrNull(i) ?: "Member"
            val intent = Intent(context, QuickSwitchTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(QuickSwitchTrampolineActivity.EXTRA_MEMBER_ID, memberId)
                putExtra(QuickSwitchTrampolineActivity.EXTRA_MEMBER_NAME, name)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // Per-member unique Uri ensures the OS doesn't dedupe pending
                // intents across rows when extras are otherwise equivalent.
                data = android.net.Uri.parse("sheaf://widget/quickswitch/$memberId")
            }
            TileRow(
                name = name,
                bitmap = avatars.getOrNull(i),
                colorHex = colors.getOrNull(i),
                compact = compact,
                onClickIntent = intent,
                displayMode = displayMode,
            )
        }
        if (ids.size > maxRows) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "+${ids.size - maxRows} more",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun TileRow(
    name: String,
    bitmap: Bitmap?,
    colorHex: String?,
    compact: Boolean,
    onClickIntent: Intent,
    displayMode: WidgetDisplayMode,
) {
    val showAvatar = displayMode != WidgetDisplayMode.NAMES_ONLY
    val showName   = displayMode != WidgetDisplayMode.AVATARS_ONLY
    val avatarSize = if (compact) 22.dp else 30.dp
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(actionStartActivity(onClickIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAvatar) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = name,
                    modifier = GlanceModifier.size(avatarSize),
                )
            } else {
                val avatarColor = parseQuickSwitchColor(colorHex ?: "#534AB7")
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
                            fontSize = if (compact) 10.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            if (showName) Spacer(modifier = GlanceModifier.width(10.dp))
        }
        if (showName) {
            Text(
                text = name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun parseQuickSwitchColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrDefault(Color(0xFF534AB7))
}
