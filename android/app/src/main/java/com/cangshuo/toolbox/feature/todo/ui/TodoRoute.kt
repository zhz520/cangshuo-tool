package com.cangshuo.toolbox.feature.todo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TodoRoute(factory: ViewModelProvider.Factory) {
    val model: TodoViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    DisposableEffect(model) {
        onDispose { model.consumeMessage() }
    }
    TodoScreen(
        state = state,
        onRefreshToday = model::refreshToday,
        onFilter = model::setFilter,
        onSort = model::setSort,
        onQuery = model::setQuery,
        onSave = model::save,
        onToggle = model::toggle,
        onDelete = model::delete,
        onDeleteCompleted = model::deleteCompleted,
        onClearEditError = model::clearEditError,
        onMessageConsumed = model::consumeMessage,
    )
}
