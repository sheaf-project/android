@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.SheafTopAppBar

/**
 * Choose which destinations sit in the bottom bar, and in what order. Changes
 * apply immediately, like the theme picker; there is no save button to forget
 * to press.
 *
 * Ordering is done with explicit move buttons rather than drag-and-drop: the
 * list is short, and buttons stay operable with a screen reader or switch
 * access, which a long-press drag does not.
 */
@Composable
fun NavPinsScreen(
    onNavigateUp: () -> Unit,
    viewModel: NavPinsViewModel = hiltViewModel(),
) {
    val pinned by viewModel.pins.collectAsState()
    val pinnedRoutes = pinned.map { it.route }
    val available = pinnableDests.filter { it.route !in pinnedRoutes }
    val atCapacity = pinned.size >= PIN_SLOTS

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Navigation bar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Up to $PIN_SLOTS destinations sit in the bar beside Home. " +
                    "Everything else stays one tap away under More.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            SectionHeader("In the bar")

            // Home is shown so the bar's real shape is visible, but it has no
            // controls: it always holds the first slot.
            ListItem(
                headlineContent = { Text(homeDest.label) },
                supportingContent = { Text("Always first") },
                leadingContent = { Icon(homeDest.icon, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            if (pinned.isEmpty()) {
                Text(
                    text = "Nothing pinned. The bar shows Home and More only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            pinned.forEachIndexed { index, dest ->
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                ListItem(
                    headlineContent = { Text(dest.label) },
                    supportingContent = { Text("Slot ${index + 2}") },
                    leadingContent = { Icon(dest.icon, contentDescription = null) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { viewModel.setPins(pinnedRoutes.moved(index, index - 1)) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowUpward,
                                    contentDescription = "Move ${dest.label} up",
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setPins(pinnedRoutes.moved(index, index + 1)) },
                                enabled = index < pinned.lastIndex,
                            ) {
                                Icon(
                                    Icons.Outlined.ArrowDownward,
                                    contentDescription = "Move ${dest.label} down",
                                )
                            }
                            IconButton(onClick = { viewModel.setPins(pinnedRoutes - dest.route) }) {
                                Icon(
                                    Icons.Outlined.RemoveCircleOutline,
                                    contentDescription = "Remove ${dest.label} from the bar",
                                )
                            }
                        }
                    },
                )
            }

            HorizontalDivider()
            SectionHeader(if (atCapacity) "Under More (bar is full)" else "Under More")

            available.forEach { dest ->
                Surface(
                    onClick = { viewModel.setPins(pinnedRoutes + dest.route) },
                    enabled = !atCapacity,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text(dest.label) },
                        leadingContent = { Icon(dest.icon, contentDescription = null) },
                        trailingContent = {
                            // Kept visible but inert at capacity, so the reason
                            // nothing happens is the greyed-out state rather
                            // than a control that vanished.
                            Icon(
                                Icons.Outlined.AddCircleOutline,
                                contentDescription = "Pin ${dest.label} to the bar",
                                tint = if (atCapacity) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                       else MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            headlineColor = if (atCapacity) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }

            TextButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Reset to defaults")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Move the item at [from] to [to], shifting the rest along. Out-of-range
 * targets return the list untouched, so callers can wire up buttons at the ends
 * of the list without special-casing them.
 */
internal fun List<String>.moved(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
