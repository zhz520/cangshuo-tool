package com.cangshuo.toolbox.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_item ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_item WHERE id = :id")
    suspend fun find(id: Long): InventoryItemEntity?

    @Query(
        "INSERT INTO inventory_item (name, notes, category, location, quantity, unit, " +
            "unit_price_cents, low_stock_threshold, purchase_epoch_day, expiry_epoch_day, created_at, updated_at) " +
            "SELECT :name, :notes, :category, :location, :quantity, :unit, :unitPriceCents, " +
            ":lowStockThreshold, :purchaseEpochDay, :expiryEpochDay, :createdAt, :updatedAt " +
            "WHERE (SELECT COUNT(*) FROM inventory_item) < :limit",
    )
    suspend fun insertIfBelowLimit(
        name: String,
        notes: String,
        category: Int,
        location: String,
        quantity: Int,
        unit: String,
        unitPriceCents: Long?,
        lowStockThreshold: Int,
        purchaseEpochDay: Long?,
        expiryEpochDay: Long?,
        createdAt: Long,
        updatedAt: Long,
        limit: Int,
    ): Long

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
