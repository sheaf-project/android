package systems.lupine.sheaf.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Three-way display-mode picker shared by the QuickSwitch and
 * MemberTracker config screens. Mirrors the wear tile's "names /
 * names + avatars / avatars" choice so users get the same vocabulary
 * regardless of which surface they're configuring.
 */
@Composable
internal fun DisplayModeRow(
    selected: WidgetDisplayMode,
    onSelect: (WidgetDisplayMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Display",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(
                label = "Avatar + name",
                selected = selected == WidgetDisplayMode.AVATARS_AND_NAMES,
                onClick = { onSelect(WidgetDisplayMode.AVATARS_AND_NAMES) },
            )
            ModeChip(
                label = "Avatar only",
                selected = selected == WidgetDisplayMode.AVATARS_ONLY,
                onClick = { onSelect(WidgetDisplayMode.AVATARS_ONLY) },
            )
            ModeChip(
                label = "Name only",
                selected = selected == WidgetDisplayMode.NAMES_ONLY,
                onClick = { onSelect(WidgetDisplayMode.NAMES_ONLY) },
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(),
        modifier = Modifier.padding(PaddingValues(end = 0.dp)),
    )
}
