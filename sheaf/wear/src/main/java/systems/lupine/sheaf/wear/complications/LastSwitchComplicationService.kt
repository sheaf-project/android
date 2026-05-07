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
        build(type, Duration.ofMinutes(134))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val sp = getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        val lastChangeMs = sp.getLong("last_front_change_at", 0L)
        if (lastChangeMs == 0L) return NoDataComplicationData()
        val d = Duration.between(Instant.ofEpochMilli(lastChangeMs), Instant.now())
        return build(request.complicationType, d)
    }

    private fun build(type: ComplicationType, d: Duration): ComplicationData? {
        val tap = openAppPendingIntent(this, REQUEST_CODE)
        // Two formattings: SHORT_TEXT collapses to "now" / "5m" / "1h 2m" so
        // it fits in the watchface's narrow text slot ("just now" was 8
        // chars and got clipped to "JUST N..."). LONG_TEXT keeps "Just now"
        // and special-cases the "ago" suffix off it so the line doesn't
        // read "Just now ago".
        val short = formatShort(d) ?: return NoDataComplicationData()
        val long = formatLong(d) ?: return NoDataComplicationData()
        val description = PlainComplicationText.Builder("Last switch $long").build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(short).build(),
                description,
            )
                .setTitle(PlainComplicationText.Builder("switch").build())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> {
                val text = if (long == "Just now") long else "$long ago"
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder(text).build(),
                    description,
                )
                    .setTitle(PlainComplicationText.Builder("Last switch").build())
                    .setTapAction(tap)
                    .build()
            }

            else -> null
        }
    }

    private fun formatShort(d: Duration): String? = runCatching {
        when {
            d.toMinutes() < 1 -> "now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m"
            d.toHours() < 24 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
            else -> "${d.toDays()}d"
        }
    }.getOrNull()

    private fun formatLong(d: Duration): String? = runCatching {
        when {
            d.toMinutes() < 1 -> "Just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m"
            d.toHours() < 24 -> "${d.toHours()}h ${d.toMinutes() % 60}m"
            else -> "${d.toDays()}d"
        }
    }.getOrNull()

    private companion object {
        const val REQUEST_CODE = 1006
    }
}
