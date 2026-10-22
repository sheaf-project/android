package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import systems.lupine.sheaf.wear.data.WearMember

/**
 * A member's avatar: their image when there is one, otherwise their emoji or
 * initials on their colour.
 *
 * The URL is resolved by the Coil interceptor installed in
 * [systems.lupine.sheaf.wear.WearApp], since the API hands back
 * server-relative paths.
 *
 * An image that fails anyway falls back to the circle rather than leaving a
 * hole. That is what made the unresolved-URL bug so quiet: a member with an
 * image avatar rendered as nothing at all, on a watch face where an empty slot
 * next to a name reads as a layout fault rather than a missing picture.
 */
@Composable
fun MemberAvatar(
    member: WearMember,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val color = member.color?.let(::parseHexColor)
        ?: MaterialTheme.colors.primary
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White

    // Keyed on the URL so a member whose avatar changes gets a fresh attempt
    // rather than inheriting the last one's failure.
    var failed by remember(member.avatarUrl) { mutableStateOf(false) }

    if (!member.avatarUrl.isNullOrBlank() && !failed) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = member.displayNameOrName,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            onError = { failed = true },
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            // Prefer member emoji over initials when set, since on a watch
            // a single glyph reads at a glance better than two stacked letters.
            Text(
                text = member.emoji?.takeIf { it.isNotBlank() } ?: member.initials,
                style = MaterialTheme.typography.body2,
                color = onColor,
            )
        }
    }
}

private fun parseHexColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
}.getOrNull()

private fun Color.luminance(): Float {
    // sRGB relative luminance, sufficient for picking black vs white text.
    val r = red.linearize()
    val g = green.linearize()
    val b = blue.linearize()
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun Float.linearize(): Float =
    if (this <= 0.03928f) this / 12.92f else Math.pow(((this + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
