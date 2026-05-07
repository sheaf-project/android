package systems.lupine.sheaf.wear.complications

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import systems.lupine.sheaf.wear.R

/**
 * Plain shortcut complication: a Sheaf logo that opens the app on tap.
 * Useful next to other shortcuts on a Tile-based watchface.
 */
class OpenAppComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType)

    private fun build(type: ComplicationType): ComplicationData? {
        val tap = openAppPendingIntent(this, REQUEST_CODE)
        val description = PlainComplicationText.Builder("Open Sheaf").build()
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                MonochromaticImage.Builder(
                    image = android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher),
                ).build(),
                description,
            ).setTapAction(tap).build()

            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(
                    image = android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher),
                    type = SmallImageType.PHOTO,
                ).build(),
                description,
            ).setTapAction(tap).build()

            else -> null
        }
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
