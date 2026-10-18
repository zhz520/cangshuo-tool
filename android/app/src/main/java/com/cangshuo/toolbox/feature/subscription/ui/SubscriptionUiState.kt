package com.cangshuo.toolbox.feature.subscription.ui

import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionError
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionFilter
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionItem
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionSort
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionStats

enum class SubscriptionMessage { ADDED, UPDATED, DELETED, RENEWED, PAUSED, RESUMED, STORAGE_FAILED }

data class SubscriptionUiState(
    val items: List<SubscriptionItem> = emptyList(),
    val stats: SubscriptionStats = SubscriptionStats(),
    val filter: SubscriptionFilter = SubscriptionFilter.ALL,
    val sort: SubscriptionSort = SubscriptionSort.NEXT_PAYMENT,
    val query: String = "",
    val todayEpochDay: Long = 0,
    val hasActiveReminders: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saveSuccessRevision: Long = 0,
    val editError: SubscriptionError? = null,
    val message: SubscriptionMessage? = null,
    val messageRevision: Long = 0,
    val busyItemIds: Set<Long> = emptySet(),
)
