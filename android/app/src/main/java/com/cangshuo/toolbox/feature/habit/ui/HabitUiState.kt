package com.cangshuo.toolbox.feature.habit.ui

import com.cangshuo.toolbox.feature.habit.domain.Habit
import com.cangshuo.toolbox.feature.habit.domain.HabitError
import com.cangshuo.toolbox.feature.habit.domain.HabitFilter
import com.cangshuo.toolbox.feature.habit.domain.HabitSort
import com.cangshuo.toolbox.feature.habit.domain.HabitStats

enum class HabitMessage { CREATED, UPDATED, CHECKED, UNCHECKED, ARCHIVED, RESTORED, DELETED, HISTORY_CLEARED, ENTRY_LIMIT, STORAGE_FAILED }

data class HabitUiState(
    val items: List<Habit> = emptyList(),
    val stats: HabitStats = HabitStats(0, 0, 0, 0, 0),
    val filter: HabitFilter = HabitFilter.ALL,
    val sort: HabitSort = HabitSort.TODAY_FIRST,
    val query: String = "",
    val todayEpochDay: Long = 0,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saveSuccessRevision: Long = 0,
    val editError: HabitError? = null,
    val message: HabitMessage? = null,
    val busyHabitIds: Set<Long> = emptySet(),
)
