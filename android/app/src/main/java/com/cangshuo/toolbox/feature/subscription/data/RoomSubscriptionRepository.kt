package com.cangshuo.toolbox.feature.subscription.data

import com.cangshuo.toolbox.core.database.SubscriptionItemDao
import com.cangshuo.toolbox.core.database.SubscriptionItemEntity
import com.cangshuo.toolbox.feature.subscription.domain.BillingCycle
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionCategory
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionItem
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionLimitException
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionPolicy
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSubscriptionRepository(private val dao: SubscriptionItemDao) : SubscriptionRepository {
    override fun observeAll(): Flow<List<SubscriptionItem>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun find(id: Long): SubscriptionItem? = dao.find(id)?.toDomain()

    override suspend fun insert(item: SubscriptionItem): Long {
        val id = dao.insertIfBelowLimit(item.toEntity(), SubscriptionPolicy.MAX_ITEMS)
        if (id == -1L) throw SubscriptionLimitException()
        return id
    }

    override suspend fun update(item: SubscriptionItem) {
        check(dao.update(item.toEntity()) > 0) { "Subscription no longer exists" }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

}

internal fun SubscriptionItemEntity.toDomain(): SubscriptionItem {
    val row = this
    return SubscriptionItem(
        id = row.id,
        name = row.name,
        provider = row.provider,
        category = SubscriptionCategory.fromStorage(row.category),
        priceCents = row.priceCents,
        billingCycle = BillingCycle.fromStorage(row.billingCycle),
        customCycleDays = row.customCycleDays,
        startEpochDay = row.startEpochDay,
        billingAnchorEpochDay = row.billingAnchorEpochDay,
        nextPaymentEpochDay = row.nextPaymentEpochDay,
        paymentMethod = row.paymentMethod,
        notes = row.notes,
        reminderEnabled = row.reminderEnabled,
        reminderDays = row.reminderDays,
        autoRenew = row.autoRenew,
        active = row.active,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )
}

internal fun SubscriptionItem.toEntity(): SubscriptionItemEntity {
    val item = this
    return SubscriptionItemEntity(
        id = item.id,
        name = item.name,
        provider = item.provider,
        category = item.category.storage,
        priceCents = item.priceCents,
        billingCycle = item.billingCycle.storage,
        customCycleDays = item.customCycleDays,
        startEpochDay = item.startEpochDay,
        billingAnchorEpochDay = item.billingAnchorEpochDay,
        nextPaymentEpochDay = item.nextPaymentEpochDay,
        paymentMethod = item.paymentMethod,
        notes = item.notes,
        reminderEnabled = item.reminderEnabled,
        reminderDays = item.reminderDays,
        autoRenew = item.autoRenew,
        active = item.active,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
    )
}
