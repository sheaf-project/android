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
                requestTileUpdate()
            } catch (e: Exception) {
                error.value = e.message ?: "Failed to load"
            } finally {
                isLoading.value = false
            }
        }
    }

    suspend fun switchFront(memberIds: List<String>): Boolean {
        error.value = null
        return try {
            apiClient.createFront(memberIds)
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
        val names = frontingMembers.joinToString(", ") { it.displayNameOrName }.ifEmpty { null }
        context.getSharedPreferences("tile_data", Context.MODE_PRIVATE)
            .edit()
            .putString("fronting_names", names)
            .putString("fronting_started_at", oldestFront?.startedAt)
            .apply()
    }

    private fun requestTileUpdate() {
        try {
            androidx.wear.tiles.TileService.getUpdater(context)
                .requestUpdate(systems.lupine.sheaf.wear.tile.FrontingTileService::class.java)
        } catch (_: Exception) {}
    }
}
