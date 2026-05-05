package systems.lupine.sheaf.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import coil.Coil
import dev.jeziellago.compose.markdowntext.MarkdownText

// MarkdownText wrapper that:
//  1. Always passes the app's singleton Coil ImageLoader so embedded images go
//     through our auth-aware OkHttp client and render with the Authorization
//     header attached. Without this, /v1/files/{key} image requests embedded
//     in member bios or journal entries 401 against the API.
//  2. Themes Markwon's code-block colours from MaterialTheme so dark mode
//     doesn't render code on a hardcoded light grey background.
@Composable
fun SheafMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    truncateOnTextOverflow: Boolean = false,
) {
    val imageLoader = Coil.imageLoader(LocalContext.current)
    val colors = MaterialTheme.colorScheme
    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        truncateOnTextOverflow = truncateOnTextOverflow,
        imageLoader = imageLoader,
        syntaxHighlightColor = colors.surfaceVariant,
        syntaxHighlightTextColor = colors.onSurfaceVariant,
    )
}
