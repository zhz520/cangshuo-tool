package com.cangshuo.toolbox.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="sync_entry", primaryKeys=["user_id","entity_type","entity_key"])
data class SyncEntity(@ColumnInfo(name="user_id") val userId: Long,
    @ColumnInfo(name="entity_type") val entityType: String, @ColumnInfo(name="entity_key") val entityKey: String,
    @ColumnInfo(name="updated_at") val updatedAt: Long, @ColumnInfo(name="device_id") val deviceId: String,
    val deleted: Boolean, @ColumnInfo(name="last_used_at") val lastUsedAt: Long?,
    @ColumnInfo(name="use_count") val useCount: Long?, val value: String?, val dirty: Boolean)
@Entity(tableName="sync_preference")
data class SyncPreferenceEntity(@PrimaryKey @ColumnInfo(name="user_id") val userId: Long,
    val enabled: Boolean, val enrolled: Boolean, val cursor: String,
    @ColumnInfo(name="recent_enabled", defaultValue="0") val recentEnabled: Boolean = false,
    @ColumnInfo(name="recent_enrolled", defaultValue="0") val recentEnrolled: Boolean = false,
    @ColumnInfo(name="settings_enabled", defaultValue="0") val settingsEnabled: Boolean = false,
    @ColumnInfo(name="settings_enrolled", defaultValue="0") val settingsEnrolled: Boolean = false)
@Entity(tableName="sync_device")
data class SyncDeviceEntity(@PrimaryKey val singleton: Int = 1, @ColumnInfo(name="device_id") val deviceId: String)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_preference WHERE user_id=:user") fun observePreference(user: Long): Flow<SyncPreferenceEntity?>
    @Query("SELECT * FROM sync_preference WHERE user_id=:user") suspend fun preference(user: Long): SyncPreferenceEntity?
    @Upsert suspend fun preference(preference: SyncPreferenceEntity)
    @Query("SELECT * FROM sync_entry WHERE user_id=:user AND entity_type=:type ORDER BY updated_at DESC,entity_key ASC")
    fun observeRecords(user: Long,type: String): Flow<List<SyncEntity>>
    @Query("SELECT * FROM sync_entry WHERE user_id=:user AND entity_type=:type")
    suspend fun snapshot(user: Long,type: String): List<SyncEntity>
    @Query("SELECT * FROM sync_entry WHERE user_id=:user AND entity_type=:type AND entity_key=:key")
    suspend fun find(user: Long,type: String,key: String): SyncEntity?
    @Upsert suspend fun put(entity: SyncEntity)
    @Query("SELECT * FROM sync_entry WHERE user_id=:user AND dirty=1 AND entity_type IN (:types) ORDER BY updated_at,entity_key LIMIT 100")
    suspend fun dirty(user: Long,types: Set<String>): List<SyncEntity>
    @Query("UPDATE sync_entry SET dirty=0 WHERE user_id=:user AND entity_type=:type AND entity_key=:key AND updated_at=:at AND device_id=:device")
    suspend fun acknowledge(user: Long,type: String,key: String,at: Long,device: String)
    @Query("SELECT device_id FROM sync_device WHERE singleton=1") suspend fun device(): String?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun device(entity: SyncDeviceEntity)
}
