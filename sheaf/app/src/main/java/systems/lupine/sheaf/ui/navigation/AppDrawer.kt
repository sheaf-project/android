package systems.lupine.sheaf.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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

/**
 * A destination the user can navigate to from the drawer, and (Home aside) pin
 * to the bottom bar. [selectedIcon] is the filled variant shown when the bar
 * slot is the current destination; it falls back to the outlined one.
 */
data class DrawerDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/** A titled cluster of drawer rows. A null title renders with no header. */
data class DrawerGroup(
    val title: String?,
    val items: List<DrawerDest>,
)

/** Home is the fixed first slot: not movable, not removable, always present. */
val homeDest = DrawerDest(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home)

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
        items = listOf(homeDest),
    ),
    DrawerGroup(
        title = "Tracking",
        items = listOf(
            DrawerDest(Routes.PEOPLE, "Members", Icons.Outlined.People, Icons.Filled.People),
            DrawerDest(Routes.GROUPS, "Groups", Icons.Outlined.FolderOpen, Icons.Filled.FolderOpen),
            DrawerDest(Routes.HISTORY, "Front history", Icons.Outlined.History, Icons.Filled.History),
            DrawerDest(Routes.ANALYTICS, "Analytics", Icons.Outlined.Insights, Icons.Filled.Insights),
        ),
    ),
    DrawerGroup(
        title = "Writing",
        items = listOf(
            DrawerDest(
                Routes.JOURNALS,
                "Journals",
                Icons.AutoMirrored.Outlined.MenuBook,
                Icons.AutoMirrored.Filled.MenuBook,
            ),
            DrawerDest(Routes.MESSAGES, "Board messages", Icons.Outlined.Forum, Icons.Filled.Forum),
        ),
    ),
    DrawerGroup(
        title = "Engage",
        items = listOf(
            DrawerDest(Routes.POLLS, "Polls", Icons.Outlined.HowToVote, Icons.Filled.HowToVote),
            DrawerDest(
                Routes.NOTIFICATIONS_REMINDERS,
                "Reminders",
                Icons.Outlined.Alarm,
                Icons.Filled.Alarm,
            ),
        ),
    ),
    DrawerGroup(
        title = "System",
        items = listOf(
            DrawerDest(Routes.RELATIONSHIPS, "Relationships", Icons.Outlined.Hub, Icons.Filled.Hub),
            DrawerDest(Routes.FILES, "Files", Icons.Outlined.Folder, Icons.Filled.Folder),
        ),
    ),
    DrawerGroup(
        title = null,
        items = listOf(
            DrawerDest(
                Routes.SETTINGS_NOTIFICATIONS,
                "Notifications",
                Icons.Outlined.Notifications,
                Icons.Filled.Notifications,
            ),
            DrawerDest(Routes.SUPPORT, "Support", Icons.Outlined.HelpOutline),
            DrawerDest(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
        ),
    ),
)

/** Every destination, flattened. */
val allDests: List<DrawerDest> = drawerGroups.flatMap { it.items }

/** Every route the drawer can reach, for chrome / selection decisions. */
val drawerRoutes: Set<String> = allDests.mapTo(mutableSetOf()) { it.route }

/** Everything the user may pin. Home is excluded: it owns the first slot. */
val pinnableDests: List<DrawerDest> = allDests.filter { it.route != Routes.HOME }

/** How many slots sit between Home and the More entry. */
const val PIN_SLOTS = 3

/** What a fresh install pins, before the user says otherwise. */
val DEFAULT_PINS: List<String> = listOf(Routes.PEOPLE, Routes.HISTORY, Routes.JOURNALS)

/**
 * Turn saved pin routes into the destinations the bar should show.
 *
 * [saved] is null when the user has never chosen, which seeds [DEFAULT_PINS];
 * an empty list is a real choice (bar of just Home and More) and is honoured as
 * one. Short lists are left short rather than topped up: padding would mean
 * unpinning something in the editor silently put a different destination in its
 * place, so the bar and the editor would disagree about what is pinned.
 *
 * Saved pins outlive the build that wrote them, so the routes are treated as
 * untrusted: unknown ones (a destination renamed or dropped in a later version)
 * are discarded rather than rendered as a dead slot, and duplicates collapse.
 * Home is never included; it is prepended by the caller.
 */
fun resolvePins(saved: List<String>?): List<DrawerDest> {
    val byRoute = pinnableDests.associateBy { it.route }
    val chosen = LinkedHashMap<String, DrawerDest>()
    (saved ?: DEFAULT_PINS).forEach { route ->
        byRoute[route]?.let { chosen.putIfAbsent(route, it) }
    }
    return chosen.values.take(PIN_SLOTS)
}

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
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
