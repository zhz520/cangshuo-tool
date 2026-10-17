package com.cangshuo.toolbox.feature.habit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.habit.domain.Habit
import com.cangshuo.toolbox.feature.habit.domain.HabitError
import com.cangshuo.toolbox.feature.habit.domain.HabitFilter
import com.cangshuo.toolbox.feature.habit.domain.HabitLimitException
import com.cangshuo.toolbox.feature.habit.domain.HabitPolicy
import com.cangshuo.toolbox.feature.habit.domain.HabitRepository
import com.cangshuo.toolbox.feature.habit.domain.HabitSort
import com.cangshuo.toolbox.feature.habit.domain.HabitUseCases
import com.cangshuo.toolbox.feature.habit.ui.HabitMessage
import com.cangshuo.toolbox.feature.habit.ui.HabitViewModel
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
class HabitViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelKey = 0
    private val today = 20_000L

    private class FakeRepository(initial: List<Habit> = emptyList()) : HabitRepository {
        val habits = MutableStateFlow(initial)
        var now = 1_000L
        var todayDay = 20_000L
        var failWrite = false
        var entryCount = initial.sumOf { it.checkedDates.size }
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun observeAll(): Flow<List<Habit>> = habits
        override suspend fun insert(habit: Habit): Long {
            if (failWrite) throw IOException("write")
            if (habits.value.size >= HabitPolicy.MAX_HABITS) throw HabitLimitException()
            val id = nextId++
            habits.value = habits.value + habit.copy(id = id)
            return id
        }
        override suspend fun update(habit: Habit) {
            if (failWrite) throw IOException("write")
            habits.value = habits.value.map { if (it.id == habit.id) habit else it }
        }
        override suspend fun delete(id: Long) {
            if (failWrite) throw IOException("write")
            entryCount -= habits.value.firstOrNull { it.id == id }?.checkedDates?.size ?: 0
            habits.value = habits.value.filterNot { it.id == id }
        }
        override suspend fun setArchived(id: Long, archived: Boolean, archivedDay: Long?, updatedAt: Long) {
            if (failWrite) throw IOException("write")
            habits.value = habits.value.map {
                if (it.id == id) it.copy(archived = archived, archivedDay = archivedDay, updatedAt = updatedAt) else it
            }
        }
        override suspend fun addEntry(habitId: Long, dateEpochDay: Long, checkedAt: Long): Boolean {
            if (failWrite) throw IOException("write")
            if (entryCount >= HabitPolicy.MAX_ENTRIES) return false
            val current = habits.value.firstOrNull { it.id == habitId && !it.archived } ?: return false
            if (dateEpochDay in current.checkedDates) return true
            habits.value = habits.value.map {
                if (it.id == habitId && dateEpochDay !in it.checkedDates) {
                    entryCount++
                    it.copy(checkedDates = it.checkedDates + dateEpochDay)
                } else it
            }
            return true
        }
        override suspend fun removeEntry(habitId: Long, dateEpochDay: Long) {
            if (failWrite) throw IOException("write")
            habits.value = habits.value.map {
                if (it.id == habitId && dateEpochDay in it.checkedDates) {
                    entryCount--
                    it.copy(checkedDates = it.checkedDates - dateEpochDay)
                } else it
            }
        }
        override suspend fun clearEntries(habitId: Long): Int {
            if (failWrite) throw IOException("write")
            val removed = habits.value.firstOrNull { it.id == habitId }?.checkedDates?.size ?: 0
            entryCount -= removed
            habits.value = habits.value.map { if (it.id == habitId) it.copy(checkedDates = emptySet()) else it }
            return removed
        }
        override fun nowMillis(): Long = now
        override fun todayEpochDay(): Long = todayDay
    }

    private fun model(repo: FakeRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        HabitViewModel(HabitUseCases(repo), saved).also { models.put("habit-${modelKey++}", it) }

    private fun habit(
        id: Long,
        name: String = "Habit $id",
        mask: Int = HabitWeekday.Monday.bit,
        archived: Boolean = false,
        archivedDay: Long? = if (archived) today - 1 else null,
        created: Long = id,
        checked: Set<Long> = emptySet(),
    ) = Habit(
        id = id,
        name = name,
        description = "",
        weekdaysMask = mask,
        archived = archived,
        archivedDay = archivedDay,
        position = id.toInt(),
        createdDay = today - 20,
        createdAt = created,
        updatedAt = created,
        checkedDates = checked,
    )

    private enum class HabitWeekday(val bit: Int) { Monday(1) }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun initialCollectionAndSavedViewStateAreRestored() = runTest {
        val repo = FakeRepository(listOf(habit(1, "Read"), habit(2, "Archived", archived = true)))
        val saved = SavedStateHandle(
            mapOf(
                "habitFilter" to HabitFilter.ARCHIVED.name,
                "habitSort" to HabitSort.NAME.name,
                "habitQuery" to "arch",
            ),
        )
        val model = model(repo, saved)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertEquals(HabitFilter.ARCHIVED, model.state.value.filter)
        assertEquals(HabitSort.NAME, model.state.value.sort)
        assertEquals(today, model.state.value.todayEpochDay)
    }

    @Test fun createAndEditNormalizeInputAndPreserveHistory() = runTest {
        val repo = FakeRepository(listOf(habit(1, "Old", checked = setOf(today))))
        val model = model(repo)
        runCurrent()
        assertTrue(model.save(null, "  Drink \n water  ", "  two liters  ", HabitPolicy.ALL_DAYS_MASK))
        advanceUntilIdle()
        val created = repo.habits.value.last()
        assertEquals("Drink water", created.name)
        assertEquals("two liters", created.description)
        assertEquals(HabitMessage.CREATED, model.state.value.message)
        assertFalse(model.state.value.saving)
        assertEquals(1L, model.state.value.saveSuccessRevision)

        model.consumeMessage()
        assertTrue(model.save(1L, "Updated", "", HabitWeekday.Monday.bit))
        advanceUntilIdle()
        assertEquals("Updated", repo.habits.value.first().name)
        assertEquals(setOf(today), repo.habits.value.first().checkedDates)
        assertEquals(HabitMessage.UPDATED, model.state.value.message)
        assertEquals(2L, model.state.value.saveSuccessRevision)
    }

    @Test fun invalidInputAndHundredHabitLimitAreRejectedBeforeWriting() = runTest {
        val repo = FakeRepository()
        val model = model(repo)
        runCurrent()
        assertFalse(model.save(null, " ", "", 1))
        assertEquals(HabitError.NAME, model.state.value.editError)
        assertFalse(model.save(null, "ok", "", 0))
        assertEquals(HabitError.WEEKDAYS, model.state.value.editError)
        model.clearEditError()
        assertNull(model.state.value.editError)
        repo.habits.value = List(HabitPolicy.MAX_HABITS) { habit(it + 1L) }
        runCurrent()
        assertFalse(model.save(null, "One more", "", 1))
        assertEquals(HabitError.LIMIT, model.state.value.editError)
        assertEquals(HabitPolicy.MAX_HABITS, repo.habits.value.size)
    }

    @Test fun todayAndHistoricalCheckInsCanBeToggledWithinBounds() = runTest {
        val repo = FakeRepository(listOf(habit(1)))
        val model = model(repo)
        runCurrent()
        val item = model.state.value.items.single()
        model.toggleDate(item, today)
        assertTrue(1L in model.state.value.busyHabitIds)
        advanceUntilIdle()
        assertEquals(setOf(today), repo.habits.value.single().checkedDates)
        assertEquals(HabitMessage.CHECKED, model.state.value.message)
        assertTrue(model.state.value.busyHabitIds.isEmpty())

        model.consumeMessage()
        val checked = model.state.value.items.single()
        model.toggleDate(checked, today)
        advanceUntilIdle()
        assertTrue(repo.habits.value.single().checkedDates.isEmpty())
        assertEquals(HabitMessage.UNCHECKED, model.state.value.message)

        val before = model.state.value
        model.toggleDate(model.state.value.items.single(), today + 1)
        assertEquals(before, model.state.value)
        assertFalse(model.save(999L, "Missing", "", 1))
    }

    @Test fun entryLimitArchiveClearAndDeleteAreEnforced() = runTest {
        val repo = FakeRepository(listOf(habit(1, checked = setOf(today - 2)), habit(2)))
        val model = model(repo)
        runCurrent()
        repo.entryCount = HabitPolicy.MAX_ENTRIES
        model.toggleDate(model.state.value.items.first { it.id == 2L }, today)
        advanceUntilIdle()
        assertEquals(HabitMessage.ENTRY_LIMIT, model.state.value.message)

        model.consumeMessage()
        model.setArchived(model.state.value.items.first { it.id == 1L }, true)
        advanceUntilIdle()
        assertTrue(repo.habits.value.first { it.id == 1L }.archived)
        assertEquals(HabitMessage.ARCHIVED, model.state.value.message)
        val revision = model.state.value.saveSuccessRevision
        assertFalse(model.save(1L, "Blocked", "", 1))
        assertEquals(HabitError.ARCHIVED, model.state.value.editError)
        assertEquals(revision, model.state.value.saveSuccessRevision)

        model.consumeMessage()
        model.clearEditError()
        model.setFilter(HabitFilter.ARCHIVED)
        model.setArchived(model.state.value.items.first { it.id == 1L }, false)
        advanceUntilIdle()
        assertFalse(repo.habits.value.first { it.id == 1L }.archived)
        assertEquals(HabitFilter.ALL, model.state.value.filter)
        assertEquals(HabitMessage.RESTORED, model.state.value.message)

        model.consumeMessage()
        model.clearHistory(model.state.value.items.first { it.id == 1L })
        advanceUntilIdle()
        assertTrue(repo.habits.value.first { it.id == 1L }.checkedDates.isEmpty())
        assertEquals(HabitMessage.HISTORY_CLEARED, model.state.value.message)

        model.consumeMessage()
        model.delete(repo.habits.value.first { it.id == 2L })
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.habits.value.map { it.id })
        assertEquals(HabitMessage.DELETED, model.state.value.message)
    }

    @Test fun storageFailuresAreReportedAndBusyStateClears() = runTest {
        val repo = FakeRepository(listOf(habit(1)))
        val model = model(repo)
        runCurrent()
        repo.failWrite = true
        assertTrue(model.save(null, "New", "", 1))
        advanceUntilIdle()
        assertEquals(HabitError.STORAGE, model.state.value.editError)
        assertEquals(0L, model.state.value.saveSuccessRevision)
        assertFalse(model.state.value.saving)
        model.clearEditError()
        model.toggleDate(model.state.value.items.single(), today)
        advanceUntilIdle()
        assertEquals(HabitMessage.STORAGE_FAILED, model.state.value.message)
        assertTrue(model.state.value.busyHabitIds.isEmpty())
    }

    @Test fun filterSortAndSearchSurviveRecreation() = runTest {
        val repo = FakeRepository(listOf(habit(1, "Read"), habit(2, "Archived", archived = true)))
        val saved = SavedStateHandle()
        val first = model(repo, saved)
        runCurrent()
        first.setFilter(HabitFilter.ARCHIVED)
        first.setSort(HabitSort.NAME)
        first.setQuery("arch")
        assertEquals(listOf(2L), first.state.value.items.map { it.id })
        val second = model(repo, saved)
        runCurrent()
        assertEquals(HabitFilter.ARCHIVED, second.state.value.filter)
        assertEquals(HabitSort.NAME, second.state.value.sort)
        assertEquals("arch", second.state.value.query)
    }

    @Test fun refreshTodayRebasesDateSensitiveState() = runTest {
        val repo = FakeRepository(listOf(habit(1, mask = HabitPolicy.ALL_DAYS_MASK)))
        val model = model(repo)
        runCurrent()
        assertEquals(today, model.state.value.todayEpochDay)
        repo.todayDay = today + 1
        model.refreshToday()
        assertEquals(today + 1, model.state.value.todayEpochDay)
    }
}
