package systems.lupine.sheaf.wear.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WearStore(
    val apiClient: WearApiClient,
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    val members       = MutableStateFlow<List<WearMember>>(emptyList())
    val currentFronts = MutableStateFlow<List<WearFront>>(emptyList())
    val recentFronts  = MutableStateFlow<List<WearFront>>(emptyList())
    val groups        = MutableStateFlow<List<WearGroup>>(emptyList())
    // Quick-switch ranking (pins first, then recency-weighted score) used to
    // order the switch picker. Best-effort: stays empty on failure and the
    // picker falls back to the plain member order.
    val topFronters   = MutableStateFlow<List<WearMember>>(emptyList())
    val isLoading     = MutableStateFlow(false)
    val error         = MutableStateFlow<String?>(null)
    // Tracked separately from the generic `error` because the recent-fronts
    // call is opt-out for the home path (its failure shouldn't take out the
    // rest of the load), but the history screen needs to distinguish "we
    // tried and it failed" from "no history yet".
    val recentFrontsError = MutableStateFlow<String?>(null)

    // Serialises the offline-switch queue drain. loadAll()/refreshNow() run from
    // many triggers (onResume, nav, manual refresh, post-switch, phone nudge);
    // without this, two concurrent drains could both submit the same queued row
    // before either removed it, double-creating a front.
    private val queueDrainMutex = Mutex()

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
        queueDrainMutex.withLock {
            for (q in WearSwitchQueue.snapshot(context)) {
                val iso = java.time.Instant.ofEpochMilli(q.createdAt).toString()
                runCatching { apiClient.createFront(q.memberIds, q.replaceFronts, iso) }
                    .onSuccess { WearSwitchQueue.remove(context, q.uuid) }
                    .onFailure { e ->
                        // Drop a row the server will never accept so it can't
                        // replay forever; keep transient/offline failures queued.
                        if (e is WearApiException && isPermanentSwitchError(e.code)) {
                            WearSwitchQueue.remove(context, q.uuid)
                        }
                    }
            }
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
            // Quick-switch ranking, best-effort: a failure just leaves the
            // switch picker in plain member order.
            runCatching { apiClient.getTopFronters() }
                .onSuccess { topFronters.value = it }
            cacheTileData()
            cacheTileAvatars()
            requestTileUpdate()
            systems.lupine.sheaf.wear.complications.writeLoadStatus(
                context,
                systems.lupine.sheaf.wear.complications.WearLoadStatus.OK,
            )
        } catch (e: Exception) {
            android.util.Log.e("WearStore", "refreshNow failed", e)
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
            .onFailure { e ->
                // A permanent client error (deleted member, bad payload) will
                // never succeed on replay, so don't queue it or report success:
                // surface it instead of silently dropping the switch on the floor.
                if (e is WearApiException && isPermanentSwitchError(e.code)) {
                    error.value = "Couldn't switch front (error ${e.code})"
                    return false
                }
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
        // Fronter snapshot plus the keys derived from it (names, set
        // signature, last-change timestamp) go through the shared writer so
        // the phone-push fast path and the full network refresh stay in
        // lockstep on the set-change logic. One row per fronter with the
        // effective fronting-since (chain-aware via member_since when present,
        // else the front's started_at).
        val sinceByMember = currentFronts.value.flatMap { f ->
            f.memberIds.map { id -> id to (f.memberSince[id] ?: f.startedAt) }
        }.toMap()
        val fronters = frontingMembers.map { m ->
            systems.lupine.sheaf.wear.complications.FronterRow(
                id = m.id,
                name = m.displayNameOrName,
                since = sinceByMember[m.id] ?: "",
            )
        }
        // Seed the first-sync last-change from the newest front *entry* start
        // (not the chain-aware since), preserving the original "Last switch"
        // behaviour for coalesce-enabled systems.
        val firstSyncSeed = currentFronts.value.mapNotNull { f ->
            f.startedAt?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            }
        }.maxOrNull()
        writeFronterTileData(context, fronters, firstSyncSeed)

        // Full members list for the per-member config activity. Subset of
        // WearMember (id, name, emoji) — anything else can be looked up by
        // id when needed. Not time-critical, so it's not part of the push
        // fast path; only the full refresh repopulates it.
        val membersJson = systems.lupine.sheaf.wear.complications.encodeMembersJson(
            this.members.value.map { m ->
                systems.lupine.sheaf.wear.complications.MemberRow(
                    id = m.id,
                    name = m.displayNameOrName,
                    emoji = m.emoji?.takeIf { it.isNotBlank() } ?: "",
                )
            }
        )
        context.getSharedPreferences("tile_data", Context.MODE_PRIVATE).edit()
            .putString("members_full", membersJson)
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
        requestAllTileUpdates(context)
        // Complications managed in the same package; their update requests
        // share the same fire-and-forget shape: if the watch isn't paired
        // or the complications aren't currently in use, no harm done.
        systems.lupine.sheaf.wear.complications.requestAllComplicationUpdates(context)
    }
}

// ── Shared tile_data writers ────────────────────────────────────────────────
//
// Top-level so both the full network refresh ([WearStore.cacheTileData]) and
// the phone-push fast path ([WearDataLayerService]) write the fronter snapshot
// and fire tile updates through exactly the same code. The push path applies
// the fronting state the phone hands it directly, so watchface complications
// refresh even when the watch itself can't reach the backend at that moment.

private val wearTileServices = listOf(
    systems.lupine.sheaf.wear.tile.FrontingTileService::class.java,
    systems.lupine.sheaf.wear.tile.FrontingWithAvatarsTileService::class.java,
    systems.lupine.sheaf.wear.tile.FrontingAvatarsOnlyTileService::class.java,
    systems.lupine.sheaf.wear.tile.MemberFrontingTileService::class.java,
    systems.lupine.sheaf.wear.tile.QuickSwitchTileService::class.java,
    systems.lupine.sheaf.wear.tile.FrontHistoryTileService::class.java,
)

/**
 * Writes the fronter-derived tile_data keys (fronters snapshot, joined names,
 * effective started_at, set signature, last-change timestamp) from an explicit
 * fronter list, independent of in-memory state or the network.
 *
 * last_front_change_at advances only when the *set* of fronting member ids
 * changes, so the "Last switch" complication is decoupled from the
 * fronting-duration one. On first sync (no cached signature yet) it seeds from
 * the newest fronting-since rather than "now", so a watch pairing into an
 * established front reflects the real switch time instead of "just now".
 */
internal fun writeFronterTileData(
    context: Context,
    fronters: List<systems.lupine.sheaf.wear.complications.FronterRow>,
    // Newest front-entry start (epoch ms) used only to seed last_front_change_at
    // on the very first sync, so a watch pairing into an established front
    // doesn't show "just now". The full refresh passes the actual front
    // started_at here; the phone-push fast path leaves it null and falls back
    // to the newest fronting-since, which is close enough for that cold case.
    firstSyncSeedMs: Long? = null,
) {
    val names = fronters.joinToString(", ") { it.name }.ifEmpty { null }
    val startedAt = fronters.mapNotNull { it.since.takeIf { s -> s.isNotBlank() } }.minOrNull()
    val newSetSig = fronters.map { it.id }.toSortedSet().joinToString(",")
    val sp = context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
    val previousSig = sp.getString("front_set_sig", null)
    val previousLastChange = sp.getLong("last_front_change_at", 0L)
    val lastChange = when {
        previousSig == null ->
            firstSyncSeedMs ?: newestSinceEpoch(fronters) ?: System.currentTimeMillis()
        previousSig != newSetSig -> System.currentTimeMillis()
        else -> previousLastChange
    }
    sp.edit()
        .putString("fronting_names", names)
        .putString("fronting_started_at", startedAt)
        .putString("fronters", systems.lupine.sheaf.wear.complications.encodeFrontersJson(fronters))
        .putString("front_set_sig", newSetSig)
        .putLong("last_front_change_at", lastChange)
        .apply()
}

/** Fire a tile-refresh request at every tile we ship; no-op if unpaired/unused. */
internal fun requestAllTileUpdates(context: Context) {
    val updater = runCatching {
        androidx.wear.tiles.TileService.getUpdater(context)
    }.getOrNull() ?: return
    for (cls in wearTileServices) {
        runCatching { updater.requestUpdate(cls) }
    }
}

/** Newest fronting-since across the snapshot in epoch ms, or null. */
private fun newestSinceEpoch(
    fronters: List<systems.lupine.sheaf.wear.complications.FronterRow>,
): Long? =
    fronters.mapNotNull { r ->
        r.since.takeIf { it.isNotBlank() }?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
        }
    }.maxOrNull()

/**
 * A 4xx (other than auth 401/403, timeout 408, rate-limit 429) means the request
 * itself is bad and replaying it won't help: drop the queued switch instead of
 * retrying it forever. Misclassify the other way and the user's switch is
 * silently deleted and never lands.
 *
 * Top-level (not a WearStore member) so it is testable without a Context, and it
 * mirrors the phone's SyncWorker isPermanentHttpFailure verdict for verdict.
 */
internal fun isPermanentSwitchError(code: Int): Boolean =
    code in 400..499 && code !in setOf(401, 403, 408, 429)
