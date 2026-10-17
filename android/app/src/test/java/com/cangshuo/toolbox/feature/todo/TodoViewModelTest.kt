package com.cangshuo.toolbox.feature.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.todo.domain.TodoError
import com.cangshuo.toolbox.feature.todo.domain.TodoFilter
import com.cangshuo.toolbox.feature.todo.domain.TodoItem
import com.cangshuo.toolbox.feature.todo.domain.TodoPolicy
import com.cangshuo.toolbox.feature.todo.domain.TodoPriority
import com.cangshuo.toolbox.feature.todo.domain.TodoRepository
import com.cangshuo.toolbox.feature.todo.domain.TodoSort
import com.cangshuo.toolbox.feature.todo.domain.TodoUseCases
import com.cangshuo.toolbox.feature.todo.ui.TodoMessage
import com.cangshuo.toolbox.feature.todo.ui.TodoViewModel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelKey = 0

    private class FakeRepository(initial: List<TodoItem> = emptyList()) : TodoRepository {
        val items = MutableStateFlow(initial)
        var now = 1_000L
        var today = 20_000L
        var failInsert = false
        var failUpdate = false
        var failDelete = false
        var inserts = 0
        var updates = 0
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun observeAll(): Flow<List<TodoItem>> = items

        override suspend fun find(id: Long): TodoItem? = items.value.firstOrNull { it.id == id }

        override suspend fun insert(item: TodoItem): Long {
            inserts++
            if (failInsert) throw IOException("write failed")
            val id = nextId++
            items.value = items.value + item.copy(id = id)
            return id
        }

        override suspend fun update(item: TodoItem) {
            updates++
            if (failUpdate) throw IOException("write failed")
            items.value = items.value.map { if (it.id == item.id) item else it }
        }

        override suspend fun delete(id: Long) {
            if (failDelete) throw IOException("write failed")
            items.value = items.value.filterNot { it.id == id }
        }

        override suspend fun deleteCompleted(): Int {
            if (failDelete) throw IOException("write failed")
            val before = items.value.size
            items.value = items.value.filterNot { it.completed }
            return before - items.value.size
        }

        override suspend fun count(): Int = items.value.size
        override fun nowMillis(): Long = now
        override fun todayEpochDay(): Long = today
    }

    private fun model(repo: FakeRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        TodoViewModel(TodoUseCases(repo), saved).also { models.put("todo-${modelKey++}", it) }

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

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun initialCollectionAndSavedViewStateAreRestored() = runTest {
        val repo = FakeRepository(
            listOf(
                task(1, "Alpha", notes = "active match"),
                task(2, "Alpha done", completed = true, created = 2),
            ),
        )
        val saved = SavedStateHandle(
            mapOf(
                "todoFilter" to TodoFilter.ACTIVE.name,
                "todoSort" to TodoSort.PRIORITY.name,
                "todoQuery" to "alpha",
            ),
        )
        val model = model(repo, saved)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(TodoFilter.ACTIVE, model.state.value.filter)
        assertEquals(TodoSort.PRIORITY, model.state.value.sort)
        assertEquals("alpha", model.state.value.query)
        assertEquals(listOf(1L), model.state.value.items.map { it.id })
        assertEquals(2, model.state.value.stats.total)
    }

    @Test fun addingAndEditingNormalizeInputAndPersistTheExpectedTask() = runTest {
        val repo = FakeRepository()
        val model = model(repo)
        runCurrent()
        assertTrue(model.save(null, "  Write \n plan  ", "  notes  ", 20_001L, TodoPriority.HIGH))
        assertNull(model.state.value.editError)
        advanceUntilIdle()
        assertEquals(1, repo.inserts)
        val added = repo.items.value.single()
        assertEquals("Write plan", added.title)
        assertEquals("notes", added.notes)
        assertEquals(20_001L, added.dueDateEpochDay)
        assertEquals(TodoPriority.HIGH, added.priority)
        assertEquals(TodoMessage.ADDED, model.state.value.message)

        model.consumeMessage()
        assertTrue(model.save(added.id, "Updated", "", null, TodoPriority.LOW))
        advanceUntilIdle()
        assertEquals(1, repo.updates)
        assertEquals("Updated", model.state.value.items.single().title)
        assertEquals(TodoPriority.LOW, model.state.value.items.single().priority)
        assertEquals(TodoMessage.UPDATED, model.state.value.message)
    }

    @Test fun validationAndFiveHundredTaskLimitRejectBeforeWriting() = runTest {
        val repo = FakeRepository()
        val model = model(repo)
        runCurrent()
        assertFalse(model.save(null, "   ", "", null, TodoPriority.NORMAL))
        assertEquals(TodoError.TITLE, model.state.value.editError)
        assertFalse(model.save(null, "ok", "x".repeat(TodoPolicy.MAX_NOTES_CODE_POINTS + 1), null, TodoPriority.NORMAL))
        assertEquals(TodoError.NOTES, model.state.value.editError)
        model.clearEditError()
        assertNull(model.state.value.editError)

        repo.items.value = List(TodoPolicy.MAX_TASKS) { task(it + 1L) }
        runCurrent()
        assertFalse(model.save(null, "One more", "", null, TodoPriority.NORMAL))
        assertEquals(TodoError.LIMIT, model.state.value.editError)
        assertEquals(0, repo.inserts)
    }

    @Test fun toggleDeleteAndClearCompletedUseTheRepository() = runTest {
        val repo = FakeRepository(listOf(task(1, created = 1), task(2, completed = true, created = 2)))
        val model = model(repo)
        runCurrent()
        model.toggle(model.state.value.items.first { it.id == 1L })
        advanceUntilIdle()
        assertTrue(repo.items.value.first { it.id == 1L }.completed)
        assertEquals(repo.now, repo.items.value.first { it.id == 1L }.completedAt)

        model.delete(2L)
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.items.value.map { it.id })
        assertEquals(TodoMessage.DELETED, model.state.value.message)

        model.consumeMessage()
        model.deleteCompleted()
        advanceUntilIdle()
        assertTrue(repo.items.value.isEmpty())
        assertEquals(TodoMessage.CLEARED, model.state.value.message)
    }

    @Test fun storageFailuresAreReportedWithoutDroppingTheCollectedData() = runTest {
        val repo = FakeRepository(listOf(task(1, title = "Existing")))
        val model = model(repo)
        runCurrent()
        repo.failInsert = true
        assertTrue(model.save(null, "New", "", null, TodoPriority.NORMAL))
        advanceUntilIdle()
        assertEquals(TodoMessage.STORAGE_FAILED, model.state.value.message)
        assertEquals(listOf("Existing"), model.state.value.items.map { it.title })

        model.consumeMessage()
        repo.failUpdate = true
        model.toggle(model.state.value.items.single())
        advanceUntilIdle()
        assertEquals(TodoMessage.STORAGE_FAILED, model.state.value.message)
        assertFalse(repo.items.value.single().completed)
    }

    @Test fun queryFilterAndSortUpdatesSurviveRecreation() = runTest {
        val saved = SavedStateHandle()
        val repo = FakeRepository(listOf(task(1, "Alpha"), task(2, "Beta", completed = true)))
        val first = model(repo, saved)
        runCurrent()
        first.setFilter(TodoFilter.COMPLETED)
        first.setSort(TodoSort.CREATED_DESC)
        first.setQuery("beta")
        assertEquals(listOf(2L), first.state.value.items.map { it.id })
        assertEquals(TodoFilter.COMPLETED.name, saved.get<String>("todoFilter"))

        val second = model(repo, saved)
        runCurrent()
        assertEquals(TodoFilter.COMPLETED, second.state.value.filter)
        assertEquals(TodoSort.CREATED_DESC, second.state.value.sort)
        assertEquals("beta", second.state.value.query)
        assertEquals(listOf(2L), second.state.value.items.map { it.id })
        assertFalse(second.save(999L, "Missing", "", null, TodoPriority.NORMAL))
    }
}
