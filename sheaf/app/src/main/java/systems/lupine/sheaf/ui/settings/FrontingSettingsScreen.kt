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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar

/**
 * Fronting behaviour, in two parts: what a quick-switch tap does on this
 * device, and the two preferences the account carries everywhere.
 *
 * They share a screen because they answer the same question at different
 * scopes, and the device override is only meaningful next to the account
 * default it overrides. The headings keep the scopes apart.
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
    val account by viewModel.account.collectAsState()

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

            // Everything above is this device. Everything below is the account,
            // and the heading says so: the first setting here is the same value
            // the "follow the account default" row above is describing, and
            // two controls that look alike but reach different places need the
            // difference stated rather than inferred.
            Text(
                "For your whole account",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
            )
            Text(
                "These apply everywhere you use Sheaf, not just on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            account.error?.let { message ->
                ErrorBanner(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            SwitchRow(
                title = "End current fronts when starting a new one",
                subtitle = "The default for the \"end all current fronts\" choice when you switch. " +
                    "You can still override it per switch.",
                checked = account.replaceFrontsDefault,
                enabled = !account.isSaving,
                onCheckedChange = { viewModel.setReplaceFrontsDefault(it) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 20.dp))
            SwitchRow(
                title = "Coalesce contiguous fronting",
                subtitle = "Show \"fronting since\" per member instead of per entry. In a chain of " +
                    "back-to-back entries, say solo then co-fronting, their timer counts from the " +
                    "earliest one rather than restarting each time.",
                checked = account.coalesceContiguousFronts,
                enabled = !account.isSaving,
                onCheckedChange = { viewModel.setCoalesceContiguousFronts(it) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * An account setting as a switch.
 *
 * [checked] is nullable because "we have not been told yet" is a real state:
 * the row is disabled until the answer arrives, rather than showing a
 * confident off that somebody would turn on twice.
 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean?,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val known = checked != null
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked == true,
                enabled = enabled && known,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier.alpha(if (known) 1f else 0.5f),
    )
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
