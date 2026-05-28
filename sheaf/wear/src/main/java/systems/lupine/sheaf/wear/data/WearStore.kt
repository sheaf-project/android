package systems.lupine.sheaf.wear.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WearStore(
    val apiClient: WearApiClient,
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val members       = MutableStateFlow<List<WearMember>>(emptyList())
    val currentFronts = MutableStateFlow<List<WearFront>>(emptyList())
    val recentFronts  = MutableStateFlow<List<WearFront>>(emptyList())
    val groups        = MutableStateFlow<List<WearGroup>>(emptyList())
    val isLoading     = MutableStateFlow(false)
    val error         = MutableStateFlow<String?>(null)
    // Tracked separately from the generic `error` because the recent-fronts
    // call is opt-out for the home path (its failure shouldn't take out the
    // rest of the load), but the history screen needs to distinguish "we
    // tried and it failed" from "no history yet".
    val recentFrontsError = MutableStateFlow<String?>(null)

    val frontingMembers: List<WearMember>
        get() {
            val ids = currentFronts.value.flatMap { it.memberIds }.toSet()
            return members.value.filter { it.id in ids }
        }

    val oldestFront: WearFront?
        get() = currentFronts.value.minByOrNull { it.startedAt ?: "" }

    fun loadAll() {
        scope.launch { refreshNow() }
    }

    /**
     * Body of [loadAll] as a suspending function. Callers that must keep
     * a short-lived process alive until the refresh finishes — the
     * data-layer listener handling a phone "front changed" nudge — await
     * this directly instead of firing into [scope] and racing service
     * teardown.
     */
    suspend fun refreshNow() {
        isLoading.value = true
        error.value = null
        recentFrontsError.value = null
        // Mirror in-memory loading state into the tile-data prefs so
        // tile services in their own process can branch "loading…" vs
        // "couldn't load" instead of falling through to "Members not
        // found" when the wear app simply hasn't synced yet.
        systems.lupine.sheaf.wear.complications.writeLoadStatus(
            context,
            systems.lupine.sheaf.wear.complications.WearLoadStatus.LOADING,
        )
        // Drain any locally-queued switches now we're presumably
        // online again. Each replay carries its original createdAt as
        // startedAt so the resulting front lands at the moment the
        // user actually pressed switch, not at the moment the watch
        // reconnected. Best-effort: a transient failure leaves the
        // row in place for the next refreshNow.
        for (q in WearSwitchQueue.snapshot(context)) {
            val iso = java.time.Instant.ofEpochMilli(q.createdAt).toString()
            runCatching { apiClient.createFront(q.memberIds, q.replaceFronts, iso) }
                .onSuccess { WearSwitchQueue.remove(context, q.uuid) }
        }
        try {
            members.value = apiClient.getMembers()
            currentFronts.value = apiClient.getCurrentFronts()
            groups.value = apiClient.getGroups()
            // Recent-fronts isn't critical for the home screen so its
            // failure shouldn't take out the rest of the load. Track
            // failure separately via recentFrontsError so the history
            // screen can distinguish "tried and failed" (show retry)
            // from "no history yet" (show empty message). Keeping the
            // previous list value on failure means a transient blip
            // doesn't wipe the rendered history mid-view.
            runCatching { apiClient.getRecentFronts() }
                .onSuccess { recentFronts.value = it; recentFrontsError.value = null }
                .onFailure { recentFrontsError.value = it.message ?: "Failed to load history" }
            cacheTileData()
            cacheTileAvatars()
            requestTileUpdate()
            systems.lupine.sheaf.wear.complications.writeLoadStatus(
                context,
                systems.lupine.sheaf.wear.complications.WearLoadStatus.OK,
            )
        } catch (e: Exception) {
            error.value = e.message ?: "Failed to load"
            systems.lupine.sheaf.wear.complications.writeLoadStatus(
                context,
                systems.lupine.sheaf.wear.complications.WearLoadStatus.FAILED,
            )
        } finally {
            isLoading.value = false
        }
    }

    private suspend fun cacheTileAvatars() {
        // Render avatars for the full member roster. Fronting-only tiles
        // could get away with a smaller set, but the member-watch tile
        // shows arbitrary members regardless of whether they're fronting,
        // so we cache the lot. Run on Dispatchers.IO since this can hit
        // the network for URL avatars.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            systems.lupine.sheaf.wear.tile.renderTileAvatars(context, members.value)
        }
    }

    suspend fun switchFront(memberIds: List<String>, replaceFronts: Boolean? = null): Boolean {
        error.value = null
        // Try the direct API path first. Common case; succeeds when the
        // watch has its own network.
        runCatching { apiClient.createFront(memberIds, replaceFronts) }
            .onSuccess {
                loadAll()
                return true
            }
        // Direct call failed (most often: watch is offline). Two
        // fallback rungs, in order: hand off to the phone via
        // DataLayer (the phone has a persistent queue + SyncWorker
        // and is more likely than the watch to reach the server), and
        // if even that can't be delivered, queue locally on the watch
        // for a retry from the next refreshNow. See [WearSwitchQueue]
        // for the race-avoidance reasoning — only one side ever owns
        // a given switch, so the front isn't double-created.
        val queued = WearQueuedSwitch.create(
            memberIds = memberIds,
            replaceFronts = replaceFronts ?: true,
        )
        if (!WearSwitchQueue.sendToPhone(context, queued)) {
            WearSwitchQueue.enqueue(context, queued)
        }
        // Report success either way: the user pressed switch, the
        // system has captured it, and it will land — surfacing a
        // transient "network failed" they can't act on would be
        // worse UX than the rare lost-on-floor case below.
        return true
    }

    suspend fun createMember(name: String, displayName: String?, pronouns: String?): WearMember {
        val member = apiClient.createMember(name, displayName, pronouns)
        members.value = members.value + member
        return member
    }

    fun clearData() {
        members.value = emptyList()
        currentFronts.value = emptyList()
        groups.value = emptyList()
        error.value = null
    }

    fun endFront(frontId: String) {
        scope.launch {
            try {
                apiClient.deleteFront(frontId)
                currentFronts.value = currentFronts.value.filter { it.id != frontId }
                cacheTileData()
                requestTileUpdate()
            } catch (e: Exception) {
                error.value = e.message ?: "Failed to end front"
            }
        }
    }

    private fun cacheTileData() {
        val members = frontingMembers
        val names = members.joinToString(", ") { it.displayNameOrName }.ifEmpty { null }

        // Build a JSON snapshot for complications to consume without spinning
        // up an OkHttp client of their own. One row per fronter with the
        // effective fronting-since (chain-aware via member_since when present,
        // else the front's started_at). Sort fields by id for stable diffing.
        val fronts = currentFronts.value
        val sinceByMember = fronts.flatMap { f ->
            f.memberIds.map { id -> id to (f.memberSince[id] ?: f.startedAt) }
        }.toMap()
        val frontersJson = members.joinToString(separator = ",", prefix = "[", postfix = "]") { m ->
            val since = sinceByMember[m.id] ?: ""
            "{\"id\":\"${jsonEscape(m.id)}\",\"name\":\"${jsonEscape(m.displayNameOrName)}\",\"since\":\"${jsonEscape(since)}\"}"
        }

        // Full members list for the per-member config activity. Subset of
        // WearMember (id, name, emoji) — anything else can be looked up by
        // id when needed.
        val allMembers = this.members.value
        val membersJson = allMembers.joinToString(separator = ",", prefix = "[", postfix = "]") { m ->
            val emoji = m.emoji?.takeIf { it.isNotBlank() }?.let { jsonEscape(it) } ?: ""
            "{\"id\":\"${jsonEscape(m.id)}\",\"name\":\"${jsonEscape(m.displayNameOrName)}\",\"emoji\":\"$emoji\"}"
        }

        // last_front_change_at advances only when the *set* of fronting member
        // ids changes, so the "Last switch" complication is decoupled from the
        // fronting-duration one (which uses started_at directly).
        //
        // On first sync (no cached signature yet) we derive the last-change
        // timestamp from the freshest startedAt across current fronts rather
        // than assuming "we just learned about this set means the set is
        // brand new". Without that, a watch that pairs into an established
        // long-running front would show "1m ago" right after sync and count
        // up from there, rather than reflecting the actual switch time.
        val newSetSig = members.map { it.id }.toSortedSet().joinToString(",")
        val sp = context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        val previousSig = sp.getString("front_set_sig", null)
        val previousLastChange = sp.getLong("last_front_change_at", 0L)
        val lastChange = when {
            previousSig == null -> deriveLastChangeFromFronts(fronts) ?: System.currentTimeMillis()
            previousSig != newSetSig -> System.currentTimeMillis()
            else -> previousLastChange
        }

        sp.edit()
            .putString("fronting_names", names)
            .putString("fronting_started_at", oldestFront?.startedAt)
            .putString("fronters", frontersJson)
            .putString("members_full", membersJson)
            .putString("front_set_sig", newSetSig)
            .putLong("last_front_change_at", lastChange)
            .apply()

        // Cache the recent-fronts list as a tile-readable snapshot. The
        // history viewer screen reads recentFronts directly from the
        // StateFlow, but tiles run cross-process so they consume the SP
        // snapshot. Convert started_at ISO strings to ms epoch up front
        // so the tile rendering doesn't repeat the parse on every refresh.
        val historyEntries = recentFronts.value.mapNotNull { f ->
            val ms = f.startedAt?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: return@mapNotNull null
            FrontHistoryEntry(
                timestamp = ms,
                memberIds = f.memberIds.sorted(),
                ongoing = f.endedAt.isNullOrBlank(),
            )
        }
        // Server returns newest-first; the rest of the codebase assumes
        // oldest-first ring order, so reverse before persisting. Cap to
        // the same MAX_HISTORY budget the client buffer used so the JSON
        // stays small. Skip the write when the entries are empty so a
        // transient API failure doesn't wipe a previously-good cache.
        if (historyEntries.isNotEmpty()) {
            writeFrontHistory(context, historyEntries.reversed().takeLast(MAX_HISTORY))
        }
    }

    private fun requestTileUpdate() {
        val updater = runCatching {
            androidx.wear.tiles.TileService.getUpdater(context)
        }.getOrNull() ?: return
        for (cls in tileServices) {
            runCatching { updater.requestUpdate(cls) }
        }
        // Complications managed in the same package; their update requests
        // share the same fire-and-forget shape: if the watch isn't paired
        // or the complications aren't currently in use, no harm done.
        systems.lupine.sheaf.wear.complications.requestAllComplicationUpdates(context)
    }

    private companion object {
        val tileServices = listOf(
            systems.lupine.sheaf.wear.tile.FrontingTileService::class.java,
            systems.lupine.sheaf.wear.tile.FrontingWithAvatarsTileService::class.java,
            systems.lupine.sheaf.wear.tile.FrontingAvatarsOnlyTileService::class.java,
            systems.lupine.sheaf.wear.tile.MemberFrontingTileService::class.java,
            systems.lupine.sheaf.wear.tile.QuickSwitchTileService::class.java,
            systems.lupine.sheaf.wear.tile.FrontHistoryTileService::class.java,
        )
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * Best-effort "when did this front composition last change?" using only
     * the data the API gave us. The newest startedAt across current fronts
     * is when the most recent member joined; for shrink-only changes
     * (member ended out, no new entry) it's still our best estimate without
     * a history endpoint.
     */
    private fun deriveLastChangeFromFronts(fronts: List<WearFront>): Long? =
        fronts.mapNotNull { f ->
            f.startedAt?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            }
        }.maxOrNull()
}
