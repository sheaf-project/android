package systems.lupine.sheaf.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

// Stores a pending front-switch: the desired member IDs as a comma-separated string.
@Entity(tableName = "pending_front_switches")
data class PendingFrontSwitch(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "member_ids") val memberIds: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

// Stores a pending remove-from-front: the member ID to remove.
@Entity(tableName = "pending_front_removals")
data class PendingFrontRemoval(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
