@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.SheafTopAppBar

/**
 * Fronting behaviour for this device. Currently one setting: what a
 * quick-switch tap does about the fronts that are already open.
 *
 * This is deliberately not filed under Appearance. It changes what a tap
 * *does* to your front history, not how anything looks.
 */
@Composable
fun FrontingSettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: FrontingSettingsViewModel = hiltViewModel(),
) {
    val override by viewModel.quickSwitchReplace.collectAsState()
    val accountDefault by viewModel.accountDefault.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Fronting") },
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
                "Quick switch",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )
            Text(
                "What a one-tap switch does with fronts that are already open. " +
                    "This covers the carousel on Home and the quick-switch widget; " +
                    "the full switch sheet always asks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            // Naming the account's current value matters: without it, "follow
            // the account" is a choice whose effect the user cannot see from
            // here, and the account default lives on a different client.
            val followSubtitle = when (accountDefault) {
                true -> "Currently: end other fronts"
                false -> "Currently: leave other fronts running"
                null -> "Set for your whole account"
            }
            ChoiceRow(
                title = "Follow the account default",
                subtitle = followSubtitle,
                selected = override == null,
                onClick = { viewModel.setQuickSwitchReplace(null) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
            ChoiceRow(
                title = "End other fronts",
                subtitle = "A tap switches to just that member.",
                selected = override == true,
                onClick = { viewModel.setQuickSwitchReplace(true) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
            ChoiceRow(
                title = "Leave other fronts running",
                subtitle = "A tap adds that member alongside whoever is already fronting.",
                selected = override == false,
                onClick = { viewModel.setQuickSwitchReplace(false) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = if (selected) ({
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }) else null,
        )
    }
}
