package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import systems.lupine.sheaf.wear.BuildConfig
import systems.lupine.sheaf.wear.R
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearSettingsStore
import systems.lupine.sheaf.wear.data.WearStore

/**
 * Ask the paired phone to open the Sheaf app. Uses RemoteActivityHelper (a
 * system-mediated remote start, so it isn't blocked by the phone's background-
 * activity-launch limits) with a `sheaf://open` deep link the phone app
 * registers. Toasts if the phone can't be reached.
 */
private fun openSheafOnPhone(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse("sheaf://open"))
    val future = RemoteActivityHelper(context).startRemoteActivity(intent)
    future.addListener(
        {
            runCatching { future.get() }.onFailure {
                Toast.makeText(context, "Couldn't reach your phone", Toast.LENGTH_SHORT).show()
            }
        },
        ContextCompat.getMainExecutor(context),
    )
}

val LocalWearStore    = staticCompositionLocalOf<WearStore> { error("No WearStore") }
val LocalWearAuth     = staticCompositionLocalOf<WearAuthManager> { error("No WearAuthManager") }
val LocalWearSettings = staticCompositionLocalOf<WearSettingsStore> { error("No WearSettingsStore") }

const val NAV_MENU           = "menu"
const val NAV_HOME           = "home"
const val NAV_MEMBERS        = "members"
const val NAV_SWITCH         = "switch"
const val NAV_SETTINGS       = "settings"
const val NAV_GROUPS         = "groups"
const val NAV_GROUP_DETAIL   = "group_detail"
const val NAV_HISTORY        = "history"
const val NAV_MEMBER_PROFILE = "member_profile"
const val NAV_ADD_MEMBER     = "add_member"

@Composable
fun WearNavigation(
    authManager: WearAuthManager,
    store: WearStore,
    settings: WearSettingsStore,
    onRequestSync: () -> Unit = {},
    /** Optional deep-link target; when set, navigated to on top of NAV_MENU. */
    initialRoute: String? = null,
) {
    val isAuthenticated by authManager.isAuthenticatedFlow.collectAsState()

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) store.loadAll()
    }

    if (!isAuthenticated) {
        val context = LocalContext.current
        var showLogin by remember { mutableStateOf(false) }
        if (showLogin) {
            WearLoginScreen(
                apiClient = store.apiClient,
                onCancel = { showLogin = false },
            )
        } else {
            val menuListState = rememberScalingLazyListState()
            Scaffold(
                timeText = { TimeText() },
                positionIndicator = { PositionIndicator(scalingLazyListState = menuListState) },
            ) {
                ScalingLazyColumn(state = menuListState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.title2,
                        )
                    }
                    item {
                        Text(
                            text = "Open Sheaf on your phone to sign in",
                            style = MaterialTheme.typography.body1,
                        )
                    }
                    item {
                        Chip(
                            label = { Text("Open on phone") },
                            onClick = { openSheafOnPhone(context) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Chip(
                            label = { Text("Retry Sync") },
                            onClick = onRequestSync,
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Chip(
                            label = { Text("Sign in manually") },
                            onClick = { showLogin = true },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // Build stamp so users can sanity-check whether a
                    // stale wear install is the cause of pairing trouble.
                    item {
                        Text(
                            text = "Sheaf ${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_COMMIT}" +
                                if (BuildConfig.DEBUG) " · debug" else "",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
        return
    }

    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(initialRoute) {
        if (initialRoute != null && initialRoute != NAV_MENU) {
            // Deep-link entries (from tiles, complications, etc.) came in
            // pointing at a specific screen, not the menu. Pop the menu off
            // the back stack so a swipe-back exits straight to the
            // watchface instead of hopping through the menu first.
            // launchSingleTop guards against double-launch if the effect
            // re-fires for any reason.
            navController.navigate(initialRoute) {
                popUpTo(NAV_MENU) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(
        LocalWearStore    provides store,
        LocalWearAuth     provides authManager,
        LocalWearSettings provides settings,
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = NAV_MENU,
        ) {
            composable(NAV_MENU)     { MenuScreen(navController) }
            composable(NAV_HOME)     { HomeScreen(navController) }
            composable(NAV_MEMBERS)  { MembersScreen(navController) }
            composable(NAV_SWITCH)   { SwitchScreen(navController) }
            composable(NAV_SETTINGS) { SettingsScreen(navController) }
            composable(NAV_GROUPS)   { GroupsScreen(navController) }
            composable(NAV_HISTORY)  { FrontHistoryScreen(navController) }
            composable(NAV_ADD_MEMBER) { AddMemberScreen(navController) }
            composable(
                route = "$NAV_MEMBER_PROFILE/{memberId}",
                arguments = listOf(navArgument("memberId") { type = NavType.StringType }),
            ) { back ->
                val memberId = back.arguments?.getString("memberId") ?: return@composable
                MemberProfileScreen(memberId = memberId, navController = navController)
            }
            composable(
                route = "$NAV_GROUP_DETAIL/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { back ->
                val groupId = back.arguments?.getString("groupId") ?: return@composable
                GroupDetailScreen(groupId = groupId, navController = navController)
            }
        }
    }
}
