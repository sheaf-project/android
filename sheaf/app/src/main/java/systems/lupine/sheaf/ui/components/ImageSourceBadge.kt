package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// Mirrors web's bio-editor.tsx badge. Hosted = the Sheaf instance is serving the
// image (resolves to /v1/files/<key>); external = arbitrary other URL the user
// linked to. The distinction matters because external links can rot / leak the
// reader's IP to a third-party host.
data class ImageReference(val alt: String, val url: String, val hosted: Boolean)

private val IMAGE_MD_REGEX = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")

fun extractImageReferences(markdown: String): List<ImageReference> {
    if (markdown.isEmpty()) return emptyList()
    return IMAGE_MD_REGEX.findAll(markdown).map { m ->
        val alt = m.groupValues[1]
        val url = m.groupValues[2]
        ImageReference(alt = alt, url = url, hosted = isHostedImageUrl(url))
    }.toList()
}

// Heuristic: anything that looks like a /v1/files/ reference is hosted by the
// Sheaf instance, regardless of whether it's relative or absolute. Everything
// else is treated as external. Matches web's `src.startsWith("/v1/files/")`
// check with a slight relaxation to also catch absolute URLs that go to the
// instance's API base.
fun isHostedImageUrl(url: String): Boolean = url.contains("/v1/files/")

@Composable
fun ImageSourceBadge(hosted: Boolean, modifier: Modifier = Modifier) {
    val bg = if (hosted) Color(0xFF22C55E).copy(alpha = 0.85f)
             else Color(0xFFEAB308).copy(alpha = 0.85f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            if (hosted) "hosted" else "external",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

// Compact panel listing each detected image reference with a thumbnail and a
// hosted/external badge. Renders nothing if no images are present, so it can
// be unconditionally inlined into compose screens.
@Composable
fun ImageReferencesPanel(markdown: String, modifier: Modifier = Modifier) {
    val refs = extractImageReferences(markdown)
    if (refs.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Images (${refs.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        refs.forEach { ref ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncImage(
                    model = ref.url,
                    contentDescription = ref.alt.ifBlank { "image" },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Text(
                    ref.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ImageSourceBadge(hosted = ref.hosted)
            }
        }
    }
}
