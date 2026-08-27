package systems.lupine.sheaf.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.ui.components.ColorSwatch
import systems.lupine.sheaf.ui.components.ErrorBanner

/**
 * The tags on one member: a chip per tag in the system, filled when applied.
 *
 * Mirrors [systems.lupine.sheaf.ui.relationships.RelationshipsEditor]: editable
 * on the member editor, read-only on the profile, and rendering nothing at all
 * in read-only mode when the member has no tags, so it can be dropped in
 * unconditionally.
 *
 * Changes apply on tap rather than waiting for the editor's Save. The tag set
 * is its own endpoint, not part of the member body that Save flushes, and the
 * relationships editor on the same screen already works this way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberTagsEditor(
    memberId: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    viewModel: MemberTagsEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(memberId) { viewModel.load(memberId) }
    val state by viewModel.state.collectAsState()

    val applied = state.allTags.filter { it.id in state.selected }
    if (readOnly && (state.isLoading || applied.isEmpty())) return
    // Nothing to show and nothing to pick from: a system with no tags defined
    // gets a pointer to where they're made rather than an empty card.
    if (!readOnly && !state.isLoading && state.allTags.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tags", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(
                    "No tags yet. Create them in Settings > System > Tags, then " +
                        "come back to apply them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(top = 8.dp)) }

            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (readOnly) {
                    applied.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag.name) },
                            leadingIcon = { ColorSwatch(tag.color ?: DEFAULT_TAG_COLOR, size = 14.dp) },
                        )
                    }
                } else {
                    state.allTags.forEach { tag ->
                        val on = tag.id in state.selected
                        FilterChip(
                            selected = on,
                            // Chips stay tappable while a save is in flight:
                            // the change is optimistic and rolls back on
                            // failure, so blocking here would only add lag.
                            onClick = { viewModel.toggle(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = if (on) ({
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }) else ({
                                ColorSwatch(tag.color ?: DEFAULT_TAG_COLOR, size = 14.dp)
                            }),
                        )
                    }
                }
            }
        }
    }
}

// Matches the swatch the tags manager falls back to for a colourless tag.
private const val DEFAULT_TAG_COLOR = "#10B981"
