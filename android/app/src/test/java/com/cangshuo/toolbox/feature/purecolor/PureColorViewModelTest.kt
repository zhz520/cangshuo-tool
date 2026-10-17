package com.cangshuo.toolbox.feature.purecolor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.purecolor.domain.*
import com.cangshuo.toolbox.feature.purecolor.ui.PureColorMessage
import com.cangshuo.toolbox.feature.purecolor.ui.PureColorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PureColorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    private class FakeRepository : PureColorRepository {
        var saves = 0
        var shares = 0
        override suspend fun save(params: PureColorParams, destination: Uri?): Result<Uri> {
            saves++; return Result.failure(PureColorException(PureColorError.SAVE_FAILED))
        }
        override suspend fun prepareShare(params: PureColorParams): Result<Uri> {
            shares++; return Result.failure(PureColorException(PureColorError.SHARE_FAILED))
        }
    }

    private fun model(repo: FakeRepository) = PureColorViewModel(
        SavePureColorUseCase(repo), SharePureColorUseCase(repo), SavedStateHandle(),
    )

    @Test fun defaultAndPresetParametersStayValid() {
        val state = model(FakeRepository()).uiState.value
        assertTrue(state.valid)
        assertEquals(1080, state.width)
        assertEquals(1080, state.height)
        assertEquals(0xFF1677FF.toInt(), state.argb)
    }

    @Test fun dimensionInputKeepsOnlyFourDigitsAndPresetsUpdateBothAxes() {
        val vm = model(FakeRepository())
        vm.updateWidth("12a3456")
        assertEquals("1234", vm.uiState.value.widthInput)
        vm.setPreset(1920, 1080)
        assertEquals("1920", vm.uiState.value.widthInput)
        assertEquals("1080", vm.uiState.value.heightInput)
    }

    @Test fun invalidColorOrOversizeDoesNotStartExport() = runTest(dispatcher) {
        val repo = FakeRepository(); val vm = model(repo)
        vm.updateColor("#GGGGGG")
        assertFalse(vm.uiState.value.valid)
        vm.onSave(null); vm.onShare(); advanceUntilIdle()
        assertEquals(0, repo.saves); assertEquals(0, repo.shares)
        vm.updateColor("#1677FF"); vm.setPreset(4096, 4096)
        assertFalse(vm.uiState.value.valid)
        vm.onSave(null); advanceUntilIdle()
        assertEquals(0, repo.saves)
    }

    @Test fun validExportFailureEndsBusyStateWithMessage() = runTest(dispatcher) {
        val repo = FakeRepository(); val vm = model(repo)
        vm.onSave(null); advanceUntilIdle()
        assertEquals(1, repo.saves)
        assertFalse(vm.uiState.value.saving)
        assertEquals(PureColorMessage.SAVE_FAILED, vm.uiState.value.message)
        vm.onMessageConsumed()
        vm.onShare(); advanceUntilIdle()
        assertEquals(1, repo.shares)
        assertFalse(vm.uiState.value.sharing)
        assertEquals(PureColorMessage.SHARE_FAILED, vm.uiState.value.message)
    }
}
