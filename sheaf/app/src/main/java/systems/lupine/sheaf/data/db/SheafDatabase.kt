package systems.lupine.sheaf.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CacheEntry::class, PendingFrontSwitch::class, PendingFrontRemoval::class],
    version = 1,
    exportSchema = false,
)
abstract class SheafDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingOperationsDao(): PendingOperationsDao
}
