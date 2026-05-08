package systems.lupine.sheaf.wear.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
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
internal data class FronterRow(
    val id: String,
    val name: String,
    /** ISO-8601 timestamp of the effective fronting-since, may be empty. */
    val since: String,
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
internal data class MemberRow(
    val id: String,
    val name: String,
    val emoji: String,
)

/** Reads the full members list cached by WearStore. */
internal fun readMembersSnapshot(context: Context): List<MemberRow>? {
    val raw = context
        .getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        .getString("members_full", null)
        ?: return null
    return parseMembersJson(raw)
}

internal fun parseMembersJson(raw: String): List<MemberRow> {
    val trimmed = raw.trim()
    if (trimmed == "[]" || trimmed.isEmpty()) return emptyList()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val body = trimmed.substring(1, trimmed.length - 1)
    val rows = mutableListOf<MemberRow>()
    var i = 0
    while (i < body.length) {
        val start = body.indexOf('{', i)
        if (start < 0) break
        val end = body.indexOf('}', start)
        if (end < 0) break
        val obj = body.substring(start + 1, end)
        var id = ""
        var name = ""
        var emoji = ""
        obj.split(",").forEach { kv ->
            val colon = kv.indexOf(':')
            if (colon < 0) return@forEach
            val key = kv.substring(0, colon).trim().trim('"')
            val value = kv.substring(colon + 1).trim().trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
            when (key) {
                "id" -> id = value
                "name" -> name = value
                "emoji" -> emoji = value
            }
        }
        rows.add(MemberRow(id, name, emoji))
        i = end + 1
    }
    return rows
}

// ── Per-instance config storage ───────────────────────────────────────────────
//
// Each complication instance the user adds to a watchface gets a unique
// Int instanceId. We persist the user's member-id selection per instance so
// adding the complication twice — once for J, once for Zeyra — works.

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

/**
 * Tiny hand-written parser for the fronter-rows JSON array. Avoids dragging
 * Moshi into the complication services for one trivial structure. Tolerant
 * of empty / malformed input — returns empty list rather than throwing,
 * since complications run on the watchface where a crash is invisible to
 * the user.
 */
internal fun parseFrontersJson(raw: String): List<FronterRow> {
    val trimmed = raw.trim()
    if (trimmed == "[]" || trimmed.isEmpty()) return emptyList()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val body = trimmed.substring(1, trimmed.length - 1)
    val rows = mutableListOf<FronterRow>()
    var i = 0
    while (i < body.length) {
        val start = body.indexOf('{', i)
        if (start < 0) break
        val end = body.indexOf('}', start)
        if (end < 0) break
        val obj = body.substring(start + 1, end)
        rows.add(parseRow(obj))
        i = end + 1
    }
    return rows
}

private fun parseRow(obj: String): FronterRow {
    var id = ""
    var name = ""
    var since = ""
    obj.split(",").forEach { kv ->
        val colon = kv.indexOf(':')
        if (colon < 0) return@forEach
        val key = kv.substring(0, colon).trim().trim('"')
        val value = kv.substring(colon + 1).trim().trim('"').replace("\\\"", "\"").replace("\\\\", "\\")
        when (key) {
            "id" -> id = value
            "name" -> name = value
            "since" -> since = value
        }
    }
    return FronterRow(id, name, since)
}

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
