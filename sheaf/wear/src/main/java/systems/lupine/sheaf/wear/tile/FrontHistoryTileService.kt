package systems.lupine.sheaf.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import systems.lupine.sheaf.wear.MainActivity
import systems.lupine.sheaf.wear.complications.MemberRow
import systems.lupine.sheaf.wear.complications.WearLoadStatus
import systems.lupine.sheaf.wear.complications.readLoadStatus
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.data.WearAuthManager
import systems.lupine.sheaf.wear.data.readFrontHistory
import systems.lupine.sheaf.wear.presentation.NAV_HISTORY
import java.time.Duration
import java.time.Instant

/**
 * Recent-switches timeline tile. Shows the last few entries from the
 * client-side history ring buffer in newest-first order: the avatars
 * of the fronting set at that point + a relative timestamp.
 *
 * Tap opens the front history viewer screen via MainActivity's
 * EXTRA_INITIAL_ROUTE deep-link.
 */
class FrontHistoryTileService : TileService() {

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
        // Resources for the tile cover whichever members appear in the
        // recent history. Cap at the visible window to keep the bundle
        // small; far-back entries don't render anyway.
        val needed = readFrontHistory(this)
            .takeLast(MAX_VISIBLE)
            .flatMap { it.memberIds }
            .distinct()
        for (id in needed) {
            tileAvatarResource(this, id)?.let { res ->
                builder.addIdToImageMapping(tileAvatarResourceId(id), res)
            }
        }
        return immediateTileFuture(builder.build())
    }

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> {
        val authenticated = WearAuthManager(applicationContext).isAuthenticated
        // Newest first; ring buffer is appended chronologically.
        val history = readFrontHistory(this).reversed().take(MAX_VISIBLE)
        val byId = readMembersSnapshot(this).orEmpty().associateBy { it.id }

        val status = readLoadStatus(this)
        val layout = when {
            !authenticated -> messageLayout("Open Sheaf on phone to sign in")
            history.isEmpty() && (status == WearLoadStatus.LOADING || status == WearLoadStatus.NEVER) ->
                messageLayout("Loading…")
            history.isEmpty() && status == WearLoadStatus.FAILED ->
                messageLayout("Couldn't load — open app to retry")
            history.isEmpty() -> messageLayout("No history yet")
            else -> historyLayout(history, byId)
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

    private fun historyLayout(
        entries: List<systems.lupine.sheaf.wear.data.FrontHistoryEntry>,
        byId: Map<String, MemberRow>,
    ): Layout {
        val tap = openHistoryClickable()
        val column = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_START)
            .setModifiers(Modifiers.Builder().setClickable(tap).build())
        entries.forEachIndexed { i, entry ->
            if (i > 0) column.addContent(Spacer.Builder().setHeight(dp(4f)).build())
            column.addContent(historyRow(entry, byId))
        }

        return Layout.Builder()
            .setRoot(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(column.build())
                    .build()
            )
            .build()
    }

    private fun historyRow(
        entry: systems.lupine.sheaf.wear.data.FrontHistoryEntry,
        byId: Map<String, MemberRow>,
    ): Row {
        val members = entry.memberIds.mapNotNull { byId[it] }
        val ago = timeAgoFromMillis(entry.timestamp).let { if (entry.ongoing) "$it+" else it }
        val visibleAvatars = members.take(MAX_AVATARS_PER_ROW)
        val overflowCount = members.size - visibleAvatars.size

        val row = Row.Builder().setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        visibleAvatars.forEachIndexed { i, m ->
            if (i > 0) row.addContent(Spacer.Builder().setWidth(dp(2f)).build())
            row.addContent(
                Image.Builder()
                    .setResourceId(tileAvatarResourceId(m.id))
                    .setWidth(dp(AVATAR_DP))
                    .setHeight(dp(AVATAR_DP))
                    .build()
            )
        }
        if (overflowCount > 0) {
            row.addContent(Spacer.Builder().setWidth(dp(2f)).build())
            row.addContent(
                Text.Builder()
                    .setText("+$overflowCount")
                    .setFontStyle(SECONDARY_STYLE)
                    .build()
            )
        }
        row.addContent(Spacer.Builder().setWidth(dp(6f)).build())
        row.addContent(
            Text.Builder()
                .setText(ago)
                .setFontStyle(PRIMARY_STYLE)
                .build()
        )
        return row.build()
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
                        .setFontStyle(PRIMARY_STYLE)
                        .setMaxLines(2)
                        .build()
                )
                .build()
        )
        .build()

    private fun openHistoryClickable(): Clickable = Clickable.Builder()
        .setId("open_history")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(MainActivity::class.java.name)
                        .setPackageName(packageName)
                        .addKeyToExtraMapping(
                            systems.lupine.sheaf.wear.complications.EXTRA_INITIAL_ROUTE,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(NAV_HISTORY)
                                .build(),
                        )
                        .build()
                )
                .build()
        )
        .build()

    private companion object {
        const val MAX_VISIBLE = 4
        const val MAX_AVATARS_PER_ROW = 3
        const val AVATAR_DP = 22f

        val PRIMARY_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(12f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()

        val SECONDARY_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(10f))
            .setColor(argb(0xFFAFA9EC.toInt()))
            .build()
    }
}

private fun timeAgoFromMillis(ms: Long): String = runCatching {
    val d = Duration.between(Instant.ofEpochMilli(ms), Instant.now())
    when {
        d.toMinutes() < 1 -> "now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m"
        d.toHours() < 24 -> "${d.toHours()}h"
        else -> "${d.toDays()}d"
    }
}.getOrDefault("?")
