package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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

@Composable
fun MemberAvatar(
    member: WearMember,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val color = member.color?.let(::parseHexColor)
        ?: MaterialTheme.colors.primary
    val onColor = if (color.luminance() > 0.5f) Color.Black else Color.White

    if (!member.avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = member.displayNameOrName,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
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
