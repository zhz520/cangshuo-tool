package com.cangshuo.toolbox.feature.todo

import com.cangshuo.toolbox.feature.todo.domain.TodoError
import com.cangshuo.toolbox.feature.todo.domain.TodoFilter
import com.cangshuo.toolbox.feature.todo.domain.TodoItem
import com.cangshuo.toolbox.feature.todo.domain.TodoPolicy
import com.cangshuo.toolbox.feature.todo.domain.TodoPriority
import com.cangshuo.toolbox.feature.todo.domain.TodoSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoPolicyTest {
    private fun task(
        id: Long,
        title: String = "Task $id",
        notes: String = "",
        due: Long? = null,
        priority: TodoPriority = TodoPriority.NORMAL,
        completed: Boolean = false,
        created: Long = id,
    ) = TodoItem(
        id = id,
        title = title,
        notes = notes,
        dueDateEpochDay = due,
        priority = priority,
        completed = completed,
        completedAt = created.takeIf { completed },
        createdAt = created,
        updatedAt = created,
    )

    @Test fun titleAndNoteBudgetsCountUnicodeCodePoints() {
        assertEquals(TodoError.TITLE, TodoPolicy.validateTitle("   "))
        assertNull(TodoPolicy.validateTitle("\uD83D\uDE00".repeat(120)))
        assertEquals(TodoError.TITLE, TodoPolicy.validateTitle("\uD83D\uDE00".repeat(121)))
        assertNull(TodoPolicy.validateNotes("\uD83D\uDE00".repeat(2_000)))
        assertEquals(TodoError.NOTES, TodoPolicy.validateNotes("\uD83D\uDE00".repeat(2_001)))
    }

    @Test fun normalizationTrimsAndCollapsesTitleWhitespace() {
        assertEquals("Buy milk", TodoPolicy.normalizeTitle("  Buy \n\t milk  "))
        assertEquals("Line one\nLine two", TodoPolicy.normalizeNotes("\n Line one\nLine two \n"))
        assertEquals(TodoPriority.NORMAL, TodoPriority.fromStorage(99))
        assertEquals(TodoPriority.HIGH, TodoPriority.fromStorage(2))
    }

    @Test fun searchAndStatusFiltersCompose() {
        val tasks = listOf(
            task(1, "Renew Passport", "Bring photo", completed = false, created = 1),
            task(2, "Renew library card", "PASSPORT required", completed = true, created = 3),
            task(3, "Buy milk", completed = false, created = 2),
        )
        assertEquals(listOf(1L), TodoPolicy.filterAndSort(tasks, TodoFilter.ACTIVE, TodoSort.CREATED_DESC, "passport").map { it.id })
        assertEquals(listOf(2L), TodoPolicy.filterAndSort(tasks, TodoFilter.COMPLETED, TodoSort.CREATED_DESC, "passport").map { it.id })
        assertEquals(listOf(3L), TodoPolicy.filterAndSort(tasks, TodoFilter.ALL, TodoSort.CREATED_DESC, "  milk ").map { it.id })
    }

    @Test fun activeFirstKeepsCompletedTasksAtTheEndAndUsesNewestOrder() {
        val tasks = listOf(
            task(1, completed = false, created = 10),
            task(2, completed = true, created = 30),
            task(3, completed = false, created = 20),
        )
        assertEquals(listOf(3L, 1L, 2L), TodoPolicy.filterAndSort(tasks, TodoFilter.ALL, TodoSort.ACTIVE_FIRST, "").map { it.id })
    }

    @Test fun dueDateSortGroupsActiveTasksAndPutsUndatedTasksLast() {
        val tasks = listOf(
            task(1, due = null, priority = TodoPriority.HIGH, created = 5),
            task(2, due = 30, priority = TodoPriority.HIGH, created = 4),
            task(3, due = 10, priority = TodoPriority.LOW, created = 3),
            task(4, due = 5, completed = true, created = 2),
        )
        assertEquals(listOf(3L, 2L, 1L, 4L), TodoPolicy.filterAndSort(tasks, TodoFilter.ALL, TodoSort.DUE_DATE, "").map { it.id })
    }

    @Test fun priorityAndCreatedSortsAreDistinct() {
        val tasks = listOf(
            task(1, due = 1, priority = TodoPriority.LOW, created = 20),
            task(2, due = 30, priority = TodoPriority.HIGH, created = 10),
            task(3, due = null, priority = TodoPriority.NORMAL, created = 30),
            task(4, completed = true, priority = TodoPriority.HIGH, created = 40),
        )
        assertEquals(listOf(2L, 3L, 1L, 4L), TodoPolicy.filterAndSort(tasks, TodoFilter.ALL, TodoSort.PRIORITY, "").map { it.id })
        assertEquals(listOf(4L, 3L, 1L, 2L), TodoPolicy.filterAndSort(tasks, TodoFilter.ALL, TodoSort.CREATED_DESC, "").map { it.id })
    }

    @Test fun statisticsIgnoreCompletedTasksForDueCounts() {
        val today = 1_000L
        val stats = TodoPolicy.stats(
            listOf(
                task(1, due = today - 1),
                task(2, due = today),
                task(3, due = today),
                task(4, due = today - 2, completed = true),
                task(5, due = today, completed = true),
            ),
            today,
        )
        assertEquals(5, stats.total)
        assertEquals(3, stats.active)
        assertEquals(2, stats.completed)
        assertEquals(1, stats.overdue)
        assertEquals(2, stats.dueToday)
        assertEquals(-1, TodoPolicy.dueLabel(today - 1, today))
        assertEquals(0, TodoPolicy.dueLabel(today, today))
        assertNull(TodoPolicy.dueLabel(null, today))
    }
}
