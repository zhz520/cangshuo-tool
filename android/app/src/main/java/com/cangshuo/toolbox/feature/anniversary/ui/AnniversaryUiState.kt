package com.cangshuo.toolbox.feature.anniversary.ui

import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryError
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryEvent
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryFilter
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversarySort
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryStats

enum class AnniversaryMessage { ADDED, UPDATED, DELETED, PINNED, UNPINNED, STORAGE_FAILED }

data class AnniversaryUiState(
    val items: List<AnniversaryEvent> = emptyList(),
    val stats: AnniversaryStats = AnniversaryStats(0, 0, 0, 0),
    val filter: AnniversaryFilter = AnniversaryFilter.ALL,
    val sort: AnniversarySort = AnniversarySort.PINNED,
    val query: String = "",
    val todayEpochDay: Long = 0,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saveSuccessRevision: Long = 0,
    val editError: AnniversaryError? = null,
    val message: AnniversaryMessage? = null,
    val busyEventIds: Set<Long> = emptySet(),
)
