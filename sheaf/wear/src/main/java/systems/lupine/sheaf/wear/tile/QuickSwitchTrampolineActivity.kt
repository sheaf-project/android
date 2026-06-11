package systems.lupine.sheaf.wear.tile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.data.WearApiClient
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.WearStore

/**
 * Invisible activity launched by [QuickSwitchTileService] taps. Three
 * modes:
 *
 * - [MODE_TOGGLE_MEMBER]: flip the selection state of [EXTRA_MEMBER_ID]
 *   in this tile's transient selected-set, then refresh the tile.
 * - [MODE_TOGGLE_END_EXISTING]: flip the per-tile end-existing-fronts
 *   flag and refresh.
 * - [MODE_COMMIT_SWITCH]: read the selected set + end-existing flag,
 *   call [WearStore.switchFront], clear transient state, refresh.
 *
 * No UI is rendered (theme is `Theme.Translucent.NoTitleBar`); commits
 * surface a brief Toast with the outcome.
 */
class QuickSwitchTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)
        val tileId = intent.getIntExtra(EXTRA_TILE_ID, -1)
        if (mode == null || tileId == -1) {
            finish()
            return
        }

        when (mode) {
            MODE_TOGGLE_MEMBER -> handleToggleMember(tileId)
            MODE_TOGGLE_END_EXISTING -> handleToggleEndExisting(tileId)
            MODE_COMMIT_SWITCH -> handleCommit(tileId)
            else -> finish()
        }
    }

    private fun handleToggleMember(tileId: Int) {
        val memberId = intent.getStringExtra(EXTRA_MEMBER_ID)
        if (memberId.isNullOrBlank()) {
            finish(); return
        }
        // This activity is exported (the tile host must be able to launch it),
        // so its intent is untrusted. A genuine tile tap can only ever toggle a
        // member this tile was configured to show, so accept nothing else: that
        // rejects a forged intent toggling an arbitrary or unknown member id,
        // and any toggle against a tile id that has no configured set.
        if (memberId !in loadTileMemberSet(this, tileId).toSet()) {
            finish(); return
        }
        val current = loadQuickSwitchSelected(this, tileId).toMutableSet()
        if (memberId in current) current.remove(memberId) else current.add(memberId)
        saveQuickSwitchSelected(this, tileId, current)
        refreshTile()
        finish()
    }

    private fun handleToggleEndExisting(tileId: Int) {
        val current = loadQuickSwitchEndExisting(this, tileId)
        saveQuickSwitchEndExisting(this, tileId, !current)
        refreshTile()
        finish()
    }

    private fun handleCommit(tileId: Int) {
        // Defence in depth alongside the toggle gate: intersect the persisted
        // selection with this tile's configured member set, so a tampered
        // selection (or one a forged toggle slipped in) can only ever commit
        // members the user actually placed on this tile.
        val configured = loadTileMemberSet(this, tileId).toSet()
        val selected = loadQuickSwitchSelected(this, tileId).filter { it in configured }
        if (selected.isEmpty()) {
            Toast.makeText(applicationContext, "No members selected", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        val endExisting = loadQuickSwitchEndExisting(this, tileId)
        val name = describeSelection(selected)

        val auth = WearAuthManager(this)
        val api = WearApiClient(auth)
        val store = WearStore(api, applicationContext)

        lifecycleScope.launch {
            val ok = store.switchFront(selected, replaceFronts = endExisting)
            val msg = if (ok) "Switched to $name" else "Switch failed"
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            // Always reset transient state on commit attempts. If the call
            // failed the user can re-pick; leaving the selection sticky
            // would let a stale set commit on a later retry.
            clearQuickSwitchTransient(applicationContext, tileId)
            refreshTile()
            finish()
        }
    }

    private fun describeSelection(memberIds: List<String>): String {
        val members = readMembersSnapshot(this).orEmpty().associateBy { it.id }
        return when {
            memberIds.size == 1 -> members[memberIds[0]]?.name ?: "member"
            memberIds.size == 2 -> {
                val a = members[memberIds[0]]?.name
                val b = members[memberIds[1]]?.name
                if (a != null && b != null) "$a + $b" else "${memberIds.size} members"
            }
            else -> "${memberIds.size} members"
        }
    }

    private fun refreshTile() {
        runCatching {
            androidx.wear.tiles.TileService.getUpdater(this)
                .requestUpdate(QuickSwitchTileService::class.java)
        }
    }
}
