package com.cangshuo.toolbox.feature.workvalue

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.workvalue.domain.CalculateWorkValueUseCase
import com.cangshuo.toolbox.feature.workvalue.domain.LocalWorkValueRepository
import com.cangshuo.toolbox.feature.workvalue.domain.WorkPreset
import com.cangshuo.toolbox.feature.workvalue.domain.WorkValueError
import com.cangshuo.toolbox.feature.workvalue.ui.WorkValueField
import com.cangshuo.toolbox.feature.workvalue.ui.WorkValueViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkValueViewModelTest {
    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        WorkValueViewModel(CalculateWorkValueUseCase(LocalWorkValueRepository()), handle)

    @Test
    fun emptySalaryShowsDefaultsWithoutAnError() {
        val state = model().state.value
        assertEquals("", state.values[WorkValueField.SALARY])
        assertEquals("5", state.values[WorkValueField.WORK_DAYS_PER_WEEK])
        assertEquals("9", state.values[WorkValueField.DAILY_ATTENDANCE_HOURS])
        assertNull(state.result)
        assertNull(state.error)
    }

    @Test
    fun salaryProducesNominalAndRealHourlyValuesAndPersistsInput() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        viewModel.update(WorkValueField.SALARY, "10000")
        val state = viewModel.state.value
        assertNotNull(state.result)
        assertEquals(60.0, state.result!!.nominalHourly.toDouble(), 0.000001)
        assertEquals("10000", handle.get<String>("wvSalary"))
    }

    @Test
    fun optionalBonusBenefitsAndCostsRecalculateImmediately() {
        val viewModel = model()
        viewModel.update(WorkValueField.SALARY, "10000")
        viewModel.update(WorkValueField.ANNUAL_BONUS, "12000")
        viewModel.update(WorkValueField.MONTHLY_BENEFITS, "1000")
        viewModel.update(WorkValueField.MONTHLY_JOB_COST, "500")
        val result = viewModel.state.value.result!!
        assertEquals("144000", result.annualCash.toPlainString())
        assertEquals("138000", result.annualNetCash.toPlainString())
    }

    @Test
    fun presetsUpdateOnlyTimeFieldsAndRecalculate() {
        val handle = SavedStateHandle()
        val viewModel = model(handle)
        viewModel.update(WorkValueField.SALARY, "10000")
        viewModel.applyPreset(WorkPreset.REMOTE)
        val values = viewModel.state.value.values
        assertEquals("5", values[WorkValueField.WORK_DAYS_PER_WEEK])
        assertEquals("0", values[WorkValueField.OFFICE_DAYS_PER_WEEK])
        assertEquals("0", values[WorkValueField.COMMUTE_HOURS_ONE_WAY])
        assertEquals(0.0, viewModel.state.value.result!!.commuteHoursPerYear, 0.0)
        assertEquals("0", handle.get<String>("wvOfficeDays"))
    }

    @Test
    fun invalidAndImpossibleInputsAreReported() {
        val viewModel = model()
        viewModel.update(WorkValueField.SALARY, "abc")
        assertEquals(WorkValueError.INVALID, viewModel.state.value.error)

        viewModel.update(WorkValueField.SALARY, "10000")
        viewModel.update(WorkValueField.WORK_DAYS_PER_WEEK, "0")
        assertEquals(WorkValueError.OUT_OF_RANGE, viewModel.state.value.error)

        viewModel.update(WorkValueField.WORK_DAYS_PER_WEEK, "5")
        viewModel.update(WorkValueField.PAID_NON_WORKING_DAYS, "365")
        assertEquals(WorkValueError.NO_WORK_TIME, viewModel.state.value.error)
    }

    @Test
    fun savedStateRestoresValuesAndClearResetsEverything() {
        val handle = SavedStateHandle(mapOf(
            "wvSalary" to "10000",
            "wvMonths" to "13",
            "wvOfficeDays" to "0",
            "wvCommute" to "0",
        ))
        val viewModel = model(handle)
        assertNotNull(viewModel.state.value.result)
        assertEquals("13", viewModel.state.value.values[WorkValueField.PAID_MONTHS])

        viewModel.clear()
        assertEquals("", viewModel.state.value.values[WorkValueField.SALARY])
        assertEquals("12", viewModel.state.value.values[WorkValueField.PAID_MONTHS])
        assertEquals("5", viewModel.state.value.values[WorkValueField.OFFICE_DAYS_PER_WEEK])
        assertNull(viewModel.state.value.result)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun oversizedPasteIsIgnoredWithoutReplacingThePreviousValue() {
        val viewModel = model()
        viewModel.update(WorkValueField.SALARY, "10000")
        viewModel.update(WorkValueField.SALARY, "1".repeat(19))
        assertEquals("10000", viewModel.state.value.values[WorkValueField.SALARY])
        assertNotNull(viewModel.state.value.result)
    }
}
