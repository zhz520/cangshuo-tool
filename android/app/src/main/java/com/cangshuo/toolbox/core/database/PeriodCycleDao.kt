package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodCycleDao {
    @Query("SELECT * FROM period_cycle ORDER BY start_epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<PeriodCycleEntity>>

    @Query("SELECT * FROM period_cycle WHERE id = :id")
    suspend fun find(id: Long): PeriodCycleEntity?

    @Query("SELECT COUNT(*) FROM period_cycle")
    suspend fun count(): Int

    @Insert
    suspend fun insert(cycle: PeriodCycleEntity): Long

    @Transaction
    suspend fun insertIfBelowLimit(cycle: PeriodCycleEntity, limit: Int): Long {
        if (count() >= limit) return -1L
        return insert(cycle)
    }

    @Update
    suspend fun update(cycle: PeriodCycleEntity): Int

    @Query("DELETE FROM period_cycle WHERE id = :id")
    suspend fun delete(id: Long): Int
}
