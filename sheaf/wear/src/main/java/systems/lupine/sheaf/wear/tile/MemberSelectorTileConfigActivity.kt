package systems.lupine.sheaf.wear.tile

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import systems.lupine.sheaf.wear.complications.MemberRow
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.theme.SheafWearTheme

/**
 * Multi-select picker launched by [MemberFrontingTileService] when the
 * user taps the unconfigured tile. Saves the chosen member ids keyed by
 * tile id and triggers a tile refresh on save so the next render shows
 * the live data.
 */
class MemberSelectorTileConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getIntExtra(EXTRA_TILE_ID, -1)
        val tileClassName = intent.getStringExtra(EXTRA_TILE_SERVICE_CLASS)
        if (tileId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val members = readMembersSnapshot(this).orEmpty()
        val initial = loadTileMemberSet(this, tileId).toSet()

        setContent {
            SheafWearTheme {
                MemberSelector(
                    members = members,
                    initialSelection = initial,
                    onSave = { selected ->
                        saveTileMemberSet(this, tileId, selected.toList())
                        refreshLaunchingTile(tileClassName)
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private fun refreshLaunchingTile(tileClassName: String?) {
        val updater = runCatching {
            androidx.wear.tiles.TileService.getUpdater(this)
        }.getOrNull() ?: return
        val cls: Class<out androidx.wear.tiles.TileService>? = tileClassName
            ?.let { runCatching {
                @Suppress("UNCHECKED_CAST")
                Class.forName(it) as Class<out androidx.wear.tiles.TileService>
            }.getOrNull() }
        if (cls != null) {
            runCatching { updater.requestUpdate(cls) }
        } else {
            // No class hint: fall back to refreshing all member-set tiles so
            // whichever tile launched the picker picks up the new selection.
            runCatching { updater.requestUpdate(MemberFrontingTileService::class.java) }
            runCatching { updater.requestUpdate(QuickSwitchTileService::class.java) }
        }
    }
}

@Composable
private fun MemberSelector(
    members: List<MemberRow>,
    initialSelection: Set<String>,
    onSave: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf(initialSelection) }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Pick members",
                    style = MaterialTheme.typography.title3,
                )
            }

            if (members.isEmpty()) {
                item {
                    Text(
                        text = "Open Sheaf on the watch first to load members.",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
                item {
                    Chip(
                        label = { Text("Cancel") },
                        onClick = onCancel,
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                return@ScalingLazyColumn
            }

            items(members, key = { it.id }) { m ->
                val pickedId = m.id
                val emoji = m.emoji.takeIf { it.isNotBlank() }
                val labelText = if (emoji != null) "$emoji ${m.name}" else m.name
                val isChecked = pickedId in selected
                ToggleChip(
                    checked = isChecked,
                    onCheckedChange = { wantChecked ->
                        selected = if (wantChecked) selected + pickedId else selected - pickedId
                    },
                    label = { Text(labelText) },
                    toggleControl = {
                        androidx.wear.compose.material.Icon(
                            imageVector = ToggleChipDefaults.checkboxIcon(checked = isChecked),
                            contentDescription = if (isChecked) "Selected" else "Not selected",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Chip(
                    label = { Text("Save (${selected.size})") },
                    onClick = { onSave(selected) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Chip(
                    label = { Text("Cancel") },
                    onClick = onCancel,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
