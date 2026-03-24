package systems.lupine.sheaf.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import systems.lupine.sheaf.ui.theme.DarkColorScheme
import systems.lupine.sheaf.ui.theme.LightColorScheme

class FrontingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    companion object {
        val KEY_MEMBER_NAMES = stringPreferencesKey("widget_member_names")
        val KEY_LOADING = booleanPreferencesKey("widget_loading")
        val KEY_ERROR = booleanPreferencesKey("widget_error")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val prefs = currentState<Preferences>()
        val memberNamesRaw = prefs[KEY_MEMBER_NAMES]
        val isLoading = prefs[KEY_LOADING] ?: true
        val isError = prefs[KEY_ERROR] ?: false
        val memberNames = memberNamesRaw
            ?.split("|")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val compact = LocalSize.current.height < 80.dp

        GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(16.dp)
                    .padding(if (compact) 8.dp else 16.dp)
                    .clickable(actionRunCallback<RefreshAction>()),
            ) {
                when {
                    isLoading -> LoadingContent()
                    isError -> ErrorContent()
                    else -> FrontingContent(memberNames, compact)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Loading…",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        )
    }
}

@Composable
private fun ErrorContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Currently Fronting",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to retry",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun FrontingContent(memberNames: List<String>, compact: Boolean = false) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (!compact) {
            Text(
                text = "Currently Fronting",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
        if (memberNames.isEmpty()) {
            Text(
                text = "No one is fronting",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        } else {
            memberNames.forEach { name ->
                Text(
                    text = name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}
