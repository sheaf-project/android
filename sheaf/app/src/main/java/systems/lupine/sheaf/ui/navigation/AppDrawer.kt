package systems.lupine.sheaf.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.ui.Routes

/** A single destination row in the navigation drawer. */
data class DrawerDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** A titled cluster of drawer rows. A null title renders with no header. */
data class DrawerGroup(
    val title: String?,
    val items: List<DrawerDest>,
)

/**
 * The complete destination list, grouped. This is the Android expression of
 * web's sidebar: the bottom bar / rail is only a fast path to a few of these,
 * so every feature has to be reachable from here.
 *
 * Sign out is deliberately absent. A destructive, session-ending action one
 * stray swipe from any screen is a footgun; it stays in Settings > Danger zone.
 */
val drawerGroups: List<DrawerGroup> = listOf(
    DrawerGroup(
        title = null,
        items = listOf(
            DrawerDest(Routes.HOME, "Home", Icons.Outlined.Home),
        ),
    ),
    DrawerGroup(
        title = "Tracking",
        items = listOf(
            DrawerDest(Routes.PEOPLE, "Members", Icons.Outlined.People),
            DrawerDest(Routes.GROUPS, "Groups", Icons.Outlined.FolderOpen),
            DrawerDest(Routes.HISTORY, "Front history", Icons.Outlined.History),
            DrawerDest(Routes.ANALYTICS, "Analytics", Icons.Outlined.Insights),
        ),
    ),
    DrawerGroup(
        title = "Writing",
        items = listOf(
            DrawerDest(Routes.JOURNALS, "Journals", Icons.AutoMirrored.Outlined.MenuBook),
            DrawerDest(Routes.MESSAGES, "Board messages", Icons.Outlined.Forum),
        ),
    ),
    DrawerGroup(
        title = "Engage",
        items = listOf(
            DrawerDest(Routes.POLLS, "Polls", Icons.Outlined.HowToVote),
            DrawerDest(Routes.NOTIFICATIONS_REMINDERS, "Reminders", Icons.Outlined.Alarm),
        ),
    ),
    DrawerGroup(
        title = "System",
        items = listOf(
            DrawerDest(Routes.RELATIONSHIPS, "Relationships", Icons.Outlined.Hub),
            DrawerDest(Routes.FILES, "Files", Icons.Outlined.Folder),
        ),
    ),
    DrawerGroup(
        title = null,
        items = listOf(
            DrawerDest(Routes.SETTINGS_NOTIFICATIONS, "Notifications", Icons.Outlined.Notifications),
            DrawerDest(Routes.SUPPORT, "Support", Icons.Outlined.HelpOutline),
            DrawerDest(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
        ),
    ),
)

/** Every route the drawer can reach, for chrome / selection decisions. */
val drawerRoutes: Set<String> = drawerGroups.flatMap { group -> group.items.map { it.route } }.toSet()

/**
 * Drawer body: the grouped destination list. Scrolls, because the list is
 * longer than a short phone in landscape.
 */
@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    ModalDrawerSheet {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Sheaf",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
            )
            drawerGroups.forEachIndexed { index, group ->
                if (group.title != null) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                    )
                } else if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                group.items.forEach { dest ->
                    NavigationDrawerItem(
                        selected = currentRoute == dest.route,
                        onClick = { onNavigate(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(dest.label) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
