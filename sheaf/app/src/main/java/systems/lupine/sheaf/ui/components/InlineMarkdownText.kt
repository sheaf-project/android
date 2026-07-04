package systems.lupine.sheaf.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Renders a small subset of *inline* markdown: links (clickable), **bold**,
 * *italic*, ~~strikethrough~~, and `code`. Block markdown (headings, lists,
 * images, code blocks) is intentionally NOT handled - the text is treated as a
 * single inline run, so this stays safe on compact surfaces like announcement
 * banners where full markdown would break the layout. For rich bodies use
 * [SheafMarkdownText] instead.
 *
 * Only http(s) and mailto link targets are honoured; anything else (including
 * javascript:/data:) renders as plain text, so a link can't smuggle in an
 * unsafe scheme. Links open via the platform URI handler.
 */
@Composable
fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    linkColor: Color = color,
    style: TextStyle = LocalTextStyle.current,
) {
    val annotated = remember(text, linkColor) { parseInlineMarkdown(text, linkColor) }
    Text(text = annotated, modifier = modifier, color = color, style = style)
}

// Links first so a URL's contents aren't re-parsed as emphasis; `**` before a
// lone `*` so bold wins over italic. Delimited runs can't span the delimiter
// character, which keeps the matcher simple and predictable for short bodies.
private val INLINE_MARKDOWN = Regex(
    """\[([^\]]+)\]\((https?://[^)\s]+|mailto:[^)\s]+)\)""" + // 1=label 2=url
        """|\*\*([^*]+)\*\*""" +                             // 3=bold
        """|~~([^~]+)~~""" +                                 // 4=strikethrough
        """|`([^`]+)`""" +                                   // 5=code
        """|\*([^*]+)\*""" +                                 // 6=italic (*)
        """|_([^_]+)_""",                                    // 7=italic (_)
)

private fun parseInlineMarkdown(text: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        for (m in INLINE_MARKDOWN.findAll(text)) {
            if (m.range.first > last) append(text.substring(last, m.range.first))
            val g = m.groups
            when {
                g[1] != null && g[2] != null -> withLink(
                    LinkAnnotation.Url(
                        g[2]!!.value,
                        TextLinkStyles(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        ),
                    ),
                ) { append(g[1]!!.value) }
                g[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[3]!!.value) }
                g[4] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[4]!!.value) }
                g[5] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[5]!!.value) }
                g[6] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]!!.value) }
                g[7] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[7]!!.value) }
            }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
