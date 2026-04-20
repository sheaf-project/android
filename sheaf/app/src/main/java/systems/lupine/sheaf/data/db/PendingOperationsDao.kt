package systems.lupine.sheaf.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationsDao {
    // ── Front switches ────────────────────────────────────────────────────────
    @Query("SELECT * FROM pending_front_switches ORDER BY created_at ASC")
    suspend fun getAllSwitches(): List<PendingFrontSwitch>

    @Query("SELECT COUNT(*) FROM pending_front_switches")
    fun switchCountFlow(): Flow<Int>

    @Insert
    suspend fun insertSwitch(entry: PendingFrontSwitch)

    @Delete
    suspend fun deleteSwitch(entry: PendingFrontSwitch)

    @Query("DELETE FROM pending_front_switches")
    suspend fun deleteAllSwitches()

    // ── Front removals ────────────────────────────────────────────────────────
    @Query("SELECT * FROM pending_front_removals ORDER BY created_at ASC")
    suspend fun getAllRemovals(): List<PendingFrontRemoval>

    @Query("SELECT COUNT(*) FROM pending_front_removals")
    fun removalCountFlow(): Flow<Int>

    @Insert
    suspend fun insertRemoval(removal: PendingFrontRemoval)

    @Delete
    suspend fun deleteRemoval(removal: PendingFrontRemoval)

    @Query("DELETE FROM pending_front_removals")
    suspend fun deleteAllRemovals()
}
