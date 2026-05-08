package systems.lupine.sheaf.wear.tile

import android.content.Context
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
import systems.lupine.sheaf.wear.complications.readFrontersSnapshot
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.data.WearAuthManager

/**
 * Fronting tile variant: row of fronter avatars across the top, names
 * underneath. Up to [MAX_AVATARS] avatars render at full size; overflow
 * collapses into a "+N" badge so the row never wraps.
 */
class FrontingWithAvatarsTileService : TileService() {

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(currentResourcesVersion(this))
        for (id in frontingMemberIds()) {
            tileAvatarResource(this, id)?.let { res ->
                builder.addIdToImageMapping(tileAvatarResourceId(id), res)
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
            else            -> avatarsAndNamesLayout(members)
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

    private fun frontingMemberIds(): List<String> = orderedFronters(this).map { it.id }

    private fun avatarsAndNamesLayout(members: List<MemberRow>): Layout {
        val visible = members.take(MAX_AVATARS)
        val overflow = members.size - visible.size

        val avatarRow = Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)

        visible.forEachIndexed { i, m ->
            if (i > 0) avatarRow.addContent(Spacer.Builder().setWidth(dp(4f)).build())
            avatarRow.addContent(
                Image.Builder()
                    .setResourceId(tileAvatarResourceId(m.id))
                    .setWidth(dp(AVATAR_DP))
                    .setHeight(dp(AVATAR_DP))
                    .build()
            )
        }
        if (overflow > 0) {
            avatarRow.addContent(Spacer.Builder().setWidth(dp(4f)).build())
            avatarRow.addContent(
                Text.Builder()
                    .setText("+$overflow")
                    .setFontStyle(LABEL_STYLE)
                    .build()
            )
        }

        val nameSummary = members.joinToString(", ") { it.name }

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
                                    .setText("Fronting")
                                    .setFontStyle(LABEL_STYLE)
                                    .build()
                            )
                            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                            .addContent(avatarRow.build())
                            .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                            .addContent(
                                Text.Builder()
                                    .setText(nameSummary)
                                    .setFontStyle(NAME_STYLE)
                                    .setMaxLines(2)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

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
                        .setFontStyle(NAME_STYLE)
                        .setMaxLines(3)
                        .build()
                )
                .build()
        )
        .build()

    private companion object {
        const val MAX_AVATARS = 4
        const val AVATAR_DP = 32f

        val LABEL_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(11f))
            .setColor(argb(0xFFAFA9EC.toInt()))
            .build()

        val NAME_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(13f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()
    }
}

/**
 * Returns fronters in the same order the names tile already uses (whatever
 * `members_full` lists, intersected with the live fronters snapshot). The
 * shared snapshot lives in `tile_data` SharedPreferences; we read it through
 * the same helpers complications use so a single refresh feeds everything.
 */
internal fun orderedFronters(context: Context): List<MemberRow> {
    val fronters = readFrontersSnapshot(context).orEmpty()
    if (fronters.isEmpty()) return emptyList()
    val members = readMembersSnapshot(context).orEmpty()
    val byId = members.associateBy { it.id }
    return fronters.mapNotNull { byId[it.id] }
}

/**
 * Resources version doubles as a cache key for the tile resource graph.
 * Bumping it forces the tile renderer to re-fetch the avatar PNG bytes
 * from this service. We tie it to `last_front_change_at` so the resource
 * graph rotates with the fronter set rather than drifting forever.
 */
internal fun currentResourcesVersion(context: Context): String {
    val sp = context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
    return sp.getLong("last_front_change_at", 0L).toString()
}

