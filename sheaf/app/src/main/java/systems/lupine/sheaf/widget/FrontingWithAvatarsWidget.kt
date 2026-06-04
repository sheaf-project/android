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

class FrontingWithAvatarsWidget : GlanceAppWidget() {

    companion object {
        val KEY_MEMBER_IDS    = stringPreferencesKey("avatars_widget_member_ids")
        val KEY_MEMBER_NAMES  = stringPreferencesKey("avatars_widget_member_names")
        val KEY_MEMBER_COLORS = stringPreferencesKey("avatars_widget_member_colors")
        val KEY_LOADING       = booleanPreferencesKey("avatars_widget_loading")
        val KEY_ERROR         = booleanPreferencesKey("avatars_widget_error")

        private val SMALL  = DpSize(150.dp, 60.dp)
        private val MEDIUM = DpSize(200.dp, 110.dp)
        private val LARGE  = DpSize(280.dp, 140.dp)
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

        val compact = LocalSize.current.height < 80.dp

        // Decode avatars from filesDir once per recomposition keyed by the id
        // list. Glance composes off-main so synchronous decode is safe.
        val avatars: List<Bitmap?> = remember(ids) {
            ids.map { loadWidgetAvatar(context, it) }
        }

        SheafGlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(if (compact) 10.dp else 16.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            ) {
                when {
                    isLoading -> StateMessage("Refreshing...", isError = false, compact)
                    isError -> StateMessage("Tap to retry", isError = true, compact)
                    names.isEmpty() -> StateMessage("No one is fronting", isError = false, compact)
                    else -> FrontingContent(names, colors, avatars, compact)
                }
            }
        }
    }
}

@Composable
private fun StateMessage(text: String, isError: Boolean, compact: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (!compact) {
            Text(
                text = "Currently Fronting",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
        Text(
            text = text,
            style = TextStyle(
                color = if (isError) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun FrontingContent(
    names: List<String>,
    colors: List<String>,
    avatars: List<Bitmap?>,
    compact: Boolean,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (!compact) {
            Text(
                text = "Currently Fronting",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
        }
        val maxRows = if (compact) 2 else 3
        val visible = names.take(maxRows)
        visible.forEachIndexed { i, name ->
            AvatarRow(
                name = name,
                bitmap = avatars.getOrNull(i),
                colorHex = colors.getOrNull(i),
                compact = compact,
            )
        }
        if (names.size > maxRows) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "+${names.size - maxRows} more",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (compact) 10.sp else 11.sp,
                ),
            )
        }
    }
}

@Composable
private fun AvatarRow(name: String, bitmap: Bitmap?, colorHex: String?, compact: Boolean) {
    val avatarSize = if (compact) 22.dp else 28.dp
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            // Bitmap is already alpha-masked to a circle by the avatar
            // pipeline, so no Glance cornerRadius needed (and it's noop
            // on Image pre-31 anyway).
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = name,
                modifier = GlanceModifier.size(avatarSize),
            )
        } else {
            // Fallback colored circle with initial; happens before the first
            // refresh has finished rendering avatars to disk.
            val avatarColor = parseWidgetColor(colorHex ?: "#534AB7")
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

private fun parseWidgetColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrDefault(Color(0xFF534AB7))
}
