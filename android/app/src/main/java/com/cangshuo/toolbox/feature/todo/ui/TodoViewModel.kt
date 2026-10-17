package com.cangshuo.toolbox.feature.todo.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.todo.domain.TodoError
import com.cangshuo.toolbox.feature.todo.domain.TodoFilter
import com.cangshuo.toolbox.feature.todo.domain.TodoItem
import com.cangshuo.toolbox.feature.todo.domain.TodoPolicy
import com.cangshuo.toolbox.feature.todo.domain.TodoPriority
import com.cangshuo.toolbox.feature.todo.domain.TodoSort
import com.cangshuo.toolbox.feature.todo.domain.TodoUseCases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TodoViewModel(
    private val useCases: TodoUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var allItems = emptyList<TodoItem>()
    private var today = useCases.today()
    private val mutable = MutableStateFlow(
        TodoUiState(
            filter = saved.get<String>("todoFilter")?.let { TodoFilter.entries.firstOrNull { f -> f.name == it } }
                ?: TodoFilter.ALL,
            sort = saved.get<String>("todoSort")?.let { TodoSort.entries.firstOrNull { s -> s.name == it } }
                ?: TodoSort.ACTIVE_FIRST,
            query = saved.get<String>("todoQuery")?.take(80).orEmpty(),
            todayEpochDay = today,
        ),
    )
    val state: StateFlow<TodoUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                useCases.observe().collect { items ->
                    allItems = items
                    publish(loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, message = TodoMessage.STORAGE_FAILED)
            }
        }
    }

    fun refreshToday() {
        today = useCases.today()
        publish(loading = mutable.value.loading)
    }

    fun setFilter(filter: TodoFilter) {
        saved["todoFilter"] = filter.name
        mutable.value = mutable.value.copy(filter = filter)
        publish(mutable.value.loading)
    }

    fun setSort(sort: TodoSort) {
        saved["todoSort"] = sort.name
        mutable.value = mutable.value.copy(sort = sort)
        publish(mutable.value.loading)
    }

    fun setQuery(query: String) {
        val normalized = query.take(80)
        saved["todoQuery"] = normalized
        mutable.value = mutable.value.copy(query = normalized)
        publish(mutable.value.loading)
    }

    fun save(id: Long?, title: String, notes: String, dueDateEpochDay: Long?, priority: TodoPriority): Boolean {
        val error = useCases.validate(title, notes)
        if (error != null) {
            mutable.value = mutable.value.copy(editError = error)
            return false
        }
        if (id == null && allItems.size >= TodoPolicy.MAX_TASKS) {
            mutable.value = mutable.value.copy(editError = TodoError.LIMIT)
            return false
        }
        val current = id?.let { taskId -> allItems.firstOrNull { it.id == taskId } }
        if (id != null && current == null) return false
        val item = if (current == null) {
            useCases.create(title, notes, dueDateEpochDay, priority)
        } else {
            useCases.update(current, title, notes, dueDateEpochDay, priority)
        } ?: return false
        viewModelScope.launch {
            try {
                if (current == null) useCases.insert(item) else useCases.update(item)
                mutable.value = mutable.value.copy(
                    editError = null,
                    message = if (current == null) TodoMessage.ADDED else TodoMessage.UPDATED,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = TodoMessage.STORAGE_FAILED)
            }
        }
        return true
    }

    fun toggle(item: TodoItem) {
        val completed = !item.completed
        val now = useCases.now()
        val updated = item.copy(completed = completed, completedAt = now.takeIf { completed }, updatedAt = now)
        viewModelScope.launch {
            try { useCases.update(updated) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = TodoMessage.STORAGE_FAILED) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            try {
                useCases.delete(id)
                mutable.value = mutable.value.copy(message = TodoMessage.DELETED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = TodoMessage.STORAGE_FAILED) }
        }
    }

    fun deleteCompleted() {
        viewModelScope.launch {
            try {
                if (useCases.deleteCompleted() > 0) mutable.value = mutable.value.copy(message = TodoMessage.CLEARED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = TodoMessage.STORAGE_FAILED) }
        }
    }

    fun clearEditError() {
        if (mutable.value.editError != null) mutable.value = mutable.value.copy(editError = null)
    }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun publish(loading: Boolean) {
        val current = mutable.value
        val visible = TodoPolicy.filterAndSort(allItems, current.filter, current.sort, current.query)
        mutable.value = current.copy(
            items = visible,
            stats = TodoPolicy.stats(allItems, today),
            todayEpochDay = today,
            loading = loading,
        )
    }

    companion object {
        fun factory(useCases: TodoUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                TodoViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
