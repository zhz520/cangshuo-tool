package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habit ORDER BY archived ASC, position ASC, id ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_entry ORDER BY date_epoch_day DESC")
    fun observeEntries(): Flow<List<HabitEntryEntity>>

    @Query(
        "INSERT INTO habit (name, description, weekdays_mask, archived, archived_day, position, created_day, created_at, updated_at) " +
            "SELECT :name, :description, :weekdaysMask, :archived, :archivedDay, COALESCE(MAX(position), -1) + 1, " +
            ":createdDay, :createdAt, :updatedAt FROM habit WHERE (SELECT COUNT(*) FROM habit) < :limit",
    )
    suspend fun insertIfBelowLimit(
        name: String,
        description: String,
        weekdaysMask: Int,
        archived: Boolean,
        archivedDay: Long?,
        createdDay: Long,
        createdAt: Long,
        updatedAt: Long,
        limit: Int,
    ): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE habit SET archived = :archived, archived_day = :archivedDay, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, archivedDay: Long?, updatedAt: Long)

    @Query(
        "INSERT OR IGNORE INTO habit_entry (habit_id, date_epoch_day, checked_at) " +
            "SELECT :habitId, :dateEpochDay, :checkedAt " +
            "WHERE (SELECT COUNT(*) FROM habit_entry) < :limit " +
            "AND EXISTS (SELECT 1 FROM habit WHERE id = :habitId AND archived = 0)",
    )
    suspend fun insertEntryIfAllowed(
        habitId: Long,
        dateEpochDay: Long,
        checkedAt: Long,
        limit: Int,
    ): Long

    @Query("DELETE FROM habit_entry WHERE habit_id = :habitId AND date_epoch_day = :dateEpochDay")
    suspend fun deleteEntry(habitId: Long, dateEpochDay: Long)

    @Query("DELETE FROM habit_entry WHERE habit_id = :habitId")
    suspend fun deleteEntries(habitId: Long): Int

}
