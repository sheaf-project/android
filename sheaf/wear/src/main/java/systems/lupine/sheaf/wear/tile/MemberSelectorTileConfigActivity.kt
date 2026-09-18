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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import systems.lupine.sheaf.wear.complications.MemberRow
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.theme.SheafWearTheme

/**
 * Multi-select picker for the tiles that carry a roster.
 *
 * Reached two ways, which is what lets a tile exist more than once:
 *
 * - The tile itself, when tapped while unconfigured, with our own extras.
 * - **The system**, from the tile's settings affordance, because the service
 *   declares `com.google.android.clockwork.tiles.PROVIDER_CONFIG_ACTION`
 *   pointing at one of the actions below. Wear will only offer to add a
 *   second copy of a tile whose provider declares both that and
 *   `MULTI_INSTANCES_SUPPORTED`; without the config action the flag alone
 *   does nothing, which is why our tiles were stuck at one apiece.
 *
 * Either way the roster is saved against the tile id, which the system
 * allocates per instance - so two copies of a tile configure independently
 * with no further work.
 */
class MemberSelectorTileConfigActivity : ComponentActivity() {

    companion object {
        /**
         * Where the system puts the id of the tile being configured. Its own
         * key, not ours, and the reason a system-launched config knows which
         * copy of a tile it is editing.
         */
        const val EXTRA_CLOCKWORK_TILE_ID =
            "com.google.android.clockwork.EXTRA_PROVIDER_CONFIG_TILE_ID"

        // Fully qualified on purpose: an implicit action is matched across the
        // whole device, and a bare string like "ConfigQuickSwitchTile" is one
        // collision away from another app's config screen.
        const val ACTION_CONFIG_MEMBER_TRACKER =
            "systems.lupine.sheaf.wear.tile.CONFIG_MEMBER_TRACKER"
        const val ACTION_CONFIG_QUICK_SWITCH =
            "systems.lupine.sheaf.wear.tile.CONFIG_QUICK_SWITCH"
        const val ACTION_CONFIG_FRONTERS_LIST =
            "systems.lupine.sheaf.wear.tile.CONFIG_FRONTERS_LIST"
        const val ACTION_CONFIG_FRONTERS_FACES =
            "systems.lupine.sheaf.wear.tile.CONFIG_FRONTERS_FACES"
        const val ACTION_CONFIG_FACES_ONLY =
            "systems.lupine.sheaf.wear.tile.CONFIG_FACES_ONLY"
        const val ACTION_CONFIG_HISTORY =
            "systems.lupine.sheaf.wear.tile.CONFIG_HISTORY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The system's key first: when it launches us, ours is absent.
        val tileId = when {
            intent.hasExtra(EXTRA_CLOCKWORK_TILE_ID) ->
                intent.getIntExtra(EXTRA_CLOCKWORK_TILE_ID, -1)
            else -> intent.getIntExtra(EXTRA_TILE_ID, -1)
        }
        // A tile that launched us says which service to refresh; a
        // system-launched config says it by action instead.
        val tileClassName = intent.getStringExtra(EXTRA_TILE_SERVICE_CLASS)
            ?: when (intent.action) {
                ACTION_CONFIG_MEMBER_TRACKER -> MemberFrontingTileService::class.java.name
                ACTION_CONFIG_QUICK_SWITCH -> QuickSwitchTileService::class.java.name
                ACTION_CONFIG_FRONTERS_LIST -> FrontingTileService::class.java.name
                ACTION_CONFIG_FRONTERS_FACES -> FrontingWithAvatarsTileService::class.java.name
                ACTION_CONFIG_FACES_ONLY -> FrontingAvatarsOnlyTileService::class.java.name
                ACTION_CONFIG_HISTORY -> FrontHistoryTileService::class.java.name
                else -> null
            }
        if (tileId == -1 || tileId == 0) {
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

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "Pick members",
                    style = MaterialTheme.typography.title3,
                )
            }

            // Make the paging behaviour explicit up front: the tile shows
            // a fixed number of members per screen and adds tap-through
            // pages beyond that, so picking a long list isn't a surprise.
            item {
                Text(
                    text = "Tile shows $TILE_PAGE_SIZE per page — pick more and " +
                        "it adds tap-through pages.",
                    style = MaterialTheme.typography.caption2,
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
                val pageCount =
                    if (selected.isEmpty()) 0
                    else (selected.size + TILE_PAGE_SIZE - 1) / TILE_PAGE_SIZE
                val saveLabel =
                    if (pageCount > 1) "Save (${selected.size} · $pageCount pages)"
                    else "Save (${selected.size})"
                Chip(
                    label = { Text(saveLabel) },
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
