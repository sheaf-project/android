package systems.lupine.sheaf.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

// Selection-aware Markdown manipulation helpers used by the journal compose
// toolbar. All functions return a new TextFieldValue with the cursor / selection
// set so the UI feels natural after the transformation (selection follows the
// inserted/wrapped text).

internal fun TextFieldValue.wrapSelection(before: String, after: String = before): TextFieldValue {
    val sel = selection
    val start = sel.min
    val end = sel.max
    val selected = text.substring(start, end)
    val newText = text.substring(0, start) + before + selected + after + text.substring(end)
    val newStart = start + before.length
    val newEnd = newStart + selected.length
    return copy(
        text = newText,
        selection = if (selected.isEmpty()) TextRange(newStart) else TextRange(newStart, newEnd),
    )
}

internal fun TextFieldValue.insertAtCursor(insert: String, surroundWithBlankLines: Boolean = false): TextFieldValue {
    val sel = selection
    val start = sel.min
    val end = sel.max
    val payload = if (surroundWithBlankLines) {
        val needsLeading = start > 0 && text.getOrNull(start - 1) != '\n'
        val needsTrailing = end < text.length && text.getOrNull(end) != '\n'
        buildString {
            if (needsLeading) append("\n\n")
            append(insert)
            if (needsTrailing) append("\n\n")
        }
    } else {
        insert
    }
    val newText = text.substring(0, start) + payload + text.substring(end)
    val cursor = start + payload.length
    return copy(text = newText, selection = TextRange(cursor))
}

// Adds (or strips, if already present) a per-line prefix on each line in the
// current selection. Used for bulleted/numbered lists and headings.
internal fun TextFieldValue.toggleLinePrefix(prefix: String, prefixRegex: Regex? = null): TextFieldValue {
    val sel = selection
    val (lineStart, lineEnd) = lineBoundsOfSelection()
    val block = text.substring(lineStart, lineEnd)
    val lines = block.split("\n")
    val match = prefixRegex ?: Regex("^${Regex.escape(prefix)}")
    val allHavePrefix = lines.all { match.containsMatchIn(it) }
    val newLines = lines.map { line ->
        if (allHavePrefix) line.replaceFirst(match, "")
        else if (match.containsMatchIn(line)) line.replaceFirst(match, prefix)
        else prefix + line
    }
    val newBlock = newLines.joinToString("\n")
    val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
    val delta = newBlock.length - block.length
    val newSelStart = sel.min.coerceAtMost(lineStart + newBlock.length)
    val newSelEnd = (sel.max + delta).coerceAtLeast(newSelStart)
    return copy(text = newText, selection = TextRange(newSelStart, newSelEnd))
}

// Cycles the current line(s) through heading levels: none -> # -> ## -> ### -> none.
// Mirrors the H1/H2/H3 rotation common in editor toolbars.
internal fun TextFieldValue.cycleHeading(): TextFieldValue {
    val (lineStart, lineEnd) = lineBoundsOfSelection()
    val line = text.substring(lineStart, lineEnd).substringBefore("\n")
    val current = Regex("^(#{1,6})\\s").find(line)?.groupValues?.get(1)?.length ?: 0
    val nextLevel = when (current) {
        0 -> 1
        1 -> 2
        2 -> 3
        else -> 0
    }
    val stripped = line.replaceFirst(Regex("^#{1,6}\\s*"), "")
    val newLine = if (nextLevel == 0) stripped else "${"#".repeat(nextLevel)} $stripped"
    val firstLineEnd = lineStart + line.length
    val newText = text.substring(0, lineStart) + newLine + text.substring(firstLineEnd)
    val cursor = lineStart + newLine.length
    return copy(text = newText, selection = TextRange(cursor))
}

private fun TextFieldValue.lineBoundsOfSelection(): Pair<Int, Int> {
    val start = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0)).let {
        if (it < 0) 0 else it + 1
    }
    val end = text.indexOf('\n', selection.max).let {
        if (it < 0) text.length else it
    }
    return start to end
}
