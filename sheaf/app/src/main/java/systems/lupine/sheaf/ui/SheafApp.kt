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
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import systems.lupine.sheaf.ui.auth.AuthViewModel
import systems.lupine.sheaf.ui.auth.LoginScreen
import systems.lupine.sheaf.ui.auth.OnboardingScreen
import systems.lupine.sheaf.ui.components.LocalFileCdnBase
import systems.lupine.sheaf.ui.debug.DebugScreen
import systems.lupine.sheaf.ui.groups.GroupDetailScreen
import systems.lupine.sheaf.ui.groups.GroupsScreen
import systems.lupine.sheaf.ui.analytics.AnalyticsScreen
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
import systems.lupine.sheaf.ui.messages.BoardDetailScreen
import systems.lupine.sheaf.ui.messages.BoardsListScreen
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
    const val ANALYTICS     = "analytics"
    const val SETTINGS      = "settings"
    const val SYSTEM_EDIT   = "settings/system"
    const val SP_IMPORT      = "settings/import/simplyplural"
    const val SHEAF_IMPORT   = "settings/import/sheaf"
    const val PK_IMPORT      = "settings/import/pluralkit"
    const val PK_API_IMPORT  = "settings/import/pluralkit-api"
    const val TB_IMPORT      = "settings/import/tupperbox"
    const val PS_IMPORT      = "settings/import/pluralspace"
    const val PRISM_IMPORT   = "settings/import/prism"
    const val OPENPLURAL_IMPORT = "settings/import/openplural"
    const val IMPORT_HISTORY = "settings/import/history"
    const val IMPORT_DETAIL  = "settings/import/history/{jobId}"
    const val CUSTOM_FIELDS  = "settings/fields"
    const val API_KEYS       = "settings/keys"
    const val SESSIONS       = "settings/sessions"
    const val ADMIN_PANEL    = "settings/admin"
    const val ADMIN_AUDIT    = "settings/admin/audit"
    const val ADMIN_JOBS     = "settings/admin/jobs"
    const val ADMIN_USER_DETAIL = "settings/admin/user/{userId}"
    const val SYSTEM_SAFETY  = "settings/safety"
    const val FILES          = "settings/files"
    const val EXPORT_DATA    = "settings/export"
    const val DEBUG          = "settings/debug"
    const val SUPPORT        = "settings/support"
    // Categorized settings detail screens.
    const val SETTINGS_ACCOUNT       = "settings/account"
    const val SETTINGS_ADMIN_ACTIVITY = "settings/account/admin-activity"
    const val SETTINGS_APPEARANCE    = "settings/appearance"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_SERVER        = "settings/server"
    const val SETTINGS_SYSTEM        = "settings/sys"
    const val ARCHIVED_MEMBERS       = "settings/archived-members"
    const val RELATIONSHIPS          = "settings/relationships"
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
    const val MESSAGES             = "messages"
    const val MESSAGES_BOARD       = "messages/board/{kind}/{memberId}"
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
    TopLevelDest(Routes.HISTORY,  "History",  Icons.Filled.History,               Icons.Outlined.History),
    TopLevelDest(Routes.JOURNALS, "Journals", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    TopLevelDest(Routes.POLLS,    "Polls",    Icons.Filled.HowToVote,             Icons.Outlined.HowToVote),
)

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun SheafApp(
    pendingRedemption: PendingRedemptionHolder,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val pendingRedeem by pendingRedemption.pending.collectAsState()
    val fileCdnBase by authViewModel.fileCdnBase.collectAsState()
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
    LaunchedEffect(isLoggedIn, pendingRedeem) {
        val redemption = pendingRedeem
        if (isLoggedIn && redemption != null) {
            // Holder is consumed by RedeemNotificationViewModel after it
            // reads the instance hint, not here — so the route stays a
            // plain path-arg destination (no fiddly nav-compose query
            // args) and the instance URL stays out of nav state where
            // an encoded slash can quietly fail a route match.
            navController.navigate("notifications/redeem/${Uri.encode(redemption.code)}")
        }
    }

    // Decide whether to show the bottom bar based on current destination
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomBar = currentRoute in topLevelDestinations.map { it.route }

    // Provide the instance's file CDN base app-wide so image hosted/external
    // classification (bio editor, journals) recognises CDN-served images.
    CompositionLocalProvider(LocalFileCdnBase provides fileCdnBase) {
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
                    onNavigateToMessages = { navController.navigate(Routes.MESSAGES) },
                    onNavigateToNotifications = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
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
                    onNavigateToAnalytics = { navController.navigate(Routes.ANALYTICS) },
                )
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(
                    onNavigateUp = { navController.navigateUp() },
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
                    onNavigateToSupport       = { navController.navigate(Routes.SUPPORT) },
                    onNavigateToDebug         = { navController.navigate(Routes.DEBUG) },
                )
            }
            composable(Routes.SETTINGS_ACCOUNT) {
                systems.lupine.sheaf.ui.settings.AccountSettingsScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToApiKeys = { navController.navigate(Routes.API_KEYS) },
                    onNavigateToSessions = { navController.navigate(Routes.SESSIONS) },
                    onNavigateToAdminActivity = { navController.navigate(Routes.SETTINGS_ADMIN_ACTIVITY) },
                )
            }
            composable(Routes.SETTINGS_ADMIN_ACTIVITY) {
                systems.lupine.sheaf.ui.admin.AdminActivityScreen(
                    onNavigateUp = { navController.navigateUp() },
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
                    onChannelDuplicated = { newId ->
                        // Replace current detail entry with the duplicate so
                        // back goes to the channel list rather than the
                        // original — matches the web app's navigate-on-
                        // duplicate flow.
                        navController.navigate("settings/notifications/owned/$newId") {
                            popUpTo(Routes.NOTIFICATIONS_OWNED)
                        }
                    },
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
            composable(Routes.MESSAGES) {
                BoardsListScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onBoardClick = { kind, memberId ->
                        val mid = memberId ?: "_"
                        navController.navigate("messages/board/$kind/$mid")
                    },
                )
            }
            composable(Routes.MESSAGES_BOARD) {
                BoardDetailScreen(onNavigateUp = { navController.navigateUp() })
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
                    onNavigateToArchivedMembers = { navController.navigate(Routes.ARCHIVED_MEMBERS) },
                    onNavigateToRelationships = { navController.navigate(Routes.RELATIONSHIPS) },
                )
            }
            composable(Routes.RELATIONSHIPS) {
                systems.lupine.sheaf.ui.relationships.RelationshipTypesScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.ARCHIVED_MEMBERS) {
                systems.lupine.sheaf.ui.members.ArchivedMembersScreen(
                    onNavigateUp = { navController.navigateUp() },
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
                    onNavigateToExportData = { navController.navigate(Routes.EXPORT_DATA) },
                    onNavigateToSpImport = { navController.navigate(Routes.SP_IMPORT) },
                    onNavigateToSheafImport = { navController.navigate(Routes.SHEAF_IMPORT) },
                    onNavigateToPkFileImport = { navController.navigate(Routes.PK_IMPORT) },
                    onNavigateToPkApiImport = { navController.navigate(Routes.PK_API_IMPORT) },
                    onNavigateToTupperboxImport = { navController.navigate(Routes.TB_IMPORT) },
                    onNavigateToPluralSpaceImport = { navController.navigate(Routes.PS_IMPORT) },
                    onNavigateToPrismImport = { navController.navigate(Routes.PRISM_IMPORT) },
                    onNavigateToOpenPluralImport = { navController.navigate(Routes.OPENPLURAL_IMPORT) },
                    onNavigateToImportHistory = { navController.navigate(Routes.IMPORT_HISTORY) },
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
            composable(Routes.PK_IMPORT) {
                systems.lupine.sheaf.ui.pkimport.PluralKitFileImportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.PK_API_IMPORT) {
                systems.lupine.sheaf.ui.pkapiimport.PluralKitApiImportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.TB_IMPORT) {
                systems.lupine.sheaf.ui.tbimport.TupperboxImportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.PS_IMPORT) {
                systems.lupine.sheaf.ui.pluralspaceimport.PluralSpaceImportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.PRISM_IMPORT) {
                systems.lupine.sheaf.ui.prismimport.PrismImportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.OPENPLURAL_IMPORT) {
                systems.lupine.sheaf.ui.openpluralimport.OpenPluralImportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.EXPORT_DATA) {
                systems.lupine.sheaf.ui.export.ExportDataScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.IMPORT_HISTORY) {
                systems.lupine.sheaf.ui.imports.ImportHistoryScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onOpenJob = { jobId ->
                        navController.navigate(Routes.IMPORT_DETAIL.replace("{jobId}", jobId))
                    },
                )
            }
            composable(
                route = Routes.IMPORT_DETAIL,
                arguments = listOf(
                    androidx.navigation.navArgument("jobId") { type = androidx.navigation.NavType.StringType }
                ),
            ) {
                systems.lupine.sheaf.ui.imports.ImportJobDetailScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
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
                AdminPanelScreen(
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToAudit = { navController.navigate(Routes.ADMIN_AUDIT) },
                    onNavigateToJobs = { navController.navigate(Routes.ADMIN_JOBS) },
                    onNavigateToUserDetail = { id -> navController.navigate("settings/admin/user/$id") },
                )
            }
            composable(Routes.ADMIN_AUDIT) {
                systems.lupine.sheaf.ui.admin.AdminAuditScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.ADMIN_JOBS) {
                systems.lupine.sheaf.ui.admin.AdminJobsScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.ADMIN_USER_DETAIL) { backStack ->
                val id = backStack.arguments?.getString("userId") ?: return@composable
                systems.lupine.sheaf.ui.admin.AdminUserDetailScreen(
                    userId = id,
                    onNavigateUp = { navController.navigateUp() },
                )
            }
            composable(Routes.SYSTEM_SAFETY) {
                SystemSafetyScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.FILES) {
                systems.lupine.sheaf.ui.files.FilesScreen(onNavigateUp = { navController.navigateUp() })
            }
            composable(Routes.SUPPORT) {
                systems.lupine.sheaf.ui.support.SupportScreen(
                    onNavigateUp = { navController.navigateUp() },
                )
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
}
