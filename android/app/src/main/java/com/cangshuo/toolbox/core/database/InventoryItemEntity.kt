package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_item",
    indices = [
        Index(value = ["category"]),
        Index(value = ["expiry_epoch_day"]),
        Index(value = ["updated_at"]),
    ],
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "category") val category: Int,
    @ColumnInfo(name = "location") val location: String,
    @ColumnInfo(name = "quantity") val quantity: Int,
    @ColumnInfo(name = "unit") val unit: String,
    @ColumnInfo(name = "unit_price_cents") val unitPriceCents: Long?,
    @ColumnInfo(name = "low_stock_threshold") val lowStockThreshold: Int,
    @ColumnInfo(name = "purchase_epoch_day") val purchaseEpochDay: Long?,
    @ColumnInfo(name = "expiry_epoch_day") val expiryEpochDay: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
