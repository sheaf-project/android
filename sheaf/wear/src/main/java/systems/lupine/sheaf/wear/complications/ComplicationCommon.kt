package systems.lupine.sheaf.wear.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import systems.lupine.sheaf.wear.MainActivity
import java.time.Duration
import java.time.Instant

/**
 * Snapshot of fronting state cached by [systems.lupine.sheaf.wear.data.WearStore]
 * for complications to read without each one having to spin up its own
 * network client. Stored as JSON in SharedPreferences so the structure can
 * grow without breaking older complication services running across an app
 * upgrade.
 */
@JsonClass(generateAdapter = true)
internal data class FronterRow(
    val id: String,
    val name: String,
    /** ISO-8601 timestamp of the effective fronting-since, may be empty. */
    val since: String = "",
)

/**
 * Reads the fronter snapshot stored by WearStore. Returns null if there's
 * no current data (app never opened, or signed out).
 */
internal fun readFrontersSnapshot(context: Context): List<FronterRow>? {
    val raw = context
        .getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        .getString("fronters", null)
        ?: return null
    return parseFrontersJson(raw)
}

/**
 * Lightweight projection of a member for the config-activity picker —
 * just what's needed to display and select. The full WearMember is reachable
 * via the API once a member id is committed to a complication.
 */
@JsonClass(generateAdapter = true)
internal data class MemberRow(
    val id: String,
    val name: String,
    val emoji: String = "",
)

/** Reads the full members list cached by WearStore. */
internal fun readMembersSnapshot(context: Context): List<MemberRow>? {
    val raw = context
        .getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        .getString("members_full", null)
        ?: return null
    return parseMembersJson(raw)
}

// ── Tile load status ─────────────────────────────────────────────────────────
//
// Tiles render in a separate process from the wear app and can't observe its
// in-memory load state directly. WearStore writes the outcome of each
// `loadAll` into the shared SharedPreferences keyed by tile data so tiles
// can branch into a distinct "loading…" or "couldn't load" state rather
// than rendering "Members not found" or falling through to the broad
// unauthenticated message when the wear app simply hasn't synced yet.

internal enum class WearLoadStatus { NEVER, LOADING, OK, FAILED }

private const val KEY_LOAD_STATUS = "last_load_status"

internal fun readLoadStatus(context: Context): WearLoadStatus =
    when (
        context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
            .getString(KEY_LOAD_STATUS, null)
    ) {
        "loading" -> WearLoadStatus.LOADING
        "ok" -> WearLoadStatus.OK
        "failed" -> WearLoadStatus.FAILED
        else -> WearLoadStatus.NEVER
    }

internal fun writeLoadStatus(context: Context, status: WearLoadStatus) {
    val v = when (status) {
        WearLoadStatus.NEVER -> return
        WearLoadStatus.LOADING -> "loading"
        WearLoadStatus.OK -> "ok"
        WearLoadStatus.FAILED -> "failed"
    }
    context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LOAD_STATUS, v)
        .apply()
}

// ── Snapshot JSON codec ────────────────────────────────────────────────────────
//
// The fronter / member snapshots are produced by WearStore in the wear app
// process and read here in the (separate) watchface complication process via
// the shared "tile_data" SharedPreferences. Both sides go through Moshi so a
// member name containing the field delimiter (a comma) or an embedded quote
// round-trips intact. An earlier hand-rolled encoder/parser split objects on
// commas and only escaped quotes/backslashes, so a member named e.g.
// "Bob, Jr." corrupted the whole snapshot parse. Moshi's generated adapters
// (KSP, R8-safe) escape correctly. The wire shape is unchanged (a JSON array
// of {id,name,...} objects), so snapshots written by the old encoder still
// decode fine after upgrade.

private val snapshotMoshi = Moshi.Builder().build()

private val fronterListAdapter = snapshotMoshi.adapter<List<FronterRow>>(
    Types.newParameterizedType(List::class.java, FronterRow::class.java),
)
private val memberListAdapter = snapshotMoshi.adapter<List<MemberRow>>(
    Types.newParameterizedType(List::class.java, MemberRow::class.java),
)

internal fun encodeFrontersJson(rows: List<FronterRow>): String =
    fronterListAdapter.toJson(rows)

internal fun encodeMembersJson(rows: List<MemberRow>): String =
    memberListAdapter.toJson(rows)

// Tolerant of empty / malformed input: complications run on the watchface,
// where a thrown exception is invisible to the user, so a bad snapshot
// degrades to an empty list rather than crashing.
internal fun parseMembersJson(raw: String): List<MemberRow> =
    runCatching { memberListAdapter.fromJson(raw) }.getOrNull().orEmpty()

internal fun parseFrontersJson(raw: String): List<FronterRow> =
    runCatching { fronterListAdapter.fromJson(raw) }.getOrNull().orEmpty()

// ── Per-instance config storage ───────────────────────────────────────────────
//
// Each complication instance the user adds to a watchface gets a unique
// Int instanceId. We persist the user's member-id selection per instance so
// adding the complication twice works.

private const val MEMBER_PREFS = "complication_config"
private fun memberKey(instanceId: Int) = "member_id:$instanceId"

internal fun saveMemberConfig(context: Context, instanceId: Int, memberId: String) {
    context.getSharedPreferences(MEMBER_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(memberKey(instanceId), memberId)
        .apply()
}

internal fun loadMemberConfig(context: Context, instanceId: Int): String? =
    context.getSharedPreferences(MEMBER_PREFS, Context.MODE_PRIVATE)
        .getString(memberKey(instanceId), null)

/** Earliest non-blank `since` across the snapshot, or null. */
internal fun List<FronterRow>.earliestSince(): String? =
    mapNotNull { it.since.takeIf { s -> s.isNotBlank() } }.minOrNull()

/**
 * Sort fronters by oldest-fronting-first (most senior at index 0). Empty
 * `since` values sort to the end.
 */
internal fun List<FronterRow>.byOldestFirst(): List<FronterRow> =
    sortedBy { if (it.since.isBlank()) "9" else it.since }

/** Sort by newest-fronting-first (just-joined at index 0). */
internal fun List<FronterRow>.byNewestFirst(): List<FronterRow> =
    sortedByDescending { it.since }

/**
 * Format a duration since [iso] in the same shape the watch HomeScreen uses.
 * Returns null if [iso] is empty or unparseable.
 */
internal fun timeAgoOrNull(iso: String): String? = runCatching {
    val d = Duration.between(Instant.parse(iso), Instant.now())
    when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m"
        d.toHours() < 24 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
        else -> "${d.toDays()}d"
    }
}.getOrNull()

/**
 * Truncates a list of names to fit a target character budget, joining with
 * commas and appending "+N" when entries had to be dropped. Uses the
 * `priority` order — entries earlier in the input survive longer.
 */
internal fun fitNames(names: List<String>, budget: Int): String {
    if (names.isEmpty()) return ""
    val trimmed = mutableListOf<String>()
    var remaining = names.size
    for (name in names) {
        val candidate = (trimmed + name).joinToString(", ") +
            if (remaining - 1 > 0 && trimmed.size + 1 < names.size) ", +${names.size - (trimmed.size + 1)}" else ""
        if (candidate.length > budget && trimmed.isNotEmpty()) break
        trimmed.add(name)
        remaining--
    }
    val dropped = names.size - trimmed.size
    return if (dropped == 0) trimmed.joinToString(", ")
    else "${trimmed.joinToString(", ")}, +$dropped"
}

/** PendingIntent that opens MainActivity (default landing). */
internal fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * PendingIntent that opens the wear app and routes to the named navigation
 * destination. MainActivity reads [EXTRA_INITIAL_ROUTE] and forwards to the
 * Compose nav graph.
 */
internal fun openRoutePendingIntent(context: Context, requestCode: Int, route: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_INITIAL_ROUTE, route)
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

const val EXTRA_INITIAL_ROUTE = "systems.lupine.sheaf.wear.INITIAL_ROUTE"

/**
 * Fire-and-forget update request to every complication service we ship.
 * Called from WearStore after a data refresh; safe to call when no
 * complication is currently in use (no-ops silently).
 */
internal fun requestAllComplicationUpdates(context: Context) {
    val classes = listOf(
        FrontersOldestFirstComplicationService::class.java,
        FrontersNewestFirstComplicationService::class.java,
        OpenAppComplicationService::class.java,
        QuickSwitchComplicationService::class.java,
        FrontingDurationComplicationService::class.java,
        LastSwitchComplicationService::class.java,
        MemberFrontingComplicationService::class.java,
    )
    for (cls in classes) {
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, cls))
                .requestUpdateAll()
        }
    }
}
