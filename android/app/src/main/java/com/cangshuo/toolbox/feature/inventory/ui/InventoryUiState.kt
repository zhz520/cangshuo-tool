package com.cangshuo.toolbox.feature.inventory.ui

import com.cangshuo.toolbox.feature.inventory.domain.InventoryError
import com.cangshuo.toolbox.feature.inventory.domain.InventoryFilter
import com.cangshuo.toolbox.feature.inventory.domain.InventoryItem
import com.cangshuo.toolbox.feature.inventory.domain.InventorySort
import com.cangshuo.toolbox.feature.inventory.domain.InventoryStats

enum class InventoryMessage { ADDED, UPDATED, DELETED, STORAGE_FAILED }

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val stats: InventoryStats = InventoryStats(),
    val filter: InventoryFilter = InventoryFilter.ALL,
    val sort: InventorySort = InventorySort.UPDATED,
    val query: String = "",
    val todayEpochDay: Long = 0,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saveSuccessRevision: Long = 0,
    val editError: InventoryError? = null,
    val message: InventoryMessage? = null,
    val messageRevision: Long = 0,
    val busyItemIds: Set<Long> = emptySet(),
)
