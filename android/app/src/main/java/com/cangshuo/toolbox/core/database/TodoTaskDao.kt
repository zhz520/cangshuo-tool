package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoTaskDao {
    @Query("SELECT * FROM todo_task ORDER BY completed ASC, updated_at DESC, id DESC")
    fun observeAll(): Flow<List<TodoTaskEntity>>

    @Query("SELECT * FROM todo_task WHERE id = :id")
    suspend fun find(id: Long): TodoTaskEntity?

    @Query("SELECT COUNT(*) FROM todo_task")
    suspend fun count(): Int

    @Insert
    suspend fun insert(task: TodoTaskEntity): Long

    @Update
    suspend fun update(task: TodoTaskEntity)

    @Query("DELETE FROM todo_task WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM todo_task WHERE completed = 1")
    suspend fun deleteCompleted(): Int

    @Query("DELETE FROM todo_task")
    suspend fun clear()
}
