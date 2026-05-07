package systems.lupine.sheaf.wear.util

/**
 * Aggressively strips Markdown syntax for plain-text rendering on a watch.
 *
 * The phone renders Markdown properly; the watch doesn't, and shouldn't bother
 * — there's no room for headings, lists, or images on a 1.4" screen and the
 * raw syntax leaking into the body text is worse than no formatting at all.
 *
 * Handled:
 * - `![alt](url)` image references: dropped entirely
 * - `[text](url)` links: unwrapped to just `text`
 * - `**text**` / `__text__` / `*text*` / `_text_` emphasis: unwrapped
 * - `` `text` `` inline code: unwrapped
 * - leading `#` heading markers: stripped
 * - leading `>` blockquote, `- ` / `* ` / `+ ` list markers: stripped
 * - leading `1. ` style ordered-list markers: stripped
 * - HTML `<br>` and stray `\r`: collapsed to newline
 *
 * Not handled (rare in member descriptions): tables, code fences, footnotes.
 * If they ever start appearing, fix forward.
 */
fun stripMarkdown(input: String): String {
    if (input.isEmpty()) return input
    var s = input

    // Image refs first — we drop the whole thing rather than unwrap to alt
    // text, since alt for a member-description image is rarely useful.
    s = IMAGE_REGEX.replace(s, "")
    // Links: keep the visible text only.
    s = LINK_REGEX.replace(s) { it.groupValues[1] }
    // Inline emphasis. Order matters — strip the heaviest markers first so
    // `**foo**` doesn't accidentally leave a stray `*foo*`.
    s = BOLD_STAR.replace(s) { it.groupValues[1] }
    s = BOLD_UNDER.replace(s) { it.groupValues[1] }
    s = ITALIC_STAR.replace(s) { it.groupValues[1] }
    s = ITALIC_UNDER.replace(s) { it.groupValues[1] }
    s = INLINE_CODE.replace(s) { it.groupValues[1] }

    // Per-line leading marker cleanup.
    s = s.lineSequence().joinToString("\n") { line ->
        var l = line
        l = HEADING_PREFIX.replace(l, "")
        l = BLOCKQUOTE_PREFIX.replace(l, "")
        l = UNORDERED_PREFIX.replace(l, "")
        l = ORDERED_PREFIX.replace(l, "")
        l
    }

    s = s.replace("<br>", "\n", ignoreCase = true)
    s = s.replace("\r", "")
    return s.trim()
}

private val IMAGE_REGEX = Regex("""!\[[^\]]*]\([^)]*\)""")
private val LINK_REGEX = Regex("""\[([^\]]+)]\([^)]*\)""")
private val BOLD_STAR = Regex("""\*\*([^*]+)\*\*""")
private val BOLD_UNDER = Regex("""__([^_]+)__""")
private val ITALIC_STAR = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
private val ITALIC_UNDER = Regex("""(?<!_)_([^_\n]+)_(?!_)""")
private val INLINE_CODE = Regex("""`([^`\n]+)`""")
private val HEADING_PREFIX = Regex("""^\s{0,3}#{1,6}\s+""")
private val BLOCKQUOTE_PREFIX = Regex("""^\s{0,3}>\s?""")
private val UNORDERED_PREFIX = Regex("""^\s{0,3}[-*+]\s+""")
private val ORDERED_PREFIX = Regex("""^\s{0,3}\d{1,3}\.\s+""")
