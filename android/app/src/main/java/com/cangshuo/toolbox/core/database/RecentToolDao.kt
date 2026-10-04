package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentToolDao {
    @Query("SELECT * FROM recent_tool ORDER BY last_used_at DESC, tool_code ASC")
    fun observeRecent(): Flow<List<RecentToolEntity>>

    @Query("SELECT * FROM recent_tool WHERE tool_code = :toolCode")
    suspend fun find(toolCode: String): RecentToolEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recent: RecentToolEntity)

    @Query("DELETE FROM recent_tool WHERE tool_code = :toolCode")
    suspend fun remove(toolCode: String)

    @Query("DELETE FROM recent_tool WHERE tool_code NOT IN (SELECT tool_code FROM recent_tool ORDER BY last_used_at DESC, tool_code ASC LIMIT :maxEntries)")
    suspend fun trimTo(maxEntries: Int)

    @Query("DELETE FROM recent_tool")
    suspend fun clear()
}
