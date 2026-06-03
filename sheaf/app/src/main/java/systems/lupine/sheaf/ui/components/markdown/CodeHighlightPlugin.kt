package systems.lupine.sheaf.ui.components.markdown

import android.text.style.BackgroundColorSpan
import android.text.style.TypefaceSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock

/**
 * Replaces Markwon's stock code-block rendering with one that:
 *  1. Paints a themed background behind the whole block (same shade as
 *     before — keeps continuity with the legacy compose-markdown look).
 *  2. Sets a monospace typeface across the literal.
 *  3. Runs [CodeSyntaxHighlighter] over the literal, painting per-token
 *     foreground colours in the slots picked from [colors].
 *
 * Indented code blocks (the 4-space variant) get the background +
 * monospace treatment but no syntax highlighting since they carry no
 * language hint.
 */
internal class CodeHighlightPlugin(
    private val codeBackground: Int,
    private val colors: SyntaxColors,
) : AbstractMarkwonPlugin() {

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(FencedCodeBlock::class.java) { visitor, node ->
            renderCodeBlock(
                visitor = visitor,
                node = node,
                literal = node.literal,
                language = node.info,
            )
        }
        builder.on(IndentedCodeBlock::class.java) { visitor, node ->
            renderCodeBlock(
                visitor = visitor,
                node = node,
                literal = node.literal,
                language = null,
            )
        }
    }

    /** Common rendering body for both code-block variants. Markwon's
     *  [io.noties.markwon.SpannableBuilder.setSpan] is the three-arg
     *  shape that internally uses `Spanned.SPAN_EXCLUSIVE_EXCLUSIVE`. */
    private fun renderCodeBlock(
        visitor: MarkwonVisitor,
        node: org.commonmark.node.Node,
        literal: String,
        language: String?,
    ) {
        visitor.blockStart(node)
        val builder = visitor.builder()
        val start = builder.length
        builder.append(literal)
        val end = builder.length

        // Background + monospace across the whole block.
        builder.setSpan(BackgroundColorSpan(codeBackground), start, end)
        builder.setSpan(TypefaceSpan("monospace"), start, end)

        // Per-token foreground colouring. Falls through to no-op for
        // unknown / missing language tags — the block still reads
        // fine as monospaced text on the themed background.
        CodeSyntaxHighlighter.highlight(
            offset = start,
            text = literal,
            language = language,
            colors = colors,
            applySpan = { span, s, e -> builder.setSpan(span, s, e) },
        )

        visitor.ensureNewLine()
        visitor.forceNewLine()
        visitor.blockEnd(node)
    }
}
