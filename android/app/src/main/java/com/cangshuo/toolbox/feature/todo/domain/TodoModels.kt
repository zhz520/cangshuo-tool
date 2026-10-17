package com.cangshuo.toolbox.feature.todo.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.Locale

enum class TodoPriority(val storage: Int) {
    LOW(0), NORMAL(1), HIGH(2);

    companion object {
        fun fromStorage(value: Int): TodoPriority = entries.firstOrNull { it.storage == value } ?: NORMAL
    }
}

enum class TodoFilter { ALL, ACTIVE, COMPLETED }
enum class TodoSort { ACTIVE_FIRST, DUE_DATE, PRIORITY, CREATED_DESC }
enum class TodoError { TITLE, NOTES, LIMIT, STORAGE }

data class TodoItem(
    val id: Long,
    val title: String,
    val notes: String,
    val dueDateEpochDay: Long?,
    val priority: TodoPriority,
    val completed: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class TodoStats(
    val total: Int,
    val active: Int,
    val completed: Int,
    val overdue: Int,
    val dueToday: Int,
)

interface TodoRepository {
    fun observeAll(): Flow<List<TodoItem>>

    suspend fun find(id: Long): TodoItem?

    suspend fun insert(item: TodoItem): Long

    suspend fun update(item: TodoItem)

    suspend fun delete(id: Long)

    suspend fun deleteCompleted(): Int

    suspend fun count(): Int

    fun nowMillis(): Long

    fun todayEpochDay(): Long
}

object TodoPolicy {
    const val MAX_TASKS = 500
    const val MAX_TITLE_CODE_POINTS = 120
    const val MAX_NOTES_CODE_POINTS = 2_000

    fun validateTitle(title: String): TodoError? = when {
        title.isBlank() -> TodoError.TITLE
        title.trim().codePointCount(0, title.trim().length) > MAX_TITLE_CODE_POINTS -> TodoError.TITLE
        else -> null
    }

    fun validateNotes(notes: String): TodoError? =
        if (notes.codePointCount(0, notes.length) > MAX_NOTES_CODE_POINTS) TodoError.NOTES else null

    fun normalizeTitle(title: String): String = title.trim().replace(Regex("\\s+"), " ")

    fun normalizeNotes(notes: String): String = notes.trim()

    fun filterAndSort(
        items: List<TodoItem>,
        filter: TodoFilter,
        sort: TodoSort,
        query: String,
    ): List<TodoItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val filtered = items.filter { item ->
            (filter == TodoFilter.ALL || item.completed == (filter == TodoFilter.COMPLETED)) &&
                (needle.isEmpty() || item.title.lowercase(Locale.ROOT).contains(needle) ||
                    item.notes.lowercase(Locale.ROOT).contains(needle))
        }
        val activeFirst = compareBy<TodoItem> { it.completed }
        val due = compareBy<TodoItem> { it.dueDateEpochDay == null }.thenBy { it.dueDateEpochDay ?: Long.MAX_VALUE }
        val priority = compareByDescending<TodoItem> { it.priority.storage }
        val newest = compareByDescending<TodoItem> { it.createdAt }.thenByDescending { it.id }
        return when (sort) {
            TodoSort.ACTIVE_FIRST -> filtered.sortedWith(activeFirst.then(newest))
            TodoSort.DUE_DATE -> filtered.sortedWith(activeFirst.then(due).then(priority).then(newest))
            TodoSort.PRIORITY -> filtered.sortedWith(activeFirst.then(priority).then(due).then(newest))
            TodoSort.CREATED_DESC -> filtered.sortedWith(newest)
        }
    }

    fun stats(items: List<TodoItem>, todayEpochDay: Long): TodoStats {
        val completed = items.count { it.completed }
        val active = items.size - completed
        val overdue = items.count { !it.completed && it.dueDateEpochDay != null && it.dueDateEpochDay < todayEpochDay }
        val dueToday = items.count { !it.completed && it.dueDateEpochDay == todayEpochDay }
        return TodoStats(items.size, active, completed, overdue, dueToday)
    }

    fun dueLabel(dueDateEpochDay: Long?, todayEpochDay: Long): Int? {
        val date = dueDateEpochDay ?: return null
        return (date - todayEpochDay).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }
}

class TodoUseCases(private val repository: TodoRepository) {
    fun observe() = repository.observeAll()

    fun create(
        title: String,
        notes: String,
        dueDateEpochDay: Long?,
        priority: TodoPriority,
    ): TodoItem? {
        validate(title, notes)?.let { return null }
        val now = repository.nowMillis()
        return TodoItem(
            id = 0,
            title = TodoPolicy.normalizeTitle(title),
            notes = TodoPolicy.normalizeNotes(notes),
            dueDateEpochDay = dueDateEpochDay,
            priority = priority,
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(
        current: TodoItem,
        title: String,
        notes: String,
        dueDateEpochDay: Long?,
        priority: TodoPriority,
    ): TodoItem? {
        validate(title, notes)?.let { return null }
        return current.copy(
            title = TodoPolicy.normalizeTitle(title),
            notes = TodoPolicy.normalizeNotes(notes),
            dueDateEpochDay = dueDateEpochDay,
            priority = priority,
            updatedAt = repository.nowMillis(),
        )
    }

    fun validate(title: String, notes: String): TodoError? =
        TodoPolicy.validateTitle(title) ?: TodoPolicy.validateNotes(notes)

    fun now(): Long = repository.nowMillis()
    fun today(): Long = repository.todayEpochDay()
    fun localDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
    suspend fun find(id: Long): TodoItem? = repository.find(id)
    suspend fun insert(item: TodoItem): Long = repository.insert(item)
    suspend fun update(item: TodoItem) = repository.update(item)
    suspend fun delete(id: Long) = repository.delete(id)
    suspend fun deleteCompleted(): Int = repository.deleteCompleted()
    suspend fun count(): Int = repository.count()
}
