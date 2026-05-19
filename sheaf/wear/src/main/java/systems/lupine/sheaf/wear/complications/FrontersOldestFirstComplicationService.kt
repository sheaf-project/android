package systems.lupine.sheaf.wear.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Currently fronting members, oldest-fronting (most senior) listed first.
 * SHORT_TEXT shows just a count; LONG_TEXT shows names truncated to fit
 * with a "+N" overflow tail. Tap opens the home screen.
 */
class FrontersOldestFirstComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildFrontersComplication(type, listOf("Alice", "Bob"), tap = openAppPendingIntent(this, PREVIEW_REQUEST))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val rows = readFrontersSnapshot(this) ?: return NoDataComplicationData()
        val ordered = rows.byOldestFirst().map { it.name }
        return buildFrontersComplication(
            request.complicationType,
            ordered,
            tap = openAppPendingIntent(this, REQUEST_CODE),
        )
    }

    private companion object {
        const val REQUEST_CODE = 1003
        const val PREVIEW_REQUEST = 1103
    }
}

internal fun SuspendingComplicationDataSourceService.buildFrontersComplication(
    type: ComplicationType,
    names: List<String>,
    tap: android.app.PendingIntent,
): ComplicationData? {
    val description = PlainComplicationText.Builder("Currently fronting").build()
    return when (type) {
        ComplicationType.SHORT_TEXT -> {
            // Watchfaces render SHORT_TEXT in narrow slots — typically 6-7
            // chars before the layout clips. Show the priority fronter's
            // name truncated to fit alongside a "+N" overflow so the
            // ordering choice (oldest- vs newest-first) is meaningful.
            ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(fitFrontersShortText(names)).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder("front").build())
                .setTapAction(tap)
                .build()
        }

        ComplicationType.LONG_TEXT -> {
            val text = if (names.isEmpty()) "No one fronting" else fitNames(names, LONG_TEXT_BUDGET)
            LongTextComplicationData.Builder(
                PlainComplicationText.Builder(text).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder("Fronting").build())
                .setTapAction(tap)
                .build()
        }

        else -> null
    }
}

/**
 * Build a SHORT_TEXT-shaped fronters string within [SHORT_TEXT_BUDGET]:
 * one priority name plus a "+N" tail when there are more fronters. The
 * primary name is truncated as needed so the suffix always remains visible
 * (otherwise the user can't tell there are more fronters at all). Pure
 * function; tested at the unit level rather than through the complication
 * service boundary.
 */
internal fun fitFrontersShortText(names: List<String>): String {
    // "None" rather than a dash: on a narrow SHORT_TEXT slot a lone "—"
    // reads as an empty body next to the boldly-rendered title, making a
    // genuine no-fronters refresh indistinguishable from a stale/never-
    // loaded complication. A real word makes the empty state legible.
    if (names.isEmpty()) return "None"
    val primary = names.first()
    if (names.size == 1) return primary.take(SHORT_TEXT_BUDGET)
    val suffix = " +${names.size - 1}"
    val room = (SHORT_TEXT_BUDGET - suffix.length).coerceAtLeast(1)
    return primary.take(room) + suffix
}

// LONG_TEXT typically gets ~20-30 visible chars depending on watchface; aim
// short so we don't get clipped. The "+N" tail accommodates the overflow.
private const val LONG_TEXT_BUDGET = 24

// SHORT_TEXT slots typically fit ~7-8 chars across watchfaces; we go with
// 8 so two short-name fronters render cleanly (e.g. "Alice +1") at the
// cost of occasional clipping on the narrowest faces. If that bites in
// the field, expose this as a wear setting.
private const val SHORT_TEXT_BUDGET = 8
