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

class FrontingAvatarsOnlyWidget : GlanceAppWidget() {

    companion object {
        val KEY_MEMBER_IDS    = stringPreferencesKey("avatars_only_widget_member_ids")
        val KEY_MEMBER_NAMES  = stringPreferencesKey("avatars_only_widget_member_names")
        val KEY_MEMBER_COLORS = stringPreferencesKey("avatars_only_widget_member_colors")
        val KEY_LOADING       = booleanPreferencesKey("avatars_only_widget_loading")
        val KEY_ERROR         = booleanPreferencesKey("avatars_only_widget_error")

        // 1x1 (one big avatar), 2x1 (up to 3), 3x1 (up to 5)
        private val SMALL  = DpSize(70.dp, 70.dp)
        private val MEDIUM = DpSize(160.dp, 70.dp)
        private val LARGE  = DpSize(250.dp, 70.dp)
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
        val ids    = prefs[KEY_MEMBER_IDS]?.split("|")?.filter { it.isNotEmpty() }    ?: emptyList()
        val names  = prefs[KEY_MEMBER_NAMES]?.split("|")?.filter { it.isNotEmpty() }  ?: emptyList()
        val colors = prefs[KEY_MEMBER_COLORS]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        val isLoading = prefs[KEY_LOADING] ?: true
        val isError   = prefs[KEY_ERROR]   ?: false

        val width = LocalSize.current.width
        val capacity = when {
            width < 120.dp -> 1
            width < 200.dp -> 3
            width < 270.dp -> 5
            else -> 6
        }

        val avatars: List<Bitmap?> = remember(ids) {
            ids.map { loadWidgetAvatar(context, it) }
        }

        SheafGlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(8.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> Text(
                        text = "...",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    )
                    isError -> Text(
                        text = "!",
                        style = TextStyle(color = GlanceTheme.colors.error, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    )
                    names.isEmpty() -> Text(
                        text = "No one fronting",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                    else -> AvatarStrip(names, colors, avatars, capacity)
                }
            }
        }
    }
}

@Composable
private fun AvatarStrip(
    names: List<String>,
    colors: List<String>,
    avatars: List<Bitmap?>,
    capacity: Int,
) {
    val visible = (0 until minOf(capacity, names.size))
    val overflow = (names.size - visible.count()).coerceAtLeast(0)
    val singleSlot = capacity == 1

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // When more members are fronting than fit, the last slot becomes a
        // "+N" pill so the user knows the strip is truncated.
        val slotsForAvatars = if (overflow > 0 && !singleSlot) visible.count() - 1 else visible.count()
        val avatarSize = if (singleSlot) 48.dp else 40.dp

        for (i in 0 until slotsForAvatars) {
            AvatarCell(
                name = names[i],
                bitmap = avatars.getOrNull(i),
                colorHex = colors.getOrNull(i),
                size = avatarSize,
            )
            if (i < slotsForAvatars - 1 || (overflow > 0 && !singleSlot)) {
                Spacer(modifier = GlanceModifier.width(6.dp))
            }
        }

        if (overflow > 0 && !singleSlot) {
            OverflowPill(count = overflow + 1, size = avatarSize)
        } else if (overflow > 0 && singleSlot) {
            // 1-slot widget: tiny overlay won't fit cleanly, label it below
            // — but we have no room. Fall back to showing initial only;
            // overflow indicator is implicit by tapping in.
        }
    }
}

@Composable
private fun AvatarCell(name: String, bitmap: Bitmap?, colorHex: String?, size: androidx.compose.ui.unit.Dp) {
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = name,
            modifier = GlanceModifier.size(size),
        )
    } else {
        val avatarColor = parseAvatarsOnlyColor(colorHex ?: "#534AB7")
        Box(
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(size / 2)
                .background(ColorProvider(day = avatarColor, night = avatarColor)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = TextStyle(
                    color = ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun OverflowPill(count: Int, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 2)
            .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

private fun parseAvatarsOnlyColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrDefault(Color(0xFF534AB7))
}
