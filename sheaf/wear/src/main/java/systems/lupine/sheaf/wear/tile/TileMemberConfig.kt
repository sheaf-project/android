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
    // Bumping config_version invalidates the tile's resource cache so the
    // next render fetches fresh inline images for the picked members. The
    // resourcesVersion incorporates this counter, so changes here flow
    // through the tile system's cache key automatically.
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(memberSetKey(tileId), memberIds.joinToString(","))
        .putLong(CONFIG_VERSION_KEY, System.currentTimeMillis())
        .apply()
}

internal const val CONFIG_VERSION_KEY = "tile_config_version"

internal fun tileConfigVersion(context: Context): Long =
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .getLong(CONFIG_VERSION_KEY, 0L)

internal fun loadTileMemberSet(context: Context, tileId: Int): List<String> {
    val raw = context
        .getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .getString(memberSetKey(tileId), null)
        ?: return emptyList()
    return raw.split(',').filter { it.isNotBlank() }
}

internal const val EXTRA_TILE_ID = "systems.lupine.sheaf.wear.tile.TILE_ID"
internal const val EXTRA_TILE_SERVICE_CLASS = "systems.lupine.sheaf.wear.tile.TILE_SERVICE_CLASS"
internal const val EXTRA_MEMBER_ID = "systems.lupine.sheaf.wear.tile.MEMBER_ID"
internal const val EXTRA_MODE = "systems.lupine.sheaf.wear.tile.MODE"
internal const val ACTION_PICK_TILE_MEMBERS = "systems.lupine.sheaf.wear.tile.action.PICK_TILE_MEMBERS"

internal const val MODE_TOGGLE_MEMBER = "toggle_member"
internal const val MODE_TOGGLE_END_EXISTING = "toggle_end_existing"
internal const val MODE_COMMIT_SWITCH = "commit_switch"

// Quick-switch tile keeps two pieces of transient state per tile id:
// the set of currently-selected members for the next switch, and whether
// to end existing fronts on commit. Both reset to "no selection / end on"
// after each commit so the next interaction starts clean.

private fun selectedKey(tileId: Int) = "qs_selected:$tileId"
private fun endExistingKey(tileId: Int) = "qs_end_existing:$tileId"

internal fun loadQuickSwitchSelected(context: Context, tileId: Int): Set<String> {
    val raw = context
        .getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .getString(selectedKey(tileId), null)
        ?: return emptySet()
    return raw.split(',').filter { it.isNotBlank() }.toSet()
}

internal fun saveQuickSwitchSelected(context: Context, tileId: Int, selected: Set<String>) {
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(selectedKey(tileId), selected.joinToString(","))
        .apply()
}

internal fun loadQuickSwitchEndExisting(context: Context, tileId: Int): Boolean =
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .getBoolean(endExistingKey(tileId), true)

internal fun saveQuickSwitchEndExisting(context: Context, tileId: Int, value: Boolean) {
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(endExistingKey(tileId), value)
        .apply()
}

internal fun clearQuickSwitchTransient(context: Context, tileId: Int) {
    context.getSharedPreferences(TILE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(selectedKey(tileId))
        .remove(endExistingKey(tileId))
        .apply()
}
