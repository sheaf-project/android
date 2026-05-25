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
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_TOP
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Border
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import systems.lupine.sheaf.wear.complications.MemberRow
import systems.lupine.sheaf.wear.complications.readMembersSnapshot
import systems.lupine.sheaf.wear.data.WearAuthManager

/**
 * Quick-switch tile, mini-SwitchScreen edition. The user pre-picks a
 * roster of candidate members at tile-add time; the tile then renders
 * each as a tappable button. Tapping toggles a member into / out of
 * the next-switch selection (visual ✓ below the avatar). A small
 * "End existing" toggle and a bottom Switch button mirror the in-app
 * SwitchScreen, keeping the mental model identical.
 *
 * State across renders lives in SharedPreferences keyed by tile id, so
 * the toggle remembers its position even though the tile itself is
 * stateless. Selection clears after each commit so the next interaction
 * starts from "nothing selected".
 */
class QuickSwitchTileService : TileService() {

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
        val configured = loadTileMemberSet(this, tileId)
        val members = resolveMembers(configured)
        val selected = loadQuickSwitchSelected(this, tileId)
        val endExisting = loadQuickSwitchEndExisting(this, tileId)

        val status = systems.lupine.sheaf.wear.complications.readLoadStatus(this)
        val layout = when {
            !authenticated -> messageLayout("Open Sheaf on phone to sign in")
            // Authenticated but the wear app hasn't synced yet (members
            // snapshot empty). Distinguish loading from a prior failure
            // so users know whether to wait or take action.
            members.isEmpty() && (
                status == systems.lupine.sheaf.wear.complications.WearLoadStatus.LOADING ||
                status == systems.lupine.sheaf.wear.complications.WearLoadStatus.NEVER
            ) -> messageLayout("Loading…")
            members.isEmpty() &&
                status == systems.lupine.sheaf.wear.complications.WearLoadStatus.FAILED ->
                messageLayout("Couldn't load — open app to retry")
            configured.isEmpty() -> configurePromptLayout(tileId)
            members.isEmpty()  -> messageLayout("Members not found")
            else               -> switchPanelLayout(tileId, members, selected, endExisting)
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

    private fun resolveMembers(memberIds: List<String>): List<MemberRow> {
        if (memberIds.isEmpty()) return emptyList()
        val byId = readMembersSnapshot(this).orEmpty().associateBy { it.id }
        return memberIds.mapNotNull { byId[it] }
    }

    private fun switchPanelLayout(
        tileId: Int,
        members: List<MemberRow>,
        selected: Set<String>,
        endExisting: Boolean,
    ): Layout {
        // Page the candidate roster so a long member list stays tappable
        // rather than silently truncating past the grid budget. Selection
        // is keyed by member id, so a ✓ persists as the user pages back
        // and forth, and the Switch button counts the selection across all
        // pages. Tile budget on a Pixel Watch round screen is ~160x160dp
        // inside the bezel; the bottom-aligned controls stack takes ~50dp,
        // leaving most of the vertical space for the avatar buttons.
        val pages = members.chunked(TILE_PAGE_SIZE)
        val pageCount = pages.size
        val page = loadTilePage(this, tileId).mod(pageCount)
        val avatarsBlock = avatarsLayoutFor(tileId, pages[page], selected)

        // Stack: avatars centered top, controls pinned to the bottom of
        // the round visible area via VERTICAL_ALIGN_BOTTOM on a separate
        // Box that fills the full tile.
        val root = Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_TOP)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(avatarsBlock)
                    .build()
            )
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        Column.Builder()
                            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                            .addContent(endExistingChip(tileId, endExisting))
                            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                            .addContent(switchButton(tileId, selected.size))
                            .build()
                    )
                    .build()
            )

        // Page chip pinned top-centre, clear of the bottom controls.
        if (pageCount > 1) {
            root.addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setVerticalAlignment(VERTICAL_ALIGN_TOP)
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(pageChip(tileId, page, pageCount))
                    .build()
            )
        }

        return Layout.Builder().setRoot(root.build()).build()
    }

    private fun avatarsLayoutFor(
        tileId: Int,
        members: List<MemberRow>,
        selected: Set<String>,
    ): LayoutElement = when (members.size) {
        1 -> avatarRow(tileId, members, selected, avatarDp = 72f)
        2 -> avatarRow(tileId, members, selected, avatarDp = 60f)
        3 -> avatarRow(tileId, members, selected, avatarDp = 48f)
        4 -> avatarGrid(tileId, members, selected, columns = 2, avatarDp = 48f)
        5, 6 -> avatarGrid(tileId, members, selected, columns = 3, avatarDp = 40f)
        // members is already a single page slice (<= TILE_PAGE_SIZE).
        else -> avatarGrid(tileId, members, selected, columns = 4, avatarDp = 34f)
    }

    private fun avatarRow(
        tileId: Int,
        members: List<MemberRow>,
        selected: Set<String>,
        avatarDp: Float,
    ): Row {
        val row = Row.Builder().setVerticalAlignment(VERTICAL_ALIGN_CENTER)
        members.forEachIndexed { i, m ->
            if (i > 0) row.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
            row.addContent(memberCell(tileId, m, m.id in selected, avatarDp))
        }
        return row.build()
    }

    private fun avatarGrid(
        tileId: Int,
        members: List<MemberRow>,
        selected: Set<String>,
        columns: Int,
        avatarDp: Float,
    ): Column {
        val rows = members.chunked(columns)
        val column = Column.Builder().setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        rows.forEachIndexed { i, rowMembers ->
            if (i > 0) column.addContent(Spacer.Builder().setHeight(dp(GAP_DP)).build())
            val r = Row.Builder().setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            rowMembers.forEachIndexed { j, m ->
                if (j > 0) r.addContent(Spacer.Builder().setWidth(dp(GAP_DP)).build())
                r.addContent(memberCell(tileId, m, m.id in selected, avatarDp))
            }
            column.addContent(r.build())
        }
        return column.build()
    }

    private fun memberCell(
        tileId: Int,
        m: MemberRow,
        isSelected: Boolean,
        sizeDp: Float,
    ): Column = Column.Builder()
        .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
        .addContent(
            Box.Builder()
                .setWidth(dp(sizeDp))
                .setHeight(dp(sizeDp))
                .setModifiers(
                    Modifiers.Builder()
                        .setClickable(toggleMemberClickable(tileId, m.id))
                        .apply {
                            // Selected members get a thin accent ring around
                            // the (already circular) avatar so the next-switch
                            // set reads at a glance, not just from the ✓ below.
                            // The Background's circular corner makes the
                            // Border render as a ring rather than a square.
                            if (isSelected) {
                                setBackground(
                                    Background.Builder()
                                        .setCorner(
                                            Corner.Builder().setRadius(dp(sizeDp / 2f)).build()
                                        )
                                        .build()
                                )
                                setBorder(
                                    Border.Builder()
                                        .setWidth(dp(RING_WIDTH_DP))
                                        .setColor(argb(RING_SELECTED))
                                        .build()
                                )
                            }
                        }
                        .build()
                )
                .addContent(
                    Image.Builder()
                        .setResourceId(tileAvatarResourceId(m.id))
                        .setWidth(dp(sizeDp))
                        .setHeight(dp(sizeDp))
                        .build()
                )
                .build()
        )
        .addContent(
            Text.Builder()
                .setText(if (isSelected) "✓" else " ")
                .setFontStyle(if (isSelected) MARK_ON_STYLE else MARK_OFF_STYLE)
                .build()
        )
        .build()

    private fun endExistingChip(tileId: Int, on: Boolean): Box {
        // Using a checkbox-style glyph + label so the toggle reads as
        // interactive at a glance. Box is given an explicit min size so
        // the clickable hit target isn't tiny — wrap_content sized boxes
        // around small text are functionally untappable.
        val glyph = if (on) "☑" else "☐"
        return Box.Builder()
            .setWidth(dp(140f))
            .setHeight(dp(22f))
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(toggleEndExistingClickable(tileId))
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText("$glyph End existing")
                    .setFontStyle(TOGGLE_STYLE)
                    .build()
            )
            .build()
    }

    private fun switchButton(tileId: Int, selectedCount: Int): Box {
        val label = if (selectedCount == 0) "Switch" else "Switch ($selectedCount)"
        return Box.Builder()
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(commitClickable(tileId))
                    .setBackground(
                        Background.Builder()
                            .setColor(
                                if (selectedCount == 0) argb(0xFF44475A.toInt())
                                else argb(0xFF8FE0B7.toInt()),
                            )
                            .setCorner(Corner.Builder().setRadius(dp(16f)).build())
                            .build()
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(14f))
                            .setEnd(dp(14f))
                            .setTop(dp(6f))
                            .setBottom(dp(6f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        if (selectedCount == 0) BUTTON_DISABLED_STYLE
                        else BUTTON_ENABLED_STYLE
                    )
                    .build()
            )
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
                            .addContent(Text.Builder().setText("Tap to pick").setFontStyle(TOGGLE_STYLE).build())
                            .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                            .addContent(Text.Builder().setText("members").setFontStyle(TOGGLE_STYLE).build())
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
                        .setFontStyle(TOGGLE_STYLE)
                        .setMaxLines(3)
                        .build()
                )
                .build()
        )
        .build()

    private fun toggleMemberClickable(tileId: Int, memberId: String): Clickable =
        Clickable.Builder()
            .setId("toggle_${tileId}_$memberId")
            .setOnClick(trampolineLaunch(tileId, MODE_TOGGLE_MEMBER, memberId))
            // Wear OS accessibility minimum is 48dp; without this, taps on
            // avatars below that size get rejected or routed to neighbouring
            // clickables in the layout. Visible avatar size stays whatever
            // the layout chose; the touch target is bumped up to spec.
            .setMinimumClickableWidth(dp(MIN_TOUCH_DP))
            .setMinimumClickableHeight(dp(MIN_TOUCH_DP))
            .build()

    private fun toggleEndExistingClickable(tileId: Int): Clickable =
        Clickable.Builder()
            .setId("toggle_end_$tileId")
            .setOnClick(trampolineLaunch(tileId, MODE_TOGGLE_END_EXISTING, null))
            .setMinimumClickableWidth(dp(MIN_TOUCH_DP))
            .setMinimumClickableHeight(dp(MIN_TOUCH_DP))
            .build()

    private fun commitClickable(tileId: Int): Clickable =
        Clickable.Builder()
            .setId("commit_$tileId")
            .setOnClick(trampolineLaunch(tileId, MODE_COMMIT_SWITCH, null))
            .setMinimumClickableWidth(dp(MIN_TOUCH_DP))
            .setMinimumClickableHeight(dp(MIN_TOUCH_DP))
            .build()

    private fun trampolineLaunch(
        tileId: Int,
        mode: String,
        memberId: String?,
    ): ActionBuilders.LaunchAction {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setClassName(QuickSwitchTrampolineActivity::class.java.name)
            .setPackageName(packageName)
            .addKeyToExtraMapping(
                EXTRA_TILE_ID,
                ActionBuilders.AndroidIntExtra.Builder().setValue(tileId).build(),
            )
            .addKeyToExtraMapping(
                EXTRA_MODE,
                ActionBuilders.AndroidStringExtra.Builder().setValue(mode).build(),
            )
        if (memberId != null) {
            activity.addKeyToExtraMapping(
                EXTRA_MEMBER_ID,
                ActionBuilders.AndroidStringExtra.Builder().setValue(memberId).build(),
            )
        }
        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(activity.build())
            .build()
    }

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
                                .setValue(QuickSwitchTileService::class.java.name)
                                .build(),
                        )
                        .build()
                )
                .build()
        )
        .build()

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
                    .setFontStyle(TOGGLE_STYLE)
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
                                .setValue(QuickSwitchTileService::class.java.name)
                                .build(),
                        )
                        .build()
                )
                .build()
        )
        .setMinimumClickableWidth(dp(MIN_TOUCH_DP))
        .setMinimumClickableHeight(dp(MIN_TOUCH_DP))
        .build()

    private companion object {
        const val GAP_DP = 4f
        const val MIN_TOUCH_DP = 48f
        const val RING_WIDTH_DP = 3f
        // Same green accent as the ✓ mark and the Switch button, so
        // "this member is in the next switch" reads consistently.
        const val RING_SELECTED = 0xFF8FE0B7.toInt()

        val MARK_ON_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(11f))
            .setColor(argb(0xFF8FE0B7.toInt()))
            .build()

        val MARK_OFF_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(11f))
            .setColor(argb(0x40FFFFFF.toInt()))
            .build()

        val TOGGLE_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(11f))
            .setColor(argb(0xFFFFFFFF.toInt()))
            .build()

        val BUTTON_ENABLED_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(13f))
            .setColor(argb(0xFF1B2A20.toInt()))
            .build()

        val BUTTON_DISABLED_STYLE: FontStyle = FontStyle.Builder()
            .setSize(sp(13f))
            .setColor(argb(0xFFAAAAAA.toInt()))
            .build()
    }
}
