package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroSessionDao {
    @Query("SELECT * FROM pomodoro_session ORDER BY ended_at DESC, id DESC LIMIT 500")
    fun observeRecent(): Flow<List<PomodoroSessionEntity>>

    @Insert
    suspend fun insert(session: PomodoroSessionEntity): Long

    @Query(
        "DELETE FROM pomodoro_session WHERE id NOT IN " +
            "(SELECT id FROM pomodoro_session ORDER BY ended_at DESC, id DESC LIMIT :limit)",
    )
    suspend fun trimTo(limit: Int): Int

    @Query("DELETE FROM pomodoro_session")
    suspend fun clear(): Int

    @Transaction
    suspend fun insertAndTrim(session: PomodoroSessionEntity, limit: Int): Long {
        val id = insert(session)
        trimTo(limit)
        return id
    }
}
