package com.cangshuo.toolbox.feature.calculations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.cangshuo.toolbox.feature.bmi.data.LocalBmiRepository
import com.cangshuo.toolbox.feature.bmi.domain.BmiError
import com.cangshuo.toolbox.feature.bmi.domain.BmiOutcome
import com.cangshuo.toolbox.feature.bmi.domain.CalculateBmiUseCase
import com.cangshuo.toolbox.feature.bmi.ui.BmiViewModel
import com.cangshuo.toolbox.feature.compound.data.LocalCompoundRepository
import com.cangshuo.toolbox.feature.compound.domain.CalculateCompoundUseCase
import com.cangshuo.toolbox.feature.compound.domain.CompoundError
import com.cangshuo.toolbox.feature.compound.domain.CompoundOutcome
import com.cangshuo.toolbox.feature.compound.ui.CompoundViewModel
import com.cangshuo.toolbox.feature.fuel.data.LocalFuelRepository
import com.cangshuo.toolbox.feature.fuel.domain.CalculateFuelUseCase
import com.cangshuo.toolbox.feature.fuel.domain.FuelError
import com.cangshuo.toolbox.feature.fuel.domain.FuelOutcome
import com.cangshuo.toolbox.feature.fuel.ui.FuelViewModel
import com.cangshuo.toolbox.feature.mortgage.data.LocalMortgageRepository
import com.cangshuo.toolbox.feature.mortgage.domain.CalculateMortgageUseCase
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageError
import com.cangshuo.toolbox.feature.mortgage.domain.MortgageOutcome
import com.cangshuo.toolbox.feature.mortgage.ui.MortgageViewModel
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
class CalculationViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()

    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { models.clear(); Dispatchers.resetMain() }

    private fun TestScope.observe(model: ViewModel, state: Flow<*>) {
        models.put(model.javaClass.name, model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
        runCurrent()
    }

    @Test
    fun bmiSavedFormAndOversizedPasteCanRecoverWithoutLosingInput() = runTest {
        val model = BmiViewModel(CalculateBmiUseCase(LocalBmiRepository()), SavedStateHandle(mapOf("height" to "170", "weight" to "65")))
        observe(model, model.uiState)
        assertTrue(model.uiState.value.outcome is BmiOutcome.Success)
        model.updateHeight("1".repeat(13)); runCurrent()
        assertEquals("170", model.uiState.value.heightText)
        assertEquals(BmiError.INPUT_TOO_LONG, (model.uiState.value.outcome as BmiOutcome.Failure).error)
        model.selectUnits(true); model.updateHeight("67"); model.updateWeight("143"); runCurrent()
        assertTrue(model.uiState.value.outcome is BmiOutcome.Success)
        model.clear(); runCurrent()
        assertNull(model.uiState.value.outcome)
    }

    @Test
    fun compoundLimitIsVisibleAndValidInputRestoresResults() = runTest {
        val model = CompoundViewModel(CalculateCompoundUseCase(LocalCompoundRepository()), SavedStateHandle(mapOf("principal" to "10000", "rate" to "1000", "years" to "100")))
        observe(model, model.uiState)
        assertEquals(CompoundError.RESULT_LIMIT, (model.uiState.value.outcome as CompoundOutcome.Failure).error)
        model.updateYears("1"); runCurrent()
        assertTrue(model.uiState.value.outcome is CompoundOutcome.Success)
        model.updatePrincipal("1".repeat(25)); runCurrent()
        assertEquals("10000", model.uiState.value.principalText)
        assertEquals(CompoundError.INPUT_TOO_LONG, (model.uiState.value.outcome as CompoundOutcome.Failure).error)
        model.clear(); runCurrent()
        assertNull(model.uiState.value.outcome)
    }

    @Test
    fun mortgageRejectsFractionalAndOverflowedTermsWithoutCrashing() = runTest {
        val model = MortgageViewModel(CalculateMortgageUseCase(LocalMortgageRepository()), SavedStateHandle(mapOf("amount" to "100", "rate" to "0.00000000000000001", "years" to "30", "showAll" to true)))
        observe(model, model.uiState)
        assertTrue(model.uiState.value.outcome is MortgageOutcome.Success)
        model.updateYears("1.00000000000000000001"); runCurrent()
        assertEquals(MortgageError.INVALID, (model.uiState.value.outcome as MortgageOutcome.Failure).error)
        model.updateYears("999999999999999999999"); runCurrent()
        assertEquals(MortgageError.RANGE, (model.uiState.value.outcome as MortgageOutcome.Failure).error)
        model.updateAmount("1".repeat(25)); runCurrent()
        assertEquals("100", model.uiState.value.amountText)
        assertEquals(MortgageError.INPUT_TOO_LONG, (model.uiState.value.outcome as MortgageOutcome.Failure).error)
        model.updateYears("10"); runCurrent()
        assertEquals(120, (model.uiState.value.outcome as MortgageOutcome.Success).result.months)
        model.clear(); runCurrent()
        assertNull(model.uiState.value.outcome)
    }

    @Test
    fun fuelLatestValuesAndOptionalPriceControlTheCostOutput() = runTest {
        val model = FuelViewModel(CalculateFuelUseCase(LocalFuelRepository()), SavedStateHandle())
        observe(model, model.uiState)
        model.updateDistance("100"); model.updateFuel("10"); model.updateDistance("520"); model.updateFuel("38"); model.updatePrice("7.85"); runCurrent()
        assertEquals(298.3, (model.uiState.value.outcome as FuelOutcome.Success).result.totalCost!!, 0.001)
        model.updateFuel("1".repeat(25)); runCurrent()
        assertEquals("38", model.uiState.value.fuelText)
        assertEquals(FuelError.INPUT_TOO_LONG, (model.uiState.value.outcome as FuelOutcome.Failure).error)
        model.updatePrice(""); runCurrent()
        assertNull((model.uiState.value.outcome as FuelOutcome.Success).result.totalCost)
        model.clear(); runCurrent()
        assertNull(model.uiState.value.outcome)
    }

    @Test
    fun compoundRejectsAlmostIntegerYearsBeforeRoundingToDouble() = runTest {
        val model = CompoundViewModel(CalculateCompoundUseCase(LocalCompoundRepository()), SavedStateHandle(mapOf("principal" to "1000", "rate" to "5", "years" to "1.00000000000000000001")))
        observe(model, model.uiState)
        assertEquals(CompoundError.INVALID, (model.uiState.value.outcome as CompoundOutcome.Failure).error)
        model.updateYears("999999999999999999999"); runCurrent()
        assertEquals(CompoundError.RANGE, (model.uiState.value.outcome as CompoundOutcome.Failure).error)
        model.updateYears("1.0"); runCurrent()
        assertTrue(model.uiState.value.outcome is CompoundOutcome.Success)
    }
}
