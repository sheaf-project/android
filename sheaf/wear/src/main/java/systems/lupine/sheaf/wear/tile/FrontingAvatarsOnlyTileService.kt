package systems.lupine.sheaf.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import systems.lupine.sheaf.wear.complications.MemberRow
import systems.lupine.sheaf.wear.data.WearAuthManager

/**
 * Avatars-only fronting tile: glanceable visual summary of who's currently
 * fronting, no text. One avatar gets the centre stage; multiple fronters
 * arrange in a row (up to 3) or 2x2 grid (4). Anything beyond 4 truncates
 * to 3 avatars + "+N" badge, which is what the watchface size budget can
 * actually render legibly.
 *
 * Layout sizing is deliberately conservative: rendering 4 large avatars on
 * a Pixel Watch 3 round screen leaves enough side margin that the avatars
 * don't get clipped by the round bezel.
 */
class FrontingAvatarsOnlyTileService : TileService() {

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
        for (m in orderedFronters(this)) {
            tileAvatarResource(this, m.id)?.let { res ->
                builder.addIdToImageMapping(tileAvatarResourceId(m.id), res)
            }
        }
        return immediateTileFuture(builder.build())
    }

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> {
        val authenticated = WearAuthManager(applicationContext).isAuthenticated
        val members = orderedFronters(this)

        val layout = when {
            !authenticated  -> messageLayout("Open Sheaf on phone")
            members.isEmpty() -> messageLayout("No one fronting")
            else            -> avatarsLayout(members)
        }

        return immediateTileFuture(
            Tile.Builder()
                .setResourcesVersion(currentResourcesVersion(this))
                .setFreshnessIntervalMillis(15 * 60 * 1000L)
                .setTileTimeline(
                    Timeline.Builder()
                        .addTimelineEntry(
                            TimelineEntry.Builder()
                                .setLayout(layout)
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }

    private fun avatarsLayout(members: List<MemberRow>): Layout {
        val (visible, overflow) = if (members.size <= MAX_VISIBLE) members to 0
        else members.take(MAX_VISIBLE - 1) to (members.size - (MAX_VISIBLE - 1))

        val root = when {
            visible.size == 1 -> singleAvatar(visible[0])
            visible.size <= 3 -> avatarRow(visible, overflow)
            else              -> avatarGrid(visible, overflow)
        }

        return Layout.Builder().setRoot(root).build()
    }

    private fun singleAvatar(m: MemberRow): Box = Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        .addContent(
            Image.Builder()
                .setResourceId(tileAvatarResourceId(m.id))
                .setWidth(dp(SOLO_DP))
                .setHeight(dp(SOLO_DP))
                .build()
        )
        .build()

    private fun avatarRow(visible: List<MemberRow>, overflow: Int): Box {
        val row = Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        visible.forEachIndexed { i, m ->
            if (i > 0) row.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            row.addContent(
                Image.Builder()
                    .setResourceId(tileAvatarResourceId(m.id))
                    .setWidth(dp(ROW_DP))
                    .setHeight(dp(ROW_DP))
                    .build()
            )
        }
        if (overflow > 0) {
            row.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            row.addContent(overflowBadge(overflow))
        }
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(row.build())
            .build()
    }

    private fun avatarGrid(visible: List<MemberRow>, overflow: Int): Box {
        // 2x2 grid: pair the visible items into two rows of two.
        val first = visible.take(2)
        val second = visible.drop(2).take(2)

        val column = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(gridRow(first, overflowSlot = if (second.size < 2) overflow else 0))
            .addContent(Spacer.Builder().setHeight(dp(GAP_DP)).build())
            .addContent(gridRow(second, overflowSlot = overflow))
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    private fun gridRow(rowMembers: List<MemberRow>, overflowSlot: Int): Row {
        val r = Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        rowMembers.forEachIndexed { i, m ->
            if (i > 0) r.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            r.addContent(
                Image.Builder()
                    .setResourceId(tileAvatarResourceId(m.id))
                    .setWidth(dp(GRID_DP))
                    .setHeight(dp(GRID_DP))
                    .build()
            )
        }
        if (overflowSlot > 0 && rowMembers.size < 2) {
            r.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            r.addContent(overflowBadge(overflowSlot))
        }
        return r.build()
    }

    private fun overflowBadge(n: Int): Text = Text.Builder()
        .setText("+$n")
        .setFontStyle(BADGE_STYLE)
        .build()

    private fun messageLayout(text: String): Layout = Layout.Builder()
        .setRoot(
            Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder()
                        .setText(text)
                        .setFontStyle(BADGE_STYLE)
                        .setMaxLines(3)
                        .build()
                )
                .build()
        )
        .build()

    private companion object {
        const val MAX_VISIBLE = 4
        const val SOLO_DP = 80f
        const val ROW_DP = 48f
        const val GRID_DP = 44f
        const val GAP_DP = 6f

        val BADGE_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(14f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()
    }
}
