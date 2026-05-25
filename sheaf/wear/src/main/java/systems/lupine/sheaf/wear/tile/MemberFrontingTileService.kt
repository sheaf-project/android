package systems.lupine.sheaf.wear.tile

import android.content.ComponentName
import androidx.wear.protolayout.ActionBuilders
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
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Border
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
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
import systems.lupine.sheaf.wear.complications.readFrontersSnapshot
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.complications.timeAgoOrNull
import systems.lupine.sheaf.wear.data.WearAuthManager

/**
 * Member-watch tile. The user picks a set of members at tile-add time
 * via [MemberSelectorTileConfigActivity]; the tile then renders each
 * picked member's avatar with a fronting / not-fronting indicator. Layout
 * adapts to the on-page count: solo big-avatar layout up to a 4x2 compact
 * grid. Rosters larger than [TILE_PAGE_SIZE] paginate behind a tap-to-
 * advance page chip pinned to the bottom of the tile.
 *
 * Tap-to-configure is the entry path: before the user has saved a
 * selection the tile is a single chip prompting them to pick. After
 * configuration, tap opens the wear app (no in-tile reconfigure; users
 * remove + re-add the tile to change the set).
 */
class MemberFrontingTileService : TileService() {

    override fun onTileResourcesRequest(
        requestParams: ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val builder = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
        for (id in loadTileMemberSet(this, requestParams.tileId)) {
            tileAvatarResource(this, id)?.let { res ->
                builder.addIdToImageMapping(tileAvatarResourceId(id), res)
            }
        }
        return immediateTileFuture(builder.build())
    }

    override fun onTileRequest(requestParams: TileRequest): ListenableFuture<Tile> {
        val tileId = requestParams.tileId
        val authenticated = WearAuthManager(applicationContext).isAuthenticated
        val memberIds = loadTileMemberSet(this, tileId)
        val members = resolveMembers(memberIds)

        val status = systems.lupine.sheaf.wear.complications.readLoadStatus(this)
        val layout = when {
            !authenticated -> messageLayout("Open Sheaf on phone to sign in")
            // Tile is configured but we have no member roster locally yet
            // (or we couldn't fetch one). Beat the "Members not found"
            // copy that read like the system genuinely had no members.
            members.isEmpty() && memberIds.isNotEmpty() && (
                status == systems.lupine.sheaf.wear.complications.WearLoadStatus.LOADING ||
                status == systems.lupine.sheaf.wear.complications.WearLoadStatus.NEVER
            ) -> messageLayout("Loading…")
            members.isEmpty() && memberIds.isNotEmpty() &&
                status == systems.lupine.sheaf.wear.complications.WearLoadStatus.FAILED ->
                messageLayout("Couldn't load — open app to retry")
            memberIds.isEmpty() -> configurePromptLayout(tileId)
            members.isEmpty()   -> messageLayout("Members not found")
            else                -> watchListLayout(tileId, members)
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

    private fun resolveMembers(memberIds: List<String>): List<TrackedMember> {
        if (memberIds.isEmpty()) return emptyList()
        val members = readMembersSnapshot(this).orEmpty().associateBy { it.id }
        val fronters = readFrontersSnapshot(this).orEmpty().associateBy { it.id }
        return memberIds.mapNotNull { id ->
            val m = members[id] ?: return@mapNotNull null
            val fr = fronters[id]
            TrackedMember(
                row = m,
                isFronting = fr != null,
                durationLabel = fr?.since?.takeIf { it.isNotBlank() }?.let(::timeAgoOrNull),
            )
        }
    }

    private fun watchListLayout(tileId: Int, members: List<TrackedMember>): Layout {
        // Slice the picked roster into screen-sized pages. The user taps a
        // page chip to advance; render-time modulo keeps the stored index
        // in range even after the roster (and so the page count) changes.
        val pages = members.chunked(TILE_PAGE_SIZE)
        val pageCount = pages.size
        val page = loadTilePage(this, tileId).mod(pageCount)
        val visible = pages[page]

        val grid: Box = when (visible.size) {
            1 -> soloLayout(visible[0])
            2, 3 -> rowLayout(visible)
            4 -> gridLayout(visible, columns = 2, avatarDp = 48f)
            5, 6 -> gridLayout(visible, columns = 3, avatarDp = 40f)
            else -> gridLayout(visible, columns = 4, avatarDp = 34f)
        }

        // Tap on a configured tile opens the wear app; a long-press on the
        // tile-carousel surface opens the system tile editor where the user
        // can remove and re-add the tile to change the member set.
        val root = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(Modifiers.Builder().setClickable(openAppClickable()).build())
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(grid)
                    .build()
            )

        // More members than fit one screen: pin a tap-to-advance page chip
        // to the bottom. Its own clickable shadows the tile-wide open-app
        // tap within the chip's bounds.
        if (pageCount > 1) {
            root.addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(pageChip(tileId, page, pageCount))
                    .build()
            )
        }

        return Layout.Builder().setRoot(root.build()).build()
    }

    private fun soloLayout(m: TrackedMember): Box {
        val tail = when {
            m.isFronting && !m.durationLabel.isNullOrBlank() -> "Fronting · ${m.durationLabel}"
            m.isFronting                                    -> "Fronting"
            else                                            -> "Off"
        }
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Column.Builder()
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(avatarImage(m, 64f))
                    .addContent(Spacer.Builder().setHeight(dp(4f)).build())
                    .addContent(Text.Builder().setText(m.row.name).setFontStyle(NAME_STYLE).build())
                    .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                    .addContent(Text.Builder().setText(tail).setFontStyle(STATUS_STYLE).build())
                    .build()
            )
            .build()
    }

    private fun rowLayout(members: List<TrackedMember>): Box {
        val avatarDp = if (members.size == 2) 56f else 44f
        val row = Row.Builder().setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        members.forEachIndexed { i, m ->
            if (i > 0) row.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            row.addContent(memberCellWithName(m, avatarDp))
        }
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(row.build())
            .build()
    }

    private fun gridLayout(
        members: List<TrackedMember>,
        columns: Int,
        avatarDp: Float,
    ): Box {
        val rows = members.chunked(columns)
        val column = Column.Builder().setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        rows.forEachIndexed { i, rowMembers ->
            if (i > 0) column.addContent(Spacer.Builder().setHeight(dp(GAP_DP)).build())
            column.addContent(gridRow(rowMembers, avatarDp))
        }
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    private fun gridRow(rowMembers: List<TrackedMember>, avatarDp: Float): Row {
        val r = Row.Builder().setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        rowMembers.forEachIndexed { i, m ->
            if (i > 0) r.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            r.addContent(memberCellCompact(m, avatarDp))
        }
        return r.build()
    }

    private fun pageChip(tileId: Int, page: Int, pageCount: Int): Box =
        Box.Builder()
            .setWidth(dp(64f))
            .setHeight(dp(22f))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(pageClickable(tileId))
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0x33FFFFFF))
                            .setCorner(Corner.Builder().setRadius(dp(11f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText("${page + 1}/$pageCount  ›")
                    .setFontStyle(STATUS_STYLE)
                    .build()
            )
            .build()

    private fun pageClickable(tileId: Int): Clickable = Clickable.Builder()
        .setId("page_$tileId")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(TilePageAdvanceActivity::class.java.name)
                        .setPackageName(packageName)
                        .addKeyToExtraMapping(
                            EXTRA_TILE_ID,
                            ActionBuilders.AndroidIntExtra.Builder().setValue(tileId).build(),
                        )
                        .addKeyToExtraMapping(
                            EXTRA_TILE_SERVICE_CLASS,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(MemberFrontingTileService::class.java.name)
                                .build(),
                        )
                        .build()
                )
                .build()
        )
        .build()

    private fun memberCellWithName(m: TrackedMember, avatarDp: Float): Column =
        Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(avatarImage(m, avatarDp))
            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
            .addContent(
                Text.Builder()
                    .setText(m.row.name.take(8))
                    .setFontStyle(NAME_SMALL_STYLE)
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(if (m.isFronting) "✓" else "✗")
                    .setFontStyle(STATUS_STYLE)
                    .build()
            )
            .build()

    private fun memberCellCompact(m: TrackedMember, avatarDp: Float): Column =
        Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(avatarImage(m, avatarDp))
            .addContent(
                Text.Builder()
                    .setText(if (m.isFronting) "✓" else "✗")
                    .setFontStyle(STATUS_STYLE)
                    .build()
            )
            .build()

    private fun avatarImage(m: TrackedMember, sizeDp: Float): LayoutElement {
        val image = Image.Builder()
            .setResourceId(tileAvatarResourceId(m.row.id))
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .build()
        // Fronting members get a thin accent ring around the (already
        // circular) avatar, so the active set reads at a glance rather
        // than only from the ✓/✗ glyph below. The Background's circular
        // corner is what makes the Border render as a ring not a square.
        if (!m.isFronting) return image
        return Box.Builder()
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setCorner(Corner.Builder().setRadius(dp(sizeDp / 2f)).build())
                            .build()
                    )
                    .setBorder(
                        Border.Builder()
                            .setWidth(dp(RING_WIDTH_DP))
                            .setColor(argb(RING_ACTIVE))
                            .build()
                    )
                    .build()
            )
            .addContent(image)
            .build()
    }

    private fun configurePromptLayout(tileId: Int): Layout {
        val tap = pickerClickable(tileId)
        return Layout.Builder()
            .setRoot(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .setModifiers(Modifiers.Builder().setClickable(tap).build())
                    .addContent(
                        Column.Builder()
                            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                            .addContent(Text.Builder().setText("Tap to pick").setFontStyle(NAME_STYLE).build())
                            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                            .addContent(Text.Builder().setText("members").setFontStyle(NAME_STYLE).build())
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

    private fun openAppClickable(): Clickable = Clickable.Builder()
        .setId("open_app")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(MainActivity::class.java.name)
                        .setPackageName(packageName)
                        .build()
                )
                .build()
        )
        .build()

    private fun pickerClickable(tileId: Int): Clickable = Clickable.Builder()
        .setId("pick_$tileId")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(MemberSelectorTileConfigActivity::class.java.name)
                        .setPackageName(packageName)
                        .addKeyToExtraMapping(
                            EXTRA_TILE_ID,
                            ActionBuilders.AndroidIntExtra.Builder().setValue(tileId).build(),
                        )
                        .addKeyToExtraMapping(
                            EXTRA_TILE_SERVICE_CLASS,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(MemberFrontingTileService::class.java.name)
                                .build(),
                        )
                        .build()
                )
                .build()
        )
        .build()

    private companion object {
        const val GAP_DP = 4f
        const val RING_WIDTH_DP = 3f
        // Green "active" accent matching the quick-switch selection ring,
        // so a ringed avatar means the same thing across both tiles.
        const val RING_ACTIVE = 0xFF8FE0B7.toInt()

        val NAME_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(14f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()

        val NAME_SMALL_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(10f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()

        val STATUS_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(11f))
            .setColor(argb(0xFFAFA9EC.toInt()))
            .build()
    }
}

private data class TrackedMember(
    val row: MemberRow,
    val isFronting: Boolean,
    val durationLabel: String?,
)

@Suppress("unused") // kept here for symmetry with complication patterns;
                    // unused locally since clickable carries the activity ref.
private fun MemberFrontingTileService.componentName(): ComponentName =
    ComponentName(this, MemberFrontingTileService::class.java)
