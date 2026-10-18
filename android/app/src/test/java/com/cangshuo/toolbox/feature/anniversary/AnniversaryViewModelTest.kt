package com.cangshuo.toolbox.feature.anniversary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryCategory
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryError
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryEvent
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryFilter
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLimitException
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryLunarInfo
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryPolicy
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepeat
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryRepository
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversarySort
import com.cangshuo.toolbox.feature.anniversary.domain.AnniversaryUseCases
import com.cangshuo.toolbox.feature.anniversary.ui.AnniversaryMessage
import com.cangshuo.toolbox.feature.anniversary.ui.AnniversaryViewModel
import java.io.IOException
import java.time.LocalDate
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
class AnniversaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelKey = 0
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private class FakeRepository(initial: List<AnniversaryEvent> = emptyList()) : AnniversaryRepository {
        val events = MutableStateFlow(initial)
        var now = 1_000L
        var todayDay = LocalDate.of(2026, 10, 18).toEpochDay()
        var failWrite = false
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun observeAll(): Flow<List<AnniversaryEvent>> = events
        override suspend fun insert(event: AnniversaryEvent): Long {
            if (failWrite) throw IOException("write")
            if (events.value.size >= AnniversaryPolicy.MAX_EVENTS) throw AnniversaryLimitException()
            val id = nextId++
            events.value = events.value + event.copy(id = id)
            return id
        }
        override suspend fun update(event: AnniversaryEvent) {
            if (failWrite) throw IOException("write")
            events.value = events.value.map { if (it.id == event.id) event else it }
        }
        override suspend fun delete(id: Long) {
            if (failWrite) throw IOException("write")
            events.value = events.value.filterNot { it.id == id }
        }
        override suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long) {
            if (failWrite) throw IOException("write")
            events.value = events.value.map { if (it.id == id) it.copy(pinned = pinned, updatedAt = updatedAt) else it }
        }
        override fun lunarInfo(date: LocalDate) = AnniversaryLunarInfo("九", "九", false, "马")
        override fun nowMillis(): Long = now
        override fun todayEpochDay(): Long = todayDay
    }

    private fun model(repo: FakeRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        AnniversaryViewModel(AnniversaryUseCases(repo), saved).also { models.put("anniversary-${modelKey++}", it) }

    private fun event(
        id: Long,
        title: String = "Event $id",
        date: LocalDate = LocalDate.of(2020, 10, 18),
        repeat: AnniversaryRepeat = AnniversaryRepeat.YEARLY,
        pinned: Boolean = false,
        created: Long = id,
    ) = AnniversaryEvent(id, title, "", date.toEpochDay(), repeat, AnniversaryCategory.OTHER, pinned, created, created)

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    @Test fun initialAndSavedViewStateAreRestored() = runTest {
        val repo = FakeRepository(listOf(event(1, "Birthday"), event(2, "Past", date = LocalDate.of(2020, 1, 1), repeat = AnniversaryRepeat.NONE)))
        val saved = SavedStateHandle(
            mapOf(
                "anniversaryFilter" to AnniversaryFilter.PAST.name,
                "anniversarySort" to AnniversarySort.NAME.name,
                "anniversaryQuery" to "past",
            ),
        )
        val model = model(repo, saved)
        assertTrue(model.state.value.loading)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals(listOf(2L), model.state.value.items.map { it.id })
        assertEquals(AnniversaryFilter.PAST, model.state.value.filter)
        assertEquals(AnniversarySort.NAME, model.state.value.sort)
        assertEquals(today, model.state.value.todayEpochDay)
    }

    @Test fun createEditNormalizeAndPreserveDraftOnFailure() = runTest {
        val repo = FakeRepository(listOf(event(1, "Old")))
        val model = model(repo)
        runCurrent()
        assertTrue(model.save(null, "  Family \n day  ", "  notes  ", today, AnniversaryRepeat.YEARLY, AnniversaryCategory.FAMILY, true))
        assertFalse(model.save(null, "Duplicate", "", today, AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
        advanceUntilIdle()
        assertEquals(2, repo.events.value.size)
        assertEquals("Family day", repo.events.value.last().title)
        assertEquals("notes", repo.events.value.last().notes)
        assertEquals(AnniversaryMessage.ADDED, model.state.value.message)
        assertEquals(1L, model.state.value.saveSuccessRevision)

        model.consumeMessage()
        assertTrue(model.save(1L, "Updated", "", today, AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
        advanceUntilIdle()
        assertEquals("Updated", repo.events.value.first().title)
        assertEquals(AnniversaryMessage.UPDATED, model.state.value.message)

        repo.failWrite = true
        assertTrue(model.save(1L, "Keep this draft", "body", today, AnniversaryRepeat.YEARLY, AnniversaryCategory.OTHER, false))
        advanceUntilIdle()
        assertEquals(AnniversaryError.STORAGE, model.state.value.editError)
        assertFalse(model.state.value.saving)
        assertEquals(2L, model.state.value.saveSuccessRevision)
    }

    @Test fun validationAndLimitRejectBeforeWriting() = runTest {
        val repo = FakeRepository()
        val model = model(repo)
        runCurrent()
        assertFalse(model.save(null, " ", "", today, AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
        assertEquals(AnniversaryError.TITLE, model.state.value.editError)
        assertFalse(model.save(null, "ok", "x".repeat(501), today, AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
        assertEquals(AnniversaryError.NOTES, model.state.value.editError)
        assertFalse(model.save(null, "ok", "", LocalDate.of(1899, 12, 31).toEpochDay(), AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
        assertEquals(AnniversaryError.DATE, model.state.value.editError)
        model.clearEditError()
        assertNull(model.state.value.editError)
        repo.events.value = List(AnniversaryPolicy.MAX_EVENTS) { event(it + 1L) }
        runCurrent()
        assertFalse(model.save(null, "One more", "", today, AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
        assertEquals(AnniversaryError.LIMIT, model.state.value.editError)
    }

    @Test fun pinDeleteRefreshAndLunarLookupWork() = runTest {
        val repo = FakeRepository(listOf(event(1), event(2)))
        val model = model(repo)
        runCurrent()
        model.setPinned(model.state.value.items.first { it.id == 1L }, true)
        assertTrue(1L in model.state.value.busyEventIds)
        advanceUntilIdle()
        assertTrue(repo.events.value.first { it.id == 1L }.pinned)
        assertEquals(AnniversaryMessage.PINNED, model.state.value.message)
        assertTrue(model.state.value.busyEventIds.isEmpty())

        model.consumeMessage()
        model.delete(repo.events.value.first { it.id == 2L })
        advanceUntilIdle()
        assertEquals(listOf(1L), repo.events.value.map { it.id })
        assertEquals(AnniversaryMessage.DELETED, model.state.value.message)
        assertEquals("马", model.lunarInfo(LocalDate.of(2026, 10, 18))?.zodiac)

        repo.todayDay = today + 1
        model.refreshToday()
        assertEquals(today + 1, model.state.value.todayEpochDay)
    }

    @Test fun pinFailureReportsStorageAndMissingEditReturnsFalse() = runTest {
        val repo = FakeRepository(listOf(event(1)))
        val model = model(repo)
        runCurrent()
        repo.failWrite = true
        model.setPinned(model.state.value.items.single(), true)
        advanceUntilIdle()
        assertEquals(AnniversaryMessage.STORAGE_FAILED, model.state.value.message)
        assertFalse(model.save(999L, "Missing", "", today, AnniversaryRepeat.NONE, AnniversaryCategory.OTHER, false))
    }
}
