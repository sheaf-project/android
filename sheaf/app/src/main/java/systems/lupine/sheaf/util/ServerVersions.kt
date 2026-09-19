package systems.lupine.sheaf.util

import systems.lupine.sheaf.data.model.ServerVersion

/**
 * Comparing what a server says it is against what a feature needs.
 *
 * Used for **wording, never for behaviour**. Whether a feature works is always
 * decided by asking for it and seeing what comes back - a 404, a 405, a field
 * that is absent - because that is the only thing that survives a self-hoster
 * running a build off main, a fork, or a version string we did not anticipate.
 * This turns a detected "not supported" into a sentence someone can act on:
 * which version they would need, and which one they have.
 */
data class ServerSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ServerSemVer> {
    override fun compareTo(other: ServerSemVer): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"
}

/**
 * Parse a version string, or null if it is not one.
 *
 * Tolerant on purpose: a leading `v`, and any suffix a build system felt like
 * adding (`1.5.1.dev3+g8ab21c`, `1.5.1-rc1`), are ignored rather than treated
 * as a parse failure. Missing components read as zero, so `1.5` is 1.5.0.
 *
 * A pre-release suffix is deliberately NOT ranked below its release: someone
 * running `1.5.1-rc1` has the code, and telling them to upgrade to 1.5.1 to
 * get a feature they already have would be worse than the small chance the
 * release candidate predates it.
 */
fun parseServerVersion(raw: String?): ServerSemVer? {
    val trimmed = raw?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: return null
    val parts = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(trimmed) ?: return null
    val (major, minor, patch) = parts.destructured
    return ServerSemVer(
        major = major.toIntOrNull() ?: return null,
        minor = minor.toIntOrNull() ?: 0,
        patch = patch.toIntOrNull() ?: 0,
    )
}

/**
 * Whether this server is at least [required], or null when it cannot be told.
 *
 * Null is a real answer and callers must treat it as one: the server did not
 * report a version, or reported something unparseable. Never assume the
 * pessimistic reading - a feature that works is not made unavailable by an
 * odd version string.
 */
fun ServerVersion?.isAtLeast(required: String): Boolean? {
    val floor = parseServerVersion(required) ?: return null
    val actual = parseServerVersion(this?.gitTag ?: this?.version) ?: return null
    return actual >= floor
}

/**
 * The sentence shown when a feature has been found unavailable.
 *
 * [feature] completes "this server doesn't support ...", so it reads as a
 * noun phrase. The version the server reports is included when there is one,
 * because "you need 1.5.1" is only actionable next to "you're on 1.5.0".
 */
fun serverTooOldMessage(
    feature: String,
    required: String,
    server: ServerVersion?,
): String {
    val current = server?.display
    return if (current.isNullOrBlank()) {
        "This server doesn't support $feature. It needs Sheaf server $required or later."
    } else {
        "This server doesn't support $feature. It needs Sheaf server $required or later; " +
            "this one is $current."
    }
}
