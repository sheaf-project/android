package systems.lupine.sheaf.ui.importcommon

/**
 * Escape a string for embedding in a hand-built JSON body.
 *
 * The importers assemble their submit bodies as strings (the options blob is
 * already-encoded JSON, so a typed data class would mean encoding it twice).
 * That means every interpolated value has to be escaped here, and the escapers
 * were doing quotes and backslashes only. A pasted PluralKit token with a
 * newline in it (trivially easy: copy a token out of a chat client and pick up
 * the trailing line break) produced a body with a literal newline inside a JSON
 * string, which is invalid JSON, so the whole import 422'd with nothing useful
 * to show the user.
 *
 * Handles the full set JSON requires: backslash, quote, the named control
 * escapes, and \\u00xx for everything else below 0x20.
 */
internal fun jsonEscape(value: String): String {
    val sb = StringBuilder(value.length + 8)
    for (c in value) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\b' -> sb.append("\\b")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\u000C' -> sb.append("\\f")
            else ->
                if (c < ' ') sb.append("\\u%04x".format(c.code))
                else sb.append(c)
        }
    }
    return sb.toString()
}

/** [jsonEscape]d and wrapped in quotes, ready to drop into a JSON body. */
internal fun jsonQuote(value: String): String = "\"${jsonEscape(value)}\""
