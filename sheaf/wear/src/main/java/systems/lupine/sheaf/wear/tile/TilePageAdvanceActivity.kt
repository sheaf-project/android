package systems.lupine.sheaf.wear.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.wear.tiles.TileService

/**
 * Invisible activity that advances a paginated tile instance to its next
 * page and asks the tile system to re-render.
 *
 * Tiles can't mutate their own state in-process, so a paginated tile's
 * "next page" control is a [androidx.wear.protolayout.ActionBuilders.LaunchAction]
 * that launches this no-UI activity. It bumps the stored page index for
 * the tile id, requests an update for the launching tile service, and
 * finishes. Wrapping past the last page is handled at render time via a
 * modulo against the live page count, so this only ever increments.
 *
 * Used by [MemberFrontingTileService] and [QuickSwitchTileService].
 */
class TilePageAdvanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getIntExtra(EXTRA_TILE_ID, -1)
        val tileClassName = intent.getStringExtra(EXTRA_TILE_SERVICE_CLASS)
        if (tileId == -1 || tileClassName == null) {
            finish()
            return
        }
        advanceTilePage(this, tileId)
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val cls = Class.forName(tileClassName) as Class<out TileService>
            TileService.getUpdater(this).requestUpdate(cls)
        }
        finish()
    }
}
