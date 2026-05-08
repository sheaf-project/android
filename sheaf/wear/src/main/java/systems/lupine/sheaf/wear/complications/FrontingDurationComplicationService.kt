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
 * How long the oldest current fronter has been front. Updates naturally as
 * the day progresses so the watchface complication "ages" — useful glance
 * data ("oh, Alice's been front since this morning"). Ages off the watchface's
 * own clock; the watch redraws minute-by-minute.
 *
 * SHORT_TEXT: just the duration ("9h 28m").
 * LONG_TEXT: name + duration ("J 9h 28m").
 */
class FrontingDurationComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, name = "Alice", since = "9h 28m")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val rows = readFrontersSnapshot(this) ?: return NoDataComplicationData()
        if (rows.isEmpty()) return NoDataComplicationData()
        val oldest = rows.byOldestFirst().first()
        val ago = oldest.since.takeIf { it.isNotBlank() }?.let(::timeAgoOrNull)
            ?: return NoDataComplicationData()
        return build(request.complicationType, name = oldest.name, since = ago)
    }

    private fun build(type: ComplicationType, name: String, since: String): ComplicationData? {
        val tap = openAppPendingIntent(this, REQUEST_CODE)
        val description = PlainComplicationText.Builder("$name fronting for $since").build()
        // SHORT_TEXT slots clip ~7 chars; "just now" overflows to "just n…",
        // so collapse to "now" for the SHORT_TEXT path. LONG_TEXT has room
        // for the friendlier wording.
        val shortSince = if (since == "just now") "now" else since
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(shortSince).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder(name.take(7)).build())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder("$name $since").build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder("Fronting for").build())
                .setTapAction(tap)
                .build()

            else -> null
        }
    }

    private companion object {
        const val REQUEST_CODE = 1005
    }
}
