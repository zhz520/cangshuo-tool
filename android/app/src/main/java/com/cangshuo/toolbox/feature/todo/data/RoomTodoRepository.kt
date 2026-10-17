package com.cangshuo.toolbox.feature.todo.data

import com.cangshuo.toolbox.core.database.TodoTaskDao
import com.cangshuo.toolbox.core.database.TodoTaskEntity
import com.cangshuo.toolbox.feature.todo.domain.TodoItem
import com.cangshuo.toolbox.feature.todo.domain.TodoPriority
import com.cangshuo.toolbox.feature.todo.domain.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomTodoRepository(private val dao: TodoTaskDao) : TodoRepository {
    override fun observeAll(): Flow<List<TodoItem>> = dao.observeAll().map { rows -> rows.map(::toDomain) }

    override suspend fun find(id: Long): TodoItem? = dao.find(id)?.let(::toDomain)

    override suspend fun insert(item: TodoItem): Long = dao.insert(toEntity(item))

    override suspend fun update(item: TodoItem) = dao.update(toEntity(item))

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteCompleted(): Int = dao.deleteCompleted()

    override suspend fun count(): Int = dao.count()

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    private fun toDomain(row: TodoTaskEntity) = TodoItem(
        id = row.id,
        title = row.title,
        notes = row.notes,
        dueDateEpochDay = row.dueDate,
        priority = TodoPriority.fromStorage(row.priority),
        completed = row.completed,
        completedAt = row.completedAt,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun toEntity(item: TodoItem) = TodoTaskEntity(
        id = item.id,
        title = item.title,
        notes = item.notes,
        dueDate = item.dueDateEpochDay,
        priority = item.priority.storage,
        completed = item.completed,
        completedAt = item.completedAt,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
    )
}
