package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryEventDao {
    @Query("SELECT * FROM anniversary_event ORDER BY pinned DESC, date_epoch_day ASC, id ASC")
    fun observeAll(): Flow<List<AnniversaryEventEntity>>

    @Query(
        "INSERT INTO anniversary_event (title, notes, date_epoch_day, repeat_rule, category, pinned, created_at, updated_at) " +
            "SELECT :title, :notes, :dateEpochDay, :repeatRule, :category, :pinned, :createdAt, :updatedAt " +
            "WHERE (SELECT COUNT(*) FROM anniversary_event) < :limit",
    )
    suspend fun insertIfBelowLimit(
        title: String,
        notes: String,
        dateEpochDay: Long,
        repeatRule: Int,
        category: Int,
        pinned: Boolean,
        createdAt: Long,
        updatedAt: Long,
        limit: Int,
    ): Long

    @Update
    suspend fun update(event: AnniversaryEventEntity)

    @Query("DELETE FROM anniversary_event WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE anniversary_event SET pinned = :pinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)
}
