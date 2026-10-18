package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_item ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_item WHERE id = :id")
    suspend fun find(id: Long): InventoryItemEntity?

    @Query("SELECT COUNT(*) FROM inventory_item")
    suspend fun countItems(): Int

    @Insert
    suspend fun insert(item: InventoryItemEntity): Long

    /** Room runs both statements in one transaction, so concurrent callers cannot exceed the limit. */
    @Transaction
    suspend fun insertIfBelowLimit(item: InventoryItemEntity, limit: Int): Long {
        if (countItems() >= limit) return -1L
        return insert(item)
    }

    @Update
    suspend fun update(item: InventoryItemEntity): Int

    @Query("DELETE FROM inventory_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "UPDATE inventory_item SET quantity = MAX(0, MIN(:maxQuantity, quantity + :delta)), " +
            "updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun adjustQuantity(id: Long, delta: Int, maxQuantity: Int, updatedAt: Long): Int
}
