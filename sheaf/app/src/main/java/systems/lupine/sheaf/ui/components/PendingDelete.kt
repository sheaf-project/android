package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.ui.theme.LocalWarningColors
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * System Safety queues destructive actions behind a grace period instead of
 * doing them immediately, so an entity can be alive on screen and already
 * scheduled for deletion. These helpers are the shared vocabulary for saying so
 * consistently wherever such a thing is listed.
 */

/** Parse a `pending_delete_at` / `finalize_after` timestamp, null-safe. */
fun parseFinalizeAt(iso: String?): OffsetDateTime? =
    if (iso.isNullOrBlank()) null
    else try {
        OffsetDateTime.parse(iso)
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * How long until [target], phrased for a badge: "in 18h", "in 3 days".
 *
 * Hours below a day, days above it: a grace period is configured in days, and
 * "in 47 hours" is harder to act on than "in 2 days".
 *
 * Rounds **down** throughout. This is a deadline for undoing something
 * destructive, so the two rounding errors are not equal: telling someone they
 * have 2 days when 25 hours remain can cost them the window, while telling them
 * 1 day when 25 hours remain only makes them act sooner. Below an hour there is
 * no floor left to give, so it says so in words rather than showing "in 0h".
 *
 * Already-elapsed windows read as "any moment" rather than a negative, since the
 * sweep that finalises them runs on its own schedule and a row can briefly
 * outlive its own deadline.
 */
fun formatFinalizeCountdown(target: OffsetDateTime): String {
    val duration = Duration.between(OffsetDateTime.now(), target)
    if (duration.isNegative || duration.isZero) return "any moment"
    val hours = duration.toHours()
    if (hours < 1) return "in under an hour"
    if (hours < 24) return "in ${hours}h"
    val days = duration.toDays()
    return if (days == 1L) "in 1 day" else "in $days days"
}

/** Opacity for a row whose entity is pending deletion, mirroring web. */
const val PENDING_DELETE_ALPHA = 0.6f

/**
 * Badge for an entity awaiting a queued delete: "Deletes in 18h".
 *
 * Renders nothing when [pendingDeleteAt] is null, so it can be dropped
 * unconditionally into any row. Deliberately not clickable: these sit inside
 * cards that are themselves clickable, and a tap target inside a tap target is
 * a coin toss. Cancelling lives where it already did, in Settings > Safety,
 * which the Home banner deep-links to.
 */
@Composable
fun PendingDeleteBadge(
    pendingDeleteAt: String?,
    modifier: Modifier = Modifier,
) {
    val target = parseFinalizeAt(pendingDeleteAt) ?: return
    val warning = LocalWarningColors.current
    val countdown = formatFinalizeCountdown(target)
    Surface(
        color = warning.container,
        contentColor = warning.onContainer,
        shape = RoundedCornerShape(50),
        // The icon is decorative and the text already says everything; merge
        // the whole badge into one announcement instead of two fragments.
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Pending delete, finalises $countdown"
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = " Deletes $countdown",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
