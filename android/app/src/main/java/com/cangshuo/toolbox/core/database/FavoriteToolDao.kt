package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteToolDao {
    @Query("SELECT * FROM favorite_tool ORDER BY added_at DESC, tool_code ASC")
    fun observeFavorites(): Flow<List<FavoriteToolEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteToolEntity)

    @Query("DELETE FROM favorite_tool WHERE tool_code = :toolCode")
    suspend fun remove(toolCode: String)
}
