package com.cangshuo.toolbox.feature.todo.ui

import com.cangshuo.toolbox.feature.todo.domain.TodoError
import com.cangshuo.toolbox.feature.todo.domain.TodoFilter
import com.cangshuo.toolbox.feature.todo.domain.TodoItem
import com.cangshuo.toolbox.feature.todo.domain.TodoPolicy
import com.cangshuo.toolbox.feature.todo.domain.TodoSort
import com.cangshuo.toolbox.feature.todo.domain.TodoStats

enum class TodoMessage { ADDED, UPDATED, DELETED, CLEARED, STORAGE_FAILED }

data class TodoUiState(
    val items: List<TodoItem> = emptyList(),
    val stats: TodoStats = TodoStats(0, 0, 0, 0, 0),
    val filter: TodoFilter = TodoFilter.ALL,
    val sort: TodoSort = TodoSort.ACTIVE_FIRST,
    val query: String = "",
    val todayEpochDay: Long = 0,
    val loading: Boolean = true,
    val editError: TodoError? = null,
    val message: TodoMessage? = null,
)
