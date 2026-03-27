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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import systems.lupine.sheaf.wear.R
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearStore

val LocalWearStore = staticCompositionLocalOf<WearStore> { error("No WearStore") }
val LocalWearAuth  = staticCompositionLocalOf<WearAuthManager> { error("No WearAuthManager") }

const val NAV_MENU           = "menu"
const val NAV_HOME           = "home"
const val NAV_MEMBERS        = "members"
const val NAV_SWITCH         = "switch"
const val NAV_SETTINGS       = "settings"
const val NAV_GROUPS         = "groups"
const val NAV_GROUP_DETAIL   = "group_detail"
const val NAV_MEMBER_PROFILE = "member_profile"
const val NAV_ADD_MEMBER     = "add_member"

@Composable
fun WearNavigation(authManager: WearAuthManager, store: WearStore, onRequestSync: () -> Unit = {}) {
    val isAuthenticated by authManager.isAuthenticatedFlow.collectAsState()

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) store.loadAll()
    }

    if (!isAuthenticated) {
        var showLogin by remember { mutableStateOf(false) }
        if (showLogin) {
            WearLoginScreen(
                apiClient = store.apiClient,
                onCancel = { showLogin = false },
            )
        } else {
            Scaffold(timeText = { TimeText() }) {
                ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            label = { Text("Retry Sync") },
                            onClick = onRequestSync,
                            colors = ChipDefaults.primaryChipColors(),
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
                }
            }
        }
        return
    }

    val navController = rememberSwipeDismissableNavController()
    CompositionLocalProvider(
        LocalWearStore provides store,
        LocalWearAuth  provides authManager,
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
