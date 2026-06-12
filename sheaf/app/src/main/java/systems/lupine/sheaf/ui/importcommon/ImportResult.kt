package systems.lupine.sheaf.ui.importcommon

import systems.lupine.sheaf.data.model.ImportJobRead
import systems.lupine.sheaf.data.model.ImportJobStatus

/**
 * Source-agnostic terminal result for importers whose exact per-source count
 * keys we don't model with a typed data class. Holds the raw counts map and
 * extracted warnings; the screen renders counts generically with humanised
 * labels so new backend count keys show up without a code change.
 */
data class ImportResult(
    val counts: Map<String, Int>,
    val warnings: List<String>,
) {
    /** Counts as ("Members imported", 12) rows, dropping zero entries, sorted. */
    fun rows(): List<Pair<String, Int>> =
        counts.entries
            .filter { it.value != 0 }
            .sortedByDescending { it.value }
            .map { humanizeCountKey(it.key) to it.value }
}

/**
 * Decode a finished [ImportJobRead] into an [ImportResult], or null when the
 * job didn't complete (caller surfaces [ImportJobRead.lastError] instead).
 */
fun ImportJobRead.terminalResult(): ImportResult? {
    if (status != ImportJobStatus.COMPLETE) return null
    val warnings = events
        .filter { it.level == "warning" }
        .map { e -> e.recordRef?.let { "$it: ${e.message}" } ?: e.message }
    return ImportResult(counts = counts, warnings = warnings)
}

/** "members_imported" -> "Members imported". */
private fun humanizeCountKey(key: String): String =
    key.split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .replaceFirstChar { it.uppercase() }
