package systems.lupine.sheaf.wear.complications

import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Duration
import java.time.Instant

/**
 * How long since the *set of fronting members* last changed. Distinct from
 * the fronting-duration complication: the duration one tracks the oldest
 * fronter's run (which doesn't reset when a co-fronter joins / leaves);
 * this one tracks "how long since anything moved" (which does).
 *
 * Use case: "we haven't swapped in a while, time to check in?" vs
 * fronting-duration's "X has been driving for 9h."
 */
class LastSwitchComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, "2h 14m")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val sp = getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        val lastChangeMs = sp.getLong("last_front_change_at", 0L)
        if (lastChangeMs == 0L) return NoDataComplicationData()
        val since = Instant.ofEpochMilli(lastChangeMs)
        val ago = formatDuration(Duration.between(since, Instant.now())) ?: return NoDataComplicationData()
        return build(request.complicationType, ago)
    }

    private fun build(type: ComplicationType, ago: String): ComplicationData? {
        val tap = openAppPendingIntent(this, REQUEST_CODE)
        val description = PlainComplicationText.Builder("Last switch $ago ago").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(ago).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder("switch").build())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder("$ago ago").build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder("Last switch").build())
                .setTapAction(tap)
                .build()

            else -> null
        }
    }

    private fun formatDuration(d: Duration): String? = runCatching {
        when {
            d.toMinutes() < 1 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m"
            d.toHours() < 24 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
            else -> "${d.toDays()}d"
        }
    }.getOrNull()

    private companion object {
        const val REQUEST_CODE = 1006
    }
}
