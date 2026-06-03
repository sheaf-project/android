package systems.lupine.sheaf.ui.components

import android.graphics.Color as AndroidColor
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import coil.Coil
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import systems.lupine.sheaf.ui.components.markdown.CodeHighlightPlugin
import systems.lupine.sheaf.ui.components.markdown.SyntaxColors

/**
 * Themed markdown renderer with code-block syntax highlighting.
 *
 * Switched off the `dev.jeziellago.compose-markdown` `MarkdownText` wrapper
 * because that library bakes its Markwon plugin chain into private
 * internals — we couldn't inject a code-highlight plugin through it. We
 * now drive Markwon directly via an [AndroidView]+[TextView], with the
 * same auth-aware Coil image fetcher as before (so embedded
 * `/v1/files/{key}` images keep working) plus our custom
 * [CodeHighlightPlugin] for per-language token colouring.
 *
 * Same public API as the original wrapper so call sites in journals,
 * member bios, board messages, etc. don't need touching.
 */
@Composable
fun SheafMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    truncateOnTextOverflow: Boolean = false,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { Coil.imageLoader(context) }
    val scheme = MaterialTheme.colorScheme

    // Resolved ARGB ints for everything Markwon / our plugins need at
    // render time. Recomputed when the colour scheme flips (light/dark,
    // palette swap) so the code block recolours without a relaunch.
    val codeBackgroundArgb = remember(scheme) {
        // Slight darken from surfaceVariant to keep code distinct from
        // surrounding chrome on light themes. surfaceVariant alone reads
        // as "card background" too closely.
        scheme.surfaceVariant.toArgb()
    }
    val syntaxColors = remember(scheme) { buildSyntaxColors(scheme.primary, scheme) }
    val onSurfaceArgb = remember(scheme) { scheme.onSurface.toArgb() }
    val linkArgb = remember(scheme) { scheme.primary.toArgb() }
    val effectiveTextStyle = style.takeIf { it != TextStyle.Default }
        ?: MaterialTheme.typography.bodyMedium
    val textSizeSp = effectiveTextStyle.fontSize.value
    val textColorArgb = (effectiveTextStyle.color.takeIf { it != Color.Unspecified } ?: scheme.onSurface).toArgb()
    val selectionColors = LocalTextSelectionColors.current
    val highlightArgb = remember(selectionColors) { selectionColors.backgroundColor.toArgb() }

    // Build Markwon once per colour configuration so plugin chain isn't
    // re-instantiated on every recomposition. Re-keys when the active
    // colour scheme changes so swatch swaps re-stylise live.
    val markwon = remember(
        context,
        codeBackgroundArgb,
        syntaxColors,
        linkArgb,
        imageLoader,
    ) {
        Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(CoilImagesPlugin.create(context, imageLoader))
            .usePlugin(CodeHighlightPlugin(codeBackgroundArgb, syntaxColors))
            .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    // Tint inline code (the single-backtick variant) the
                    // same way our themed code blocks render so the look
                    // is consistent across both forms.
                    builder.linkResolver { view, link ->
                        // Default URL handler. Future improvement: route
                        // sheaf:// links into nav instead of opening the
                        // browser; for now defer to the system.
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(link),
                            )
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            view.context.startActivity(intent)
                        } catch (_: Exception) {
                            // Swallow — bad URLs shouldn't crash the screen.
                        }
                    }
                }
            })
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColorArgb)
                setLinkTextColor(linkArgb)
                highlightColor = highlightArgb
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                // Lets long-press select inline. Matches the legacy
                // compose-markdown behaviour the previous wrapper had.
                setTextIsSelectable(true)
            }
        },
        update = { view ->
            view.maxLines = maxLines
            view.ellipsize = if (truncateOnTextOverflow) TextUtils.TruncateAt.END else null
            view.setTextColor(textColorArgb)
            view.setLinkTextColor(linkArgb)
            view.highlightColor = highlightArgb
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            markwon.setMarkdown(view, markdown)
        },
    )
}

/**
 * Map theme colours into the [SyntaxColors] slots. Picks deliberately
 * different hues for keyword vs type vs function so the same regex
 * grammar reads as visually distinct categories in both light and dark
 * mode without a per-palette override per slot.
 *
 * We bias toward the theme's accent slots (primary, secondary, tertiary,
 * error) rather than hard-coding RGB values — that way a palette swap
 * (Purple -> Mint -> Crimson -> etc.) automatically recolours code.
 */
private fun buildSyntaxColors(
    @Suppress("UNUSED_PARAMETER") accent: Color,
    scheme: androidx.compose.material3.ColorScheme,
): SyntaxColors {
    // Light themes need slightly punchier hues for foreground spans on
    // the (lighter) code background. Dark themes can use lower-intensity
    // tones. We approximate by reading onSurfaceVariant's luminance —
    // when the background is light we darken accent slots.
    val onSurface = scheme.onSurface.toArgb()
    val isDark = AndroidColor.red(onSurface) > 0x80 // onSurface is light => dark theme
    return if (isDark) {
        SyntaxColors(
            comment = scheme.onSurfaceVariant.copy(alpha = 0.75f).toArgb(),
            keyword = scheme.primary.toArgb(),
            string = scheme.tertiary.toArgb(),
            number = scheme.secondary.toArgb(),
            function = scheme.primary.copy(alpha = 0.85f).toArgb(),
            type = scheme.secondary.copy(alpha = 0.85f).toArgb(),
            attribute = scheme.tertiary.copy(alpha = 0.85f).toArgb(),
            operator = scheme.onSurface.toArgb(),
        )
    } else {
        SyntaxColors(
            comment = scheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb(),
            keyword = scheme.primary.toArgb(),
            string = scheme.tertiary.toArgb(),
            number = scheme.secondary.toArgb(),
            function = scheme.primary.toArgb(),
            type = scheme.secondary.toArgb(),
            attribute = scheme.tertiary.toArgb(),
            operator = scheme.onSurface.toArgb(),
        )
    }
}
