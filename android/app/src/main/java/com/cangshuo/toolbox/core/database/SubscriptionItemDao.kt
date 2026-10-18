package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionItemDao {
    @Query("SELECT * FROM subscription_item ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<SubscriptionItemEntity>>

    @Query("SELECT * FROM subscription_item ORDER BY updated_at DESC, id DESC")
    suspend fun getAll(): List<SubscriptionItemEntity>

    @Query("SELECT * FROM subscription_item WHERE id = :id")
    suspend fun find(id: Long): SubscriptionItemEntity?

    @Query("SELECT COUNT(*) FROM subscription_item")
    suspend fun countItems(): Int

    @Insert
    suspend fun insert(item: SubscriptionItemEntity): Long

    @Transaction
    suspend fun insertIfBelowLimit(item: SubscriptionItemEntity, limit: Int): Long {
        if (countItems() >= limit) return -1L
        return insert(item)
    }

    @Update
    suspend fun update(item: SubscriptionItemEntity): Int

    @Query("DELETE FROM subscription_item WHERE id = :id")
    suspend fun delete(id: Long)
}
