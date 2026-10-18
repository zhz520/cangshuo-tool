package com.cangshuo.toolbox.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscription_item",
    indices = [
        Index(value = ["active"]),
        Index(value = ["next_payment_epoch_day"]),
        Index(value = ["updated_at"]),
    ],
)
data class SubscriptionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "category") val category: Int,
    @ColumnInfo(name = "price_cents") val priceCents: Long,
    @ColumnInfo(name = "billing_cycle") val billingCycle: Int,
    @ColumnInfo(name = "custom_cycle_days") val customCycleDays: Int,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    @ColumnInfo(name = "billing_anchor_epoch_day") val billingAnchorEpochDay: Long,
    @ColumnInfo(name = "next_payment_epoch_day") val nextPaymentEpochDay: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
    @ColumnInfo(name = "reminder_days") val reminderDays: Int,
    @ColumnInfo(name = "auto_renew") val autoRenew: Boolean,
    @ColumnInfo(name = "active") val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
