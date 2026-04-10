package systems.lupine.sheaf.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import systems.lupine.sheaf.ui.auth.AuthViewModel
import systems.lupine.sheaf.ui.auth.LoginScreen
import systems.lupine.sheaf.ui.groups.GroupDetailScreen
import systems.lupine.sheaf.ui.groups.GroupsScreen
import systems.lupine.sheaf.ui.history.HistoryScreen
import systems.lupine.sheaf.ui.home.HomeScreen
import systems.lupine.sheaf.ui.members.MemberDetailScreen
import systems.lupine.sheaf.ui.members.MemberProfileScreen
import systems.lupine.sheaf.ui.members.MembersScreen
import systems.lupine.sheaf.ui.importsp.ImportScreen
import systems.lupine.sheaf.ui.sheafimport.SheafImportScreen
import systems.lupine.sheaf.ui.fields.CustomFieldsScreen
import systems.lupine.sheaf.ui.apikeys.ApiKeysScreen
import systems.lupine.sheaf.ui.sessions.SessionsScreen
import systems.lupine.sheaf.ui.admin.AdminPanelScreen
import systems.lupine.sheaf.ui.settings.SettingsScreen
import systems.lupine.sheaf.ui.settings.SystemEditScreen

// ── Route constants ───────────────────────────────────────────────────────────

object Routes {
    const val LOGIN         = "login"
    const val HOME          = "home"
    const val MEMBERS       = "members"
    const val MEMBER_DETAIL = "members/{memberId}"
    const val MEMBER_EDIT   = "members/{memberId}/edit"
    const val GROUPS        = "groups"
    const val GROUP_DETAIL  = "groups/{groupId}"
    const val HISTORY       = "history"
    const val SETTINGS      = "settings"
    const val SYSTEM_EDIT   = "settings/system"
    const val SP_IMPORT      = "settings/import/simplyplural"
    const val SHEAF_IMPORT   = "settings/import/sheaf"
    const val CUSTOM_FIELDS  = "settings/fields"
    const val API_KEYS       = "settings/keys"
    const val SESSIONS       = "settings/sessions"
    const val ADMIN_PANEL    = "settings/admin"
}

// ── Tab definitions ───────────────────────────────────────────────────────────

data class TopLevelDest(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDest(Routes.HOME,     "Home",     Icons.Filled.Home,          Icons.Outlined.Home),
    TopLevelDest(Routes.MEMBERS,  "Members",  Icons.Filled.People,        Icons.Outlined.People),
    TopLevelDest(Routes.GROUPS,   "Groups",   Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial),
    TopLevelDest(Routes.HISTORY,  "History",  Icons.Filled.History,       Icons.Outlined.History),
    TopLevelDest(Routes.SETTINGS, "Settings", Icons.Filled.Settings,      Icons.Outlined.Settings),
)

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun SheafApp(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val navController = rememberNavController()

    // React to login state changes
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        } else if (navController.currentDestination?.route != Routes.LOGIN) {
            // Only navigate to login if we're not already there — avoids destroying the
            // LoginScreen (and its pending auth state) when tokens are temporarily cleared
            // mid-flow (TOTP, email verification).
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Decide whether to show the bottom bar based on current destination
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDest = navBackStack?.destination
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDest?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(onNavigateToMembers = { navController.navigate(Routes.MEMBERS) })
            }
            composable(Routes.MEMBERS) {
                MembersScreen(onMemberClick = { id ->
                    navController.navigate(if (id == "new") "members/new" else "members/$id")
                })
            }
            composable(Routes.MEMBER_DETAIL) { backStack ->
                val memberId = backStack.arguments?.getString("memberId") ?: "new"
                if (memberId == "new") {
                    MemberDetailScreen(memberId = "new", onNavigateUp = { navController.navigateUp() })
                } else {
                    MemberProfileScreen(
                        onNavigateUp = { navController.navigateUp() },
                        onEdit = { navController.navigate("members/$memberId/edit") },
                    )
                }
            }
            composable(Routes.MEMBER_EDIT) { backStack ->
                val memberId = backStack.arguments?.getString("memberId") ?: return@composable
                MemberDetailScreen(memberId = memberId, onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.GROUPS) {
                GroupsScreen(onGroupClick = { id ->
                    navController.navigate(if (id == "new") "groups/new" else "groups/$id")
                })
            }
            composable(Routes.GROUP_DETAIL) { backStack ->
                val groupId = backStack.arguments?.getString("groupId") ?: "new"
                GroupDetailScreen(groupId = groupId, onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateToSystemEdit = { navController.navigate(Routes.SYSTEM_EDIT) },
                    onNavigateToSpImport = { navController.navigate(Routes.SP_IMPORT) },
                    onNavigateToSheafImport = { navController.navigate(Routes.SHEAF_IMPORT) },
                    onNavigateToCustomFields = { navController.navigate(Routes.CUSTOM_FIELDS) },
                    onNavigateToApiKeys = { navController.navigate(Routes.API_KEYS) },
                    onNavigateToSessions = { navController.navigate(Routes.SESSIONS) },
                    onNavigateToAdminPanel = { navController.navigate(Routes.ADMIN_PANEL) },
                )
            }
            composable(Routes.SYSTEM_EDIT) {
                SystemEditScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.SP_IMPORT) {
                ImportScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.SHEAF_IMPORT) {
                SheafImportScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.CUSTOM_FIELDS) {
                CustomFieldsScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.API_KEYS) {
                ApiKeysScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.SESSIONS) {
                SessionsScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.ADMIN_PANEL) {
                AdminPanelScreen(onNavigateUp = { navController.navigateUp() })
            }
        }
    }
}
