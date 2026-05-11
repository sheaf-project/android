package systems.lupine.sheaf.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import systems.lupine.sheaf.ui.auth.OnboardingScreen
import systems.lupine.sheaf.ui.debug.DebugScreen
import systems.lupine.sheaf.ui.groups.GroupDetailScreen
import systems.lupine.sheaf.ui.groups.GroupsScreen
import systems.lupine.sheaf.ui.history.HistoryScreen
import systems.lupine.sheaf.ui.journals.JournalDetailScreen
import systems.lupine.sheaf.ui.journals.JournalsScreen
import systems.lupine.sheaf.ui.home.HomeScreen
import systems.lupine.sheaf.ui.members.MemberDetailScreen
import systems.lupine.sheaf.ui.members.MemberProfileScreen
import systems.lupine.sheaf.ui.members.MembersScreen
import systems.lupine.sheaf.ui.notifications.ChannelDetailScreen
import systems.lupine.sheaf.ui.notifications.ChannelsYouOwnScreen
import systems.lupine.sheaf.ui.notifications.CreateChannelScreen
import systems.lupine.sheaf.ui.notifications.reminders.ReminderEditorScreen
import systems.lupine.sheaf.ui.notifications.reminders.RemindersScreen
import systems.lupine.sheaf.ui.polls.PollDetailScreen
import systems.lupine.sheaf.ui.polls.PollEditorScreen
import systems.lupine.sheaf.ui.polls.PollsScreen
import systems.lupine.sheaf.ui.notifications.PendingRedemptionHolder
import systems.lupine.sheaf.ui.notifications.ReceivingScreen
import systems.lupine.sheaf.ui.notifications.RedeemNotificationScreen
import systems.lupine.sheaf.ui.notifications.YourDevicesScreen
import systems.lupine.sheaf.ui.people.PeopleScreen
import systems.lupine.sheaf.ui.importsp.ImportScreen
import systems.lupine.sheaf.ui.sheafimport.SheafImportScreen
import systems.lupine.sheaf.ui.fields.CustomFieldsScreen
import systems.lupine.sheaf.ui.apikeys.ApiKeysScreen
import systems.lupine.sheaf.ui.sessions.SessionsScreen
import systems.lupine.sheaf.ui.admin.AdminPanelScreen
import systems.lupine.sheaf.ui.settings.SettingsScreen
import systems.lupine.sheaf.ui.settings.SystemEditScreen
import systems.lupine.sheaf.ui.settings.SystemSafetyScreen

// ── Route constants ───────────────────────────────────────────────────────────

object Routes {
    const val LOGIN         = "login"
    const val ONBOARDING    = "onboarding"
    const val HOME          = "home"
    const val PEOPLE        = "people"
    const val MEMBERS       = "members"
    const val MEMBER_DETAIL = "members/{memberId}"
    const val MEMBER_EDIT   = "members/{memberId}/edit"
    const val GROUPS        = "groups"
    const val GROUP_DETAIL  = "groups/{groupId}"
    const val JOURNALS      = "journals"
    const val JOURNAL_DETAIL = "journals/{journalId}"
    const val HISTORY       = "history"
    const val SETTINGS      = "settings"
    const val SYSTEM_EDIT   = "settings/system"
    const val SP_IMPORT      = "settings/import/simplyplural"
    const val SHEAF_IMPORT   = "settings/import/sheaf"
    const val CUSTOM_FIELDS  = "settings/fields"
    const val API_KEYS       = "settings/keys"
    const val SESSIONS       = "settings/sessions"
    const val ADMIN_PANEL    = "settings/admin"
    const val SYSTEM_SAFETY  = "settings/safety"
    const val FILES          = "settings/files"
    const val DEBUG          = "settings/debug"
    // Categorized settings detail screens.
    const val SETTINGS_ACCOUNT       = "settings/account"
    const val SETTINGS_APPEARANCE    = "settings/appearance"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_SERVER        = "settings/server"
    const val SETTINGS_SYSTEM        = "settings/sys"
    const val SETTINGS_DATA          = "settings/data"
    const val SETTINGS_SAFETY        = "settings/safety-cat"
    const val SETTINGS_DANGER        = "settings/danger"
    const val SETTINGS_TAGS          = "settings/tags"
    const val SETTINGS_RETENTION     = "settings/retention"
    const val NOTIFICATIONS_REDEEM   = "notifications/redeem/{code}"
    const val NOTIFICATIONS_RECEIVING = "settings/notifications/receiving"
    const val NOTIFICATIONS_DEVICES   = "settings/notifications/devices"
    const val NOTIFICATIONS_OWNED     = "settings/notifications/owned"
    const val NOTIFICATIONS_CREATE    = "settings/notifications/owned/new"
    const val NOTIFICATIONS_CHANNEL_DETAIL = "settings/notifications/owned/{channelId}"
    const val NOTIFICATIONS_REMINDERS  = "settings/notifications/reminders"
    const val NOTIFICATIONS_REMINDER_NEW  = "settings/notifications/reminders/new"
    const val NOTIFICATIONS_REMINDER_EDIT = "settings/notifications/reminders/{id}"
    const val POLLS                = "polls"
    const val POLL_DETAIL          = "polls/{pollId}"
    const val POLL_NEW             = "polls/new"
}

// ── Tab definitions ───────────────────────────────────────────────────────────

data class TopLevelDest(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDest(Routes.HOME,     "Home",     Icons.Filled.Home,                  Icons.Outlined.Home),
    TopLevelDest(Routes.PEOPLE,   "Members",  Icons.Filled.People,                Icons.Outlined.People),
    TopLevelDest(Routes.JOURNALS, "Journals", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    TopLevelDest(Routes.HISTORY,  "History",  Icons.Filled.History,               Icons.Outlined.History),
)

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun SheafApp(
    pendingRedemption: PendingRedemptionHolder,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val pendingRedeemCode by pendingRedemption.code.collectAsState()
    val navController = rememberNavController()

    // React to login state changes
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            val target = if (authViewModel.pendingOnboarding.value) Routes.ONBOARDING else Routes.HOME
            navController.navigate(target) {
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

    // Drain a pending redemption code as soon as the user is logged in.
    // Fires after the isLoggedIn → home navigation above, so the redeem
    // screen sits on top of HOME in the back stack and "Done" returns
    // the user to home rather than dropping out of the app.
    LaunchedEffect(isLoggedIn, pendingRedeemCode) {
        val code = pendingRedeemCode
        if (isLoggedIn && code != null) {
            pendingRedemption.clear()
            navController.navigate("notifications/redeem/$code")
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
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLoginSuccess = {
                    val target = if (authViewModel.pendingOnboarding.value) Routes.ONBOARDING else Routes.HOME
                    navController.navigate(target) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                })
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onNavigateToSystemSafety = { navController.navigate(Routes.SYSTEM_SAFETY) },
                    onNavigateToSpImport = { navController.navigate(Routes.SP_IMPORT) },
                    onContinue = {
                        authViewModel.completeOnboarding()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToMembers = { navController.navigate(Routes.PEOPLE) },
                    onNavigateToSystemSafety = { navController.navigate(Routes.SYSTEM_SAFETY) },
                    onNavigateToRetention = { navController.navigate(Routes.SETTINGS_RETENTION) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToPolls = { navController.navigate(Routes.POLLS) },
                )
            }
            composable(Routes.PEOPLE) {
                PeopleScreen(
                    onMemberClick = { id ->
                        navController.navigate(if (id == "new") "members/new" else "members/$id")
                    },
                    onGroupClick = { id ->
                        navController.navigate(if (id == "new") "groups/new" else "groups/$id")
                    },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.MEMBERS) {
                MembersScreen(
                    onMemberClick = { id ->
                        navController.navigate(if (id == "new") "members/new" else "members/$id")
                    },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
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
                GroupsScreen(
                    onGroupClick = { id ->
                        navController.navigate(if (id == "new") "groups/new" else "groups/$id")
                    },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.GROUP_DETAIL) { backStack ->
                val groupId = backStack.arguments?.getString("groupId") ?: "new"
                GroupDetailScreen(groupId = groupId, onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.JOURNALS) {
                JournalsScreen(
                    onEntryClick = { id ->
                        navController.navigate(if (id == "new") "journals/new" else "journals/$id")
                    },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.JOURNAL_DETAIL) {
                JournalDetailScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.NOTIFICATIONS_REDEEM) { backStack ->
                val code = backStack.arguments?.getString("code") ?: return@composable
                RedeemNotificationScreen(
                    activationCode = code,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToProfile       = { navController.navigate(Routes.SYSTEM_EDIT) },
                    onNavigateToAccount       = { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                    onNavigateToAppearance    = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    onNavigateToNotifications = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                    onNavigateToServer        = { navController.navigate(Routes.SETTINGS_SERVER) },
                    onNavigateToSystem        = { navController.navigate(Routes.SETTINGS_SYSTEM) },
                    onNavigateToData          = { navController.navigate(Routes.SETTINGS_DATA) },
                    onNavigateToSafety        = { navController.navigate(Routes.SETTINGS_SAFETY) },
                    onNavigateToDanger        = { navController.navigate(Routes.SETTINGS_DANGER) },
                    onNavigateToAdminPanel    = { navController.navigate(Routes.ADMIN_PANEL) },
                    onNavigateToDebug         = { navController.navigate(Routes.DEBUG) },
                )
            }
            composable(Routes.SETTINGS_ACCOUNT) {
                systems.lupine.sheaf.ui.settings.AccountSettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToApiKeys = { navController.navigate(Routes.API_KEYS) },
                    onNavigateToSessions = { navController.navigate(Routes.SESSIONS) },
                )
            }
            composable(Routes.SETTINGS_APPEARANCE) {
                systems.lupine.sheaf.ui.settings.AppearanceSettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.SETTINGS_NOTIFICATIONS) {
                systems.lupine.sheaf.ui.settings.NotificationSettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToReceiving = { navController.navigate(Routes.NOTIFICATIONS_RECEIVING) },
                    onNavigateToYourDevices = { navController.navigate(Routes.NOTIFICATIONS_DEVICES) },
                    onNavigateToChannelsYouOwn = { navController.navigate(Routes.NOTIFICATIONS_OWNED) },
                    onNavigateToReminders = { navController.navigate(Routes.NOTIFICATIONS_REMINDERS) },
                )
            }
            composable(Routes.NOTIFICATIONS_RECEIVING) {
                ReceivingScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.NOTIFICATIONS_DEVICES) {
                YourDevicesScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.NOTIFICATIONS_OWNED) {
                ChannelsYouOwnScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onCreateNew = { navController.navigate(Routes.NOTIFICATIONS_CREATE) },
                    onChannelClick = { id ->
                        navController.navigate("settings/notifications/owned/$id")
                    },
                )
            }
            composable(Routes.NOTIFICATIONS_CREATE) {
                CreateChannelScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onCreated = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTIFICATIONS_CHANNEL_DETAIL) { backStack ->
                val id = backStack.arguments?.getString("channelId") ?: return@composable
                ChannelDetailScreen(
                    channelId = id,
                    onNavigateUp = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTIFICATIONS_REMINDERS) {
                RemindersScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onCreateNew = { navController.navigate(Routes.NOTIFICATIONS_REMINDER_NEW) },
                    onEdit = { id ->
                        navController.navigate("settings/notifications/reminders/$id")
                    },
                )
            }
            composable(Routes.NOTIFICATIONS_REMINDER_NEW) {
                ReminderEditorScreen(
                    reminderId = null,
                    onNavigateUp = { navController.navigateUp() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTIFICATIONS_REMINDER_EDIT) { backStack ->
                val id = backStack.arguments?.getString("id") ?: return@composable
                ReminderEditorScreen(
                    reminderId = id,
                    onNavigateUp = { navController.navigateUp() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.POLLS) {
                PollsScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onPollClick = { id -> navController.navigate("polls/$id") },
                    onCreateNew = { navController.navigate(Routes.POLL_NEW) },
                )
            }
            composable(Routes.POLL_NEW) {
                PollEditorScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.POLL_DETAIL) {
                PollDetailScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.SETTINGS_SERVER) {
                systems.lupine.sheaf.ui.settings.ServerSettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.SETTINGS_SYSTEM) {
                systems.lupine.sheaf.ui.settings.SystemCategoryScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToCustomFields = { navController.navigate(Routes.CUSTOM_FIELDS) },
                    onNavigateToTags = { navController.navigate(Routes.SETTINGS_TAGS) },
                )
            }
            composable(Routes.SETTINGS_TAGS) {
                systems.lupine.sheaf.ui.tags.TagsManagerScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.SETTINGS_DATA) {
                systems.lupine.sheaf.ui.settings.DataSettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToFiles = { navController.navigate(Routes.FILES) },
                    onNavigateToSpImport = { navController.navigate(Routes.SP_IMPORT) },
                    onNavigateToSheafImport = { navController.navigate(Routes.SHEAF_IMPORT) },
                )
            }
            composable(Routes.SETTINGS_SAFETY) {
                systems.lupine.sheaf.ui.settings.SafetyCategoryScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToSystemSafety = { navController.navigate(Routes.SYSTEM_SAFETY) },
                    onNavigateToRetention = { navController.navigate(Routes.SETTINGS_RETENTION) },
                )
            }
            composable(Routes.SETTINGS_RETENTION) {
                systems.lupine.sheaf.ui.retention.RetentionScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.SETTINGS_DANGER) {
                systems.lupine.sheaf.ui.settings.DangerZoneScreen(
                    onNavigateUp = { navController.navigateUp() },
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
            composable(Routes.SYSTEM_SAFETY) {
                SystemSafetyScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.FILES) {
                systems.lupine.sheaf.ui.files.FilesScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.DEBUG) {
                DebugScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onShowOnboarding = {
                        authViewModel.forceShowOnboarding()
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.DEBUG) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
