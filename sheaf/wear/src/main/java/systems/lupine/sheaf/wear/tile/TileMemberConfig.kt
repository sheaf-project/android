package systems.lupine.sheaf.wear.tile

import android.content.Context

/**
 * Per-tile-instance member-set persistence. The user's multi-select for
 * each [MemberFrontingTileService] instance lives in SharedPreferences
 * keyed by the tile id, so adding the same tile twice to the carousel
 * gives each instance its own independent member set.
 *
 * Stored as a comma-separated list of member ids. Empty / missing means
 * "not yet configured", which the tile renders as a tap-to-configure
 * prompt.
 */

private const val TILE_PREFS = "tile_config"
private fun memberSetKey(tileId: Int) = "member_set:$tileId"

internal fun saveTileMemberSet(context: Context, tileId: Int, memberIds: List<String>) {
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(memberSetKey(tileId), memberIds.joinToString(","))
        .apply()
}

internal fun loadTileMemberSet(context: Context, tileId: Int): List<String> {
    val raw = context
        .getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .getString(memberSetKey(tileId), null)
        ?: return emptyList()
    return raw.split(',').filter { it.isNotBlank() }
}

internal const val EXTRA_TILE_ID = "systems.lupine.sheaf.wear.tile.TILE_ID"
internal const val ACTION_PICK_TILE_MEMBERS = "systems.lupine.sheaf.wear.tile.action.PICK_TILE_MEMBERS"
