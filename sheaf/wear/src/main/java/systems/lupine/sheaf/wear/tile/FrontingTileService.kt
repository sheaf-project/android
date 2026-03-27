package systems.lupine.sheaf.wear.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import systems.lupine.sheaf.wear.data.WearAuthManager
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class FrontingTileService : TileService() {

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> {
        val prefs = getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        val names = prefs.getString("fronting_names", null)
        val authenticated = WearAuthManager(applicationContext).isAuthenticated

        val displayText = when {
            !authenticated        -> "Open Sheaf on phone"
            names.isNullOrBlank() -> "No one fronting"
            else                  -> names
        }

        return immediateFuture(
            Tile.Builder()
                .setResourcesVersion("1")
                .setFreshnessIntervalMillis(15 * 60 * 1000L)
                .setTileTimeline(
                    Timeline.Builder()
                        .addTimelineEntry(
                            TimelineEntry.Builder()
                                .setLayout(buildLayout(displayText))
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }

    private fun buildLayout(text: String): Layout {
        val labelStyle = FontStyle.Builder()
            .setSize(sp(11f))
            .setColor(argb(0xFFAFA9EC.toInt()))
            .build()

        val valueStyle = FontStyle.Builder()
            .setSize(sp(15f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()

        return Layout.Builder()
            .setRoot(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Column.Builder()
                            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                            .addContent(
                                Text.Builder()
                                    .setText("Currently Fronting")
                                    .setFontStyle(labelStyle)
                                    .build()
                            )
                            .addContent(
                                Text.Builder()
                                    .setText(text)
                                    .setFontStyle(valueStyle)
                                    .setMaxLines(3)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

private class ImmediateFuture<T>(private val value: T) : ListenableFuture<T> {
    override fun addListener(r: Runnable, e: Executor) = e.execute(r)
    override fun isDone() = true
    override fun isCancelled() = false
    override fun cancel(b: Boolean) = false
    override fun get(): T = value
    override fun get(t: Long, u: TimeUnit): T = value
}

private fun <T> immediateFuture(value: T): ListenableFuture<T> = ImmediateFuture(value)
