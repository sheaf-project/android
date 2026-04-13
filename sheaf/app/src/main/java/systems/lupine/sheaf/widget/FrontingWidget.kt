package systems.lupine.sheaf.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.LocalSize
import android.content.Intent
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.SizeMode
import systems.lupine.sheaf.MainActivity
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
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class FrontingWidget : GlanceAppWidget() {

    companion object {
        val KEY_MEMBER_NAMES  = stringPreferencesKey("widget_member_names")
        val KEY_MEMBER_COLORS = stringPreferencesKey("widget_member_colors")
        val KEY_LOADING       = booleanPreferencesKey("widget_loading")
        val KEY_ERROR         = booleanPreferencesKey("widget_error")

        private val SMALL  = DpSize(130.dp, 60.dp)
        private val MEDIUM = DpSize(180.dp, 110.dp)
        private val LARGE  = DpSize(250.dp, 110.dp)
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
        val memberNamesRaw  = prefs[KEY_MEMBER_NAMES]
        val memberColorsRaw = prefs[KEY_MEMBER_COLORS]
        val isLoading = prefs[KEY_LOADING] ?: true
        val isError   = prefs[KEY_ERROR]   ?: false
        val memberNames  = memberNamesRaw?.split("|")?.filter { it.isNotEmpty() }  ?: emptyList()
        val memberColors = memberColorsRaw?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()

        val compact = LocalSize.current.height < 80.dp
        val showAvatars = !compact && LocalSize.current.width >= 200.dp

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(if (compact) 10.dp else 16.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            ) {
                when {
                    isLoading -> LoadingContent(compact)
                    isError   -> ErrorContent(compact)
                    else      -> FrontingContent(memberNames, memberColors, compact, showAvatars)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(compact: Boolean) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (compact) "Loading…" else "Refreshing…",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun ErrorContent(compact: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (!compact) {
            Text(
                text = "Currently Fronting",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
        Text(
            text = "Tap to retry",
            style = TextStyle(color = GlanceTheme.colors.error, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun FrontingContent(
    memberNames: List<String>,
    memberColors: List<String>,
    compact: Boolean,
    showAvatars: Boolean,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (!compact) {
            Text(
                text = "Currently Fronting",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
        }

        if (memberNames.isEmpty()) {
            Text(
                text = "No one is fronting",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        } else if (compact) {
            // Small: skip label (no room), show up to 2 actual names at smaller size
            val visible = memberNames.take(2)
            visible.forEachIndexed { i, name ->
                Text(
                    text = name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                if (i < visible.lastIndex) Spacer(modifier = GlanceModifier.height(2.dp))
            }
            if (memberNames.size > 2) {
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = "+${memberNames.size - 2} more",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
        } else {
            val maxRows = 3
            memberNames.take(maxRows).forEachIndexed { i, name ->
                MemberRow(name = name, colorHex = memberColors.getOrNull(i), showAvatar = showAvatars)
            }
            if (memberNames.size > maxRows) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "+${memberNames.size - maxRows} more",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun MemberRow(name: String, colorHex: String?, showAvatar: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAvatar) {
            val avatarColor = parseWidgetColor(colorHex ?: "#534AB7")
            Box(
                modifier = GlanceModifier
                    .width(26.dp)
                    .height(26.dp)
                    .cornerRadius(13.dp)
                    .background(ColorProvider(day = avatarColor, night = avatarColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        Text(
            text = name,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
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
