package com.cangshuo.toolbox.feature.datecalc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.datecalc.data.LocalDateCalcRepository
import com.cangshuo.toolbox.feature.datecalc.domain.CalculateDateUseCase
import com.cangshuo.toolbox.feature.datecalc.domain.DateCalcError
import com.cangshuo.toolbox.feature.datecalc.domain.DateDiffOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateDirection
import com.cangshuo.toolbox.feature.datecalc.domain.DateShiftOutcome
import com.cangshuo.toolbox.feature.datecalc.domain.DateUnit
import com.cangshuo.toolbox.feature.datecalc.ui.DateCalcViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DateCalcViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) = DateCalcViewModel(
        CalculateDateUseCase(LocalDateCalcRepository()),
        handle,
        today = { LocalDate.of(2026, 10, 12) },
    )

    @Test
    fun differenceNeedsBothDatesAndDistinguishesInvalidFromOutOfRange() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        assertNull(viewModel.uiState.value.diffOutcome)
        viewModel.updateFirst("2026-10-01")
        runCurrent()
        assertNull(viewModel.uiState.value.diffOutcome)
        viewModel.updateSecond("2026-13-01")
        runCurrent()
        assertEquals(
            DateCalcError.INVALID_DATE,
            (viewModel.uiState.value.diffOutcome as DateDiffOutcome.Failure).error,
        )
        viewModel.updateSecond("1899-12-31")
        runCurrent()
        assertEquals(
            DateCalcError.OUT_OF_RANGE,
            (viewModel.uiState.value.diffOutcome as DateDiffOutcome.Failure).error,
        )
        viewModel.updateSecond("2026/10/31")
        runCurrent()
        val result = (viewModel.uiState.value.diffOutcome as DateDiffOutcome.Success).result
        assertEquals(30, result.totalDays)
        assertEquals(4, result.weeks)
        assertEquals(2, result.remainderDays)
    }

    @Test
    fun oversizedPasteKeepsThePreviousValueAndShowsLengthError() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("first" to "2026-10-01", "second" to "2026-10-31")))
        observe(viewModel, viewModel.uiState)
        assertTrue(viewModel.uiState.value.diffOutcome is DateDiffOutcome.Success)
        viewModel.updateFirst("1".repeat(13))
        runCurrent()
        assertEquals("2026-10-01", viewModel.uiState.value.firstText)
        assertEquals(
            DateCalcError.INPUT_TOO_LONG,
            (viewModel.uiState.value.diffOutcome as DateDiffOutcome.Failure).error,
        )
        viewModel.updateFirst("2026-10-02")
        runCurrent()
        assertTrue(viewModel.uiState.value.diffOutcome is DateDiffOutcome.Success)
    }

    @Test
    fun shiftUsesSelectedUnitAndDirectionWithRangeGuards() = runTest {
        val viewModel = model(SavedStateHandle(mapOf("base" to "2026-01-31", "amount" to "1", "unit" to "MONTHS")))
        observe(viewModel, viewModel.uiState)
        assertEquals(
            LocalDate.of(2026, 2, 28),
            (viewModel.uiState.value.shiftOutcome as DateShiftOutcome.Success).result.result,
        )
        viewModel.selectDirection(DateDirection.BEFORE)
        runCurrent()
        assertEquals(
            LocalDate.of(2025, 12, 31),
            (viewModel.uiState.value.shiftOutcome as DateShiftOutcome.Success).result.result,
        )
        viewModel.updateAmount("100001")
        runCurrent()
        assertEquals(
            DateCalcError.AMOUNT_OUT_OF_RANGE,
            (viewModel.uiState.value.shiftOutcome as DateShiftOutcome.Failure).error,
        )
        viewModel.updateAmount("10")
        viewModel.updateBase("2999-12-31")
        viewModel.selectUnit(DateUnit.YEARS)
        viewModel.selectDirection(DateDirection.AFTER)
        runCurrent()
        assertEquals(
            DateCalcError.RESULT_OUT_OF_RANGE,
            (viewModel.uiState.value.shiftOutcome as DateShiftOutcome.Failure).error,
        )
        viewModel.clear()
        runCurrent()
        assertNull(viewModel.uiState.value.shiftOutcome)
        assertEquals("", viewModel.uiState.value.baseText)
    }

    @Test
    fun todayButtonsFillBothSectionsFromTheInjectedClock() = runTest {
        val viewModel = model()
        observe(viewModel, viewModel.uiState)
        viewModel.fillFirstToday()
        viewModel.fillBaseToday()
        runCurrent()
        assertEquals("2026-10-12", viewModel.uiState.value.firstText)
        assertEquals("2026-10-12", viewModel.uiState.value.baseText)
    }
}
