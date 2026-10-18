package com.cangshuo.toolbox.feature.period

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.period.domain.PeriodCycle
import com.cangshuo.toolbox.feature.period.domain.PeriodError
import com.cangshuo.toolbox.feature.period.domain.PeriodFlow
import com.cangshuo.toolbox.feature.period.domain.PeriodRepository
import com.cangshuo.toolbox.feature.period.domain.PeriodSettings
import com.cangshuo.toolbox.feature.period.domain.PeriodSettingsRepository
import com.cangshuo.toolbox.feature.period.domain.PeriodSymptom
import com.cangshuo.toolbox.feature.period.domain.PeriodUseCases
import com.cangshuo.toolbox.feature.period.ui.PeriodMessage
import com.cangshuo.toolbox.feature.period.ui.PeriodViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 18).toEpochDay()

    private class FakeRepository(initial: List<PeriodCycle>, val today: Long) : PeriodRepository {
        val flow = MutableStateFlow(initial)
        var inserted = 0
        var updated = 0
        var deleted = 0
        override fun observeAll() = flow
        override suspend fun find(id: Long) = flow.value.firstOrNull { it.id == id }
        override suspend fun insert(cycle: PeriodCycle): Long {
            inserted++
            val id = (flow.value.maxOfOrNull { it.id } ?: 0L) + 1L
            flow.value = flow.value + cycle.copy(id = id)
            return id
        }
        override suspend fun update(cycle: PeriodCycle) {
            updated++
            flow.value = flow.value.map { if (it.id == cycle.id) cycle else it }
        }
        override suspend fun delete(id: Long) {
            deleted++
            flow.value = flow.value.filterNot { it.id == id }
        }
        override fun nowMillis() = 100L + inserted + updated
        override fun todayEpochDay() = today
    }

    private class FakeSettings : PeriodSettingsRepository {
        val mutable = MutableStateFlow(PeriodSettings())
        override val settings: StateFlow<PeriodSettings> = mutable
        override fun update(settings: PeriodSettings) { mutable.value = settings }
    }

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun editSaveNavigateAndSettingsDelegate() = runTest {
        val oldStart = LocalDate.of(2026, 9, 20).toEpochDay()
        val repo = FakeRepository(
            listOf(
                PeriodCycle(1, oldStart, oldStart + 4, PeriodFlow.MEDIUM, emptySet(), null, "", 1, 1),
            ),
            today,
        )
        val settings = FakeSettings()
        val model = PeriodViewModel(PeriodUseCases(repo, settings), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()
        assertEquals(1, model.state.value.cycles.size)
        assertEquals(29, model.state.value.insights.currentCycleDay)

        model.selectDay(oldStart + 2)
        model.openEditorForSelectedDay()
        assertEquals(PeriodFlow.MEDIUM, model.state.value.editor?.flow)
        model.setFlow(PeriodFlow.HEAVY)
        model.toggleSymptom(PeriodSymptom.CRAMPS)
        model.saveEditor()
        runCurrent()
        assertEquals(1, repo.updated)
        assertEquals(PeriodMessage.SAVED, model.state.value.message)
        assertTrue(model.state.value.editor == null)

        model.selectDay(today)
        model.openEditorForSelectedDay()
        assertNotNull(model.state.value.editor)
        model.saveEditor()
        runCurrent()
        assertEquals(1, repo.inserted)

        val month = model.state.value.visibleMonth
        model.previousMonth()
        assertEquals(month.minusMonths(1), model.state.value.visibleMonth)
        model.goToday()
        assertEquals(today, model.state.value.selectedEpochDay)

        model.openSettings()
        model.setCycleDays("30")
        model.setPeriodDays("4")
        model.saveSettings()
        runCurrent()
        assertEquals(PeriodSettings(30, 4), settings.settings.value)
        assertEquals(PeriodMessage.SETTINGS_SAVED, model.state.value.message)
        advanceUntilIdle()
    }

    @Test fun longNotesAreRejectedWithoutTruncationAndDeleteIsDelegated() = runTest {
        val start = today - 4
        val repo = FakeRepository(
            listOf(PeriodCycle(1, start, today - 2, PeriodFlow.MEDIUM, emptySet(), null, "", 1, 1)),
            today,
        )
        val model = PeriodViewModel(PeriodUseCases(repo, FakeSettings()), SavedStateHandle())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        runCurrent()

        model.editCycle(1)
        model.setEditorStart(today - 1)
        assertEquals(PeriodError.DATE_ORDER, model.state.value.editor?.error)
        assertEquals(today - 2, model.state.value.editor?.endEpochDay)
        assertEquals(start, model.state.value.editor?.startEpochDay)
        val note = "\uD83D\uDE00".repeat(501)
        model.setNotes(note)
        assertEquals(note, model.state.value.editor?.notes)
        model.saveEditor()
        runCurrent()
        assertEquals(PeriodError.NOTES, model.state.value.editor?.error)
        assertEquals(0, repo.updated)

        model.setNotes("updated")
        model.saveEditor()
        runCurrent()
        assertEquals(1, repo.updated)
        model.openEditorForSelectedDay()
        model.deleteEditorCycle()
        runCurrent()
        assertEquals(1, repo.deleted)
        assertEquals(PeriodMessage.DELETED, model.state.value.message)
        advanceUntilIdle()
    }
}
