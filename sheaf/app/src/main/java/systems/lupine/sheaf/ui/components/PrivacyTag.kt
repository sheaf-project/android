package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.ui.theme.LocalWarningColors
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Who can see a thing, as a small word beside it.
 *
 * A word rather than an icon, because a badge on its own says nothing for
 * private, and private is the answer people open a screen to confirm before
 * typing something sensitive into it.
 *
 * One tone per level, so the answer is readable at a glance: private is the
 * quiet default, the two levels that put something in front of somebody get a
 * colour, and public gets the loud one. Public borrows the same warning pair
 * the pending-delete badge uses, which is this app's "visible, or about to
 * be" colour and is defined per palette for light and dark. A level from a
 * newer server falls back to quiet rather than to nothing.
 */
@Composable
fun PrivacyTag(level: String, modifier: Modifier = Modifier) {
    val warning = LocalWarningColors.current
    val (container: Color, content: Color) = when (level) {
        "public" -> warning.container to warning.onContainer
        "friends" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            privacyLevelLabel(level),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The vocabulary, in one place so every surface says the same words. */
fun privacyLevelLabel(level: String): String = when (level) {
    "private" -> "Private"
    "friends" -> "Friends only"
    "public" -> "Public"
    else -> level.replaceFirstChar { it.uppercase() }
}

/**
 * The line under a privacy control when a raise is waiting out a System Safety
 * grace period: what it will become, and when.
 *
 * Renders nothing when nothing is staged, so a call site can drop it in
 * unconditionally.
 */
@Composable
fun StagedPrivacyNote(
    pendingPrivacy: String?,
    activatesAt: String?,
    modifier: Modifier = Modifier,
) {
    if (pendingPrivacy.isNullOrBlank() || activatesAt.isNullOrBlank()) return
    val zone = LocalDisplayTimeZone.current
    Text(
        "Staged: becomes ${privacyLevelLabel(pendingPrivacy)} on ${formatStagedDate(activatesAt, zone)}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val stagedDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")

private fun formatStagedDate(iso: String, zone: ZoneId): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime().format(stagedDateFormatter)
}.getOrDefault(iso)
