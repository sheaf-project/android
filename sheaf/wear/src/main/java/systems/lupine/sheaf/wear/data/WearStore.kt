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
    val groups        = MutableStateFlow<List<WearGroup>>(emptyList())
    val isLoading     = MutableStateFlow(false)
    val error         = MutableStateFlow<String?>(null)

    val frontingMembers: List<WearMember>
        get() {
            val ids = currentFronts.value.flatMap { it.memberIds }.toSet()
            return members.value.filter { it.id in ids }
        }

    val oldestFront: WearFront?
        get() = currentFronts.value.minByOrNull { it.startedAt ?: "" }

    fun loadAll() {
        scope.launch {
            isLoading.value = true
            error.value = null
            try {
                members.value = apiClient.getMembers()
                currentFronts.value = apiClient.getCurrentFronts()
                groups.value = apiClient.getGroups()
                cacheTileData()
                cacheTileAvatars()
                requestTileUpdate()
            } catch (e: Exception) {
                error.value = e.message ?: "Failed to load"
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun cacheTileAvatars() {
        // Render only fronting members' avatars to keep the on-disk cache
        // small. The avatar-bearing tiles only display fronters, so any
        // wider rendering would be wasted IO. Run on Dispatchers.IO since
        // this can hit the network for URL avatars.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            systems.lupine.sheaf.wear.tile.renderTileAvatars(context, frontingMembers)
        }
    }

    suspend fun switchFront(memberIds: List<String>, replaceFronts: Boolean? = null): Boolean {
        error.value = null
        return try {
            apiClient.createFront(memberIds, replaceFronts)
            loadAll()
            true
        } catch (e: Exception) {
            error.value = e.message ?: "Failed to switch front"
            false
        }
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
        val newSetSig = members.map { it.id }.toSortedSet().joinToString(",")
        val sp = context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
        val previousSig = sp.getString("front_set_sig", null)
        val lastChange = if (previousSig != newSetSig) {
            System.currentTimeMillis()
        } else {
            sp.getLong("last_front_change_at", 0L)
        }

        sp.edit()
            .putString("fronting_names", names)
            .putString("fronting_started_at", oldestFront?.startedAt)
            .putString("fronters", frontersJson)
            .putString("members_full", membersJson)
            .putString("front_set_sig", newSetSig)
            .putLong("last_front_change_at", lastChange)
            .apply()
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
        )
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
