package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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

// The instance's file CDN base (settings.s3_public_url, surfaced via the auth
// config's file_cdn_base and persisted in prefs). Provided once at the app root
// so the hosted/external classification can recognise CDN-served images without
// every caller threading it through. Null when the instance serves files from
// its own /v1/files/ path rather than a CDN.
val LocalFileCdnBase = staticCompositionLocalOf<String?> { null }

private val IMAGE_MD_REGEX = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")

fun extractImageReferences(markdown: String, cdnBase: String? = null): List<ImageReference> {
    if (markdown.isEmpty()) return emptyList()
    return IMAGE_MD_REGEX.findAll(markdown).map { m ->
        val alt = m.groupValues[1]
        val url = m.groupValues[2]
        ImageReference(alt = alt, url = url, hosted = isHostedImageUrl(url, cdnBase))
    }.toList()
}

// Hosted = the Sheaf instance is serving the image, either from its own
// /v1/files/ path or from the configured file CDN (s3_public_url). Everything
// else is external. Mirrors web's isHostedImage(src, cdnBase): a /v1/files/
// reference OR a URL under the CDN base. Without this, CDN-served images (the
// default on the hosted instance) were misclassified as external.
fun isHostedImageUrl(url: String, cdnBase: String? = null): Boolean =
    url.contains("/v1/files/") ||
        (!cdnBase.isNullOrBlank() && url.startsWith("${cdnBase.trimEnd('/')}/"))

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
fun ImageReferencesPanel(
    markdown: String,
    modifier: Modifier = Modifier,
    cdnBase: String? = LocalFileCdnBase.current,
) {
    val refs = extractImageReferences(markdown, cdnBase)
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
