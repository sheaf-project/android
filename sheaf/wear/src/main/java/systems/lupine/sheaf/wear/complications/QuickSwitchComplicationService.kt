package systems.lupine.sheaf.wear.complications

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import systems.lupine.sheaf.wear.R
import systems.lupine.sheaf.wear.presentation.NAV_SWITCH

/**
 * One-tap shortcut to the Switch Front screen, skipping the menu. Saves
 * three taps for users who switch fronts often.
 */
class QuickSwitchComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType)

    private fun build(type: ComplicationType): ComplicationData? {
        val tap = openRoutePendingIntent(this, REQUEST_CODE, NAV_SWITCH)
        val description = PlainComplicationText.Builder("Switch front").build()
        val icon = Icon.createWithResource(this, R.drawable.ic_complication_swap)
        return when (type) {
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                MonochromaticImage.Builder(image = icon).build(),
                description,
            ).setTapAction(tap).build()

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("Switch").build(),
                description,
            )
                .setMonochromaticImage(MonochromaticImage.Builder(image = icon).build())
                .setTapAction(tap)
                .build()

            else -> null
        }
    }

    private companion object {
        const val REQUEST_CODE = 1002
    }
}
