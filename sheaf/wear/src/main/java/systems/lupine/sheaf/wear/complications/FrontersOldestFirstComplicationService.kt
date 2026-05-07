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
        buildFrontersComplication(type, listOf("J", "Zeyra"), tap = openAppPendingIntent(this, PREVIEW_REQUEST))

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
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            PlainComplicationText.Builder(names.size.toString()).build(),
            description,
        )
            .setTitle(PlainComplicationText.Builder("front").build())
            .setTapAction(tap)
            .build()

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

// LONG_TEXT typically gets ~20-30 visible chars depending on watchface; aim
// short so we don't get clipped. The "+N" tail accommodates the overflow.
private const val LONG_TEXT_BUDGET = 24
