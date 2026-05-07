package systems.lupine.sheaf.wear.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Same fronter list as the oldest-first variant but ordered with the most
 * recently-joined fronter first, so a long member list keeps the freshest
 * information visible when LONG_TEXT truncates.
 */
class FrontersNewestFirstComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildFrontersComplication(type, listOf("Zeyra", "J"), tap = openAppPendingIntent(this, PREVIEW_REQUEST))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val rows = readFrontersSnapshot(this) ?: return NoDataComplicationData()
        val ordered = rows.byNewestFirst().map { it.name }
        return buildFrontersComplication(
            request.complicationType,
            ordered,
            tap = openAppPendingIntent(this, REQUEST_CODE),
        )
    }

    private companion object {
        const val REQUEST_CODE = 1004
        const val PREVIEW_REQUEST = 1104
    }
}
