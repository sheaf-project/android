package systems.lupine.sheaf.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CacheEntry::class, PendingFrontSwitch::class, PendingFrontRemoval::class],
    version = 3,
    exportSchema = false,
)
abstract class SheafDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun pendingOperationsDao(): PendingOperationsDao

    companion object {
        // v1 -> v2: add replace_fronts column to pending_front_switches.
        // Default 1 (true) keeps existing pending switches behaving exactly as
        // they did before this column existed (always end-and-replace).
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pending_front_switches " +
                        "ADD COLUMN replace_fronts INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        // v2 -> v3: add custom_status column to pending_front_switches.
        // Nullable so existing queued rows from older offline-sessions
        // keep their (implicit) null and replay without a custom status.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pending_front_switches " +
                        "ADD COLUMN custom_status TEXT"
                )
            }
        }
    }
}
