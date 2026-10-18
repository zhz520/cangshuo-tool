package com.cangshuo.toolbox.feature.inventory.data

import com.cangshuo.toolbox.core.database.InventoryItemDao
import com.cangshuo.toolbox.core.database.InventoryItemEntity
import com.cangshuo.toolbox.feature.inventory.domain.InventoryCategory
import com.cangshuo.toolbox.feature.inventory.domain.InventoryItem
import com.cangshuo.toolbox.feature.inventory.domain.InventoryLimitException
import com.cangshuo.toolbox.feature.inventory.domain.InventoryPolicy
import com.cangshuo.toolbox.feature.inventory.domain.InventoryRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomInventoryRepository(private val dao: InventoryItemDao) : InventoryRepository {
    override fun observeAll(): Flow<List<InventoryItem>> =
        dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun find(id: Long): InventoryItem? = dao.find(id)?.let(::toDomain)

    override suspend fun insert(item: InventoryItem): Long {
        val id = dao.insertIfBelowLimit(toEntity(item), InventoryPolicy.MAX_ITEMS)
        if (id == -1L) throw InventoryLimitException()
        return id
    }

    override suspend fun update(item: InventoryItem) {
        check(dao.update(toEntity(item)) > 0) { "Inventory item no longer exists" }
    }
    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun adjustQuantity(id: Long, delta: Int): Boolean =
        dao.adjustQuantity(id, delta, InventoryPolicy.MAX_QUANTITY, nowMillis()) > 0

    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun toDomain(row: InventoryItemEntity) = InventoryItem(
        id = row.id,
        name = row.name,
        notes = row.notes,
        category = InventoryCategory.fromStorage(row.category),
        location = row.location,
        quantity = row.quantity,
        unit = row.unit,
        unitPriceCents = row.unitPriceCents,
        lowStockThreshold = row.lowStockThreshold,
        purchaseEpochDay = row.purchaseEpochDay,
        expiryEpochDay = row.expiryEpochDay,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun toEntity(item: InventoryItem) = InventoryItemEntity(
        id = item.id,
        name = item.name,
        notes = item.notes,
        category = item.category.storage,
        location = item.location,
        quantity = item.quantity,
        unit = item.unit,
        unitPriceCents = item.unitPriceCents,
        lowStockThreshold = item.lowStockThreshold,
        purchaseEpochDay = item.purchaseEpochDay,
        expiryEpochDay = item.expiryEpochDay,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
    )
}
