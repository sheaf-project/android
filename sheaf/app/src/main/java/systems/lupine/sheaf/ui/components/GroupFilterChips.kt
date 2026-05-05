package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.data.model.GroupRead

// Horizontal chip-row used above member pickers (front-creation, etc.) to
// narrow members to a single group. Mirrors web's MemberSelect behaviour:
// "All" + one chip per group, single-select, tap-to-toggle.
@Composable
fun GroupFilterChips(
    groups: List<GroupRead>,
    activeGroupId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = activeGroupId == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        groups.forEach { g ->
            FilterChip(
                selected = activeGroupId == g.id,
                onClick = { onSelect(if (activeGroupId == g.id) null else g.id) },
                label = { Text(g.name) },
                leadingIcon = if (g.color != null) {
                    { ColorSwatch(g.color, size = 12.dp) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
