package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QrHistoryDao {
    // Bound the projection before CursorWindow receives payloads; 100 × 16 KB maximum.
    @Query("SELECT * FROM qr_scan_history WHERE length(CAST(payload AS BLOB)) BETWEEN 1 AND 16000 AND scanned_at > 0 AND length(entry_key) = 64 AND length(symbology) <= 32 ORDER BY scanned_at DESC, entry_key ASC LIMIT 100")
    fun observeEntries(): Flow<List<QrHistoryEntity>>

    @Query("SELECT enabled FROM qr_history_preference WHERE singleton = 1")
    fun observeEnabled(): Flow<Boolean?>

    @Query("SELECT enabled FROM qr_history_preference WHERE singleton = 1")
    suspend fun isEnabled(): Boolean?

    @Upsert suspend fun setPreference(preference: QrHistoryPreferenceEntity)
    @Upsert suspend fun upsert(entries: List<QrHistoryEntity>)

    @Query("DELETE FROM qr_scan_history WHERE entry_key NOT IN (SELECT entry_key FROM qr_scan_history ORDER BY scanned_at DESC, entry_key ASC LIMIT 100)")
    suspend fun trim()

    @Query("DELETE FROM qr_scan_history WHERE entry_key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM qr_scan_history")
    suspend fun clear()

    @Transaction
    suspend fun recordIfEnabled(entries: List<QrHistoryEntity>): Boolean {
        if (isEnabled() != true) return false
        upsert(entries)
        trim()
        return true
    }
}
