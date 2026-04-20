package systems.lupine.sheaf.data.db

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import systems.lupine.sheaf.data.model.FrontRead
import systems.lupine.sheaf.data.model.GroupRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.model.SystemRead
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_MEMBERS = "members"
private const val KEY_SYSTEM = "system"
private const val KEY_FRONTS = "current_fronts"
private const val KEY_GROUPS = "groups"
private const val KEY_HISTORY = "history"

@Singleton
class LocalCache @Inject constructor(
    private val dao: CacheDao,
    private val moshi: Moshi,
) {
    private val memberListType = Types.newParameterizedType(List::class.java, MemberRead::class.java)
    private val frontListType  = Types.newParameterizedType(List::class.java, FrontRead::class.java)
    private val groupListType  = Types.newParameterizedType(List::class.java, GroupRead::class.java)

    private val memberListAdapter by lazy { moshi.adapter<List<MemberRead>>(memberListType) }
    private val systemAdapter      by lazy { moshi.adapter(SystemRead::class.java) }
    private val frontListAdapter   by lazy { moshi.adapter<List<FrontRead>>(frontListType) }
    private val groupListAdapter   by lazy { moshi.adapter<List<GroupRead>>(groupListType) }

    suspend fun saveMembers(members: List<MemberRead>) =
        dao.put(CacheEntry(KEY_MEMBERS, memberListAdapter.toJson(members)))

    suspend fun getMembers(): List<MemberRead>? =
        dao.get(KEY_MEMBERS)?.let { runCatching { memberListAdapter.fromJson(it.json) }.getOrNull() }

    suspend fun getMember(id: String): MemberRead? = getMembers()?.find { it.id == id }

    suspend fun saveSystem(system: SystemRead) =
        dao.put(CacheEntry(KEY_SYSTEM, systemAdapter.toJson(system)))

    suspend fun getSystem(): SystemRead? =
        dao.get(KEY_SYSTEM)?.let { runCatching { systemAdapter.fromJson(it.json) }.getOrNull() }

    suspend fun saveFronts(fronts: List<FrontRead>) =
        dao.put(CacheEntry(KEY_FRONTS, frontListAdapter.toJson(fronts)))

    suspend fun getFronts(): List<FrontRead>? =
        dao.get(KEY_FRONTS)?.let { runCatching { frontListAdapter.fromJson(it.json) }.getOrNull() }

    suspend fun saveGroups(groups: List<GroupRead>) =
        dao.put(CacheEntry(KEY_GROUPS, groupListAdapter.toJson(groups)))

    suspend fun getGroups(): List<GroupRead>? =
        dao.get(KEY_GROUPS)?.let { runCatching { groupListAdapter.fromJson(it.json) }.getOrNull() }

    suspend fun saveHistory(fronts: List<FrontRead>) =
        dao.put(CacheEntry(KEY_HISTORY, frontListAdapter.toJson(fronts)))

    suspend fun getHistory(): List<FrontRead>? =
        dao.get(KEY_HISTORY)?.let { runCatching { frontListAdapter.fromJson(it.json) }.getOrNull() }
}
