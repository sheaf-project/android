package systems.lupine.sheaf.wear.data

import android.content.Context

/**
 * Lightweight client-side front-change ring buffer. Each entry captures
 * the fronting member set at the moment that set last changed, plus the
 * timestamp of the change. Eventually a server-side `/recent-switches`
 * endpoint can replace this, but for the watch surfaces (history screen,
 * timeline tile) the client-only buffer is enough since the watch is the
 * read consumer anyway.
 *
 * Stored as a tiny JSON array under `tile_data`/`front_history`. Bounded
 * to [MAX_HISTORY] entries; oldest dropped when the cap is hit. Format
 * is hand-rolled to avoid pulling Moshi into the cross-process tile
 * read path.
 */
internal data class FrontHistoryEntry(
    /** ms since epoch when the new fronting set took effect. */
    val timestamp: Long,
    /** Fronting member ids at that point, in ascending order for stable diffing. */
    val memberIds: List<String>,
    /** True when the front entry was still open at snapshot time (no ended_at). */
    val ongoing: Boolean = false,
)

internal const val MAX_HISTORY = 20

private const val PREFS = "tile_data"
private const val KEY = "front_history"

internal fun readFrontHistory(context: Context): List<FrontHistoryEntry> {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, null)
        ?: return emptyList()
    return parseFrontHistoryJson(raw)
}

internal fun writeFrontHistory(context: Context, entries: List<FrontHistoryEntry>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY, frontHistoryToJson(entries))
        .apply()
}

internal fun frontHistoryToJson(entries: List<FrontHistoryEntry>): String =
    entries.joinToString(separator = ",", prefix = "[", postfix = "]") { e ->
        val ids = e.memberIds.joinToString(",") { "\"${jsonEscape(it)}\"" }
        val onTail = if (e.ongoing) ",\"o\":1" else ""
        "{\"t\":${e.timestamp},\"m\":[$ids]$onTail}"
    }

internal fun parseFrontHistoryJson(raw: String): List<FrontHistoryEntry> {
    val trimmed = raw.trim()
    if (trimmed == "[]" || trimmed.isEmpty()) return emptyList()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val body = trimmed.substring(1, trimmed.length - 1)
    val out = mutableListOf<FrontHistoryEntry>()
    var i = 0
    while (i < body.length) {
        val objStart = body.indexOf('{', i)
        if (objStart < 0) break
        val objEnd = body.indexOf('}', objStart)
        if (objEnd < 0) break
        // Member-id list lives inside [ ] within the object; the closing
        // bracket of the array sits before the object's closing brace.
        val arrStart = body.indexOf('[', objStart)
        val arrEnd = body.indexOf(']', arrStart)
        if (arrStart < 0 || arrEnd < 0 || arrEnd > objEnd + 1) {
            i = objEnd + 1
            continue
        }
        val effectiveObjEnd = body.indexOf('}', arrEnd)
        if (effectiveObjEnd < 0) break
        val obj = body.substring(objStart + 1, effectiveObjEnd)
        out.add(parseHistoryEntry(obj))
        i = effectiveObjEnd + 1
    }
    return out
}

private fun parseHistoryEntry(obj: String): FrontHistoryEntry {
    var t = 0L
    var ids = emptyList<String>()
    var ongoing = false
    // Split on commas at depth 0 (not inside the [ ] member-id array).
    val parts = mutableListOf<String>()
    var depth = 0
    var start = 0
    for (i in obj.indices) {
        val c = obj[i]
        when (c) {
            '[' -> depth++
            ']' -> depth--
            ',' -> if (depth == 0) {
                parts.add(obj.substring(start, i))
                start = i + 1
            }
        }
    }
    parts.add(obj.substring(start))
    for (kv in parts) {
        val colon = kv.indexOf(':')
        if (colon < 0) continue
        val key = kv.substring(0, colon).trim().trim('"')
        val value = kv.substring(colon + 1).trim()
        when (key) {
            "t" -> t = value.toLongOrNull() ?: 0L
            "m" -> ids = parseStringArray(value)
            "o" -> ongoing = value.trim() == "1"
        }
    }
    return FrontHistoryEntry(t, ids, ongoing)
}

private fun parseStringArray(arr: String): List<String> {
    val trimmed = arr.trim()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val body = trimmed.substring(1, trimmed.length - 1).trim()
    if (body.isEmpty()) return emptyList()
    return body.split(',').map { it.trim().trim('"').replace("\\\"", "\"").replace("\\\\", "\\") }
}

private fun jsonEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")
