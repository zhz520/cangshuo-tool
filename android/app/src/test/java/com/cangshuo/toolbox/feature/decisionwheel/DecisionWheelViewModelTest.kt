package com.cangshuo.toolbox.feature.decisionwheel

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelError
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelRepository
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelUseCases
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelOption
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelPreset
import com.cangshuo.toolbox.feature.decisionwheel.ui.DecisionWheelViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionWheelViewModelTest {
    private class FakeRepository : DecisionWheelRepository {
        val draws = ArrayDeque<Int>()
        var now = 1_000L
        override fun defaults() = listOf(WheelOption("A"), WheelOption("B"))
        override fun preset(kind: WheelPreset) = when (kind) {
            WheelPreset.MEALS -> listOf(WheelOption("Rice"), WheelOption("Noodles"), WheelOption("Salad"))
            WheelPreset.YES_NO -> listOf(WheelOption("Yes"), WheelOption("No"))
            WheelPreset.ACTIVITIES -> listOf(WheelOption("Walk"), WheelOption("Read"))
        }
        override fun nextInt(bound: Int): Int = draws.removeFirstOrNull()?.coerceIn(0, bound - 1) ?: 0
        override fun nowMillis(): Long = now++
    }

    private fun model(repo: FakeRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        DecisionWheelViewModel(DecisionWheelUseCases(repo), saved)

    @Test fun addingEditingAndDeletingOptionsAreValidatedAndPersisted() {
        val saved = SavedStateHandle()
        val model = model(saved = saved)
        assertTrue(model.saveOption(null, "  Cake  ", 3))
        assertEquals(listOf("A", "B", "Cake"), model.state.value.settings.options.map { it.label })
        assertEquals(3, model.state.value.settings.options.last().weight)
        assertFalse(model.saveOption(null, "cake", 1))
        assertEquals(DecisionWheelError.DUPLICATE, model.state.value.editError)
        assertTrue(model.saveOption(2, "Ice cream", 5))
        assertEquals("Ice cream", model.state.value.settings.options[2].label)
        assertEquals(listOf("A", "B", "Ice cream"), saved.get<ArrayList<String>>("dwLabels"))
        assertTrue(model.removeOption(2))
        assertFalse(model.removeOption(0))
        assertEquals(2, model.state.value.settings.options.size)
    }

    @Test fun presetsAndResetReplaceOnlyTheOptions() {
        val model = model()
        model.applyPreset(WheelPreset.MEALS)
        assertEquals(listOf("Rice", "Noodles", "Salad"), model.state.value.settings.options.map { it.label })
        model.resetOptions()
        assertEquals(listOf("A", "B"), model.state.value.settings.options.map { it.label })
    }

    @Test fun spinIsSingleFlightAndSettlesOnce() {
        val repo = FakeRepository().apply { draws.addAll(listOf(0, 1, 50)) }
        val model = model(repo)
        model.spin(0f)
        val spin = model.state.value.spin
        assertNotNull(spin)
        assertTrue(model.state.value.spinning)
        val before = model.state.value
        model.spin(0f)
        assertEquals(before, model.state.value)
        model.complete(spin!!.id, spin.targetRotation)
        assertFalse(model.state.value.spinning)
        assertTrue(model.state.value.resultVisible)
        assertEquals("A", model.state.value.lastResult?.label)
        assertEquals(1, model.state.value.history.size)
        model.complete(spin.id, spin.targetRotation)
        assertEquals(1, model.state.value.history.size)
        model.dismissResult()
        assertFalse(model.state.value.resultVisible)
        assertEquals("A", model.state.value.lastResult?.label)
    }

    @Test fun avoidRepeatUsesThePreviousResult() {
        val repo = FakeRepository().apply { draws.addAll(listOf(0, 0, 50, 0, 0, 50)) }
        val model = model(repo)
        model.spin(0f)
        model.complete(model.state.value.spin!!.id, model.state.value.spin!!.targetRotation)
        model.spin(0f)
        assertEquals(1, model.state.value.spin?.winnerIndex)
        model.complete(model.state.value.spin!!.id, model.state.value.spin!!.targetRotation)
        assertEquals(listOf("B", "A"), model.state.value.history.map { it.label })
    }

    @Test fun historyOptionsAndFlagsRestoreFromSavedState() {
        val saved = SavedStateHandle(
            mapOf(
                "dwLabels" to arrayListOf("A", "C"),
                "dwWeights" to intArrayOf(2, 4),
                "dwAvoidRepeat" to false,
                "dwHaptics" to false,
                "dwHistoryLabels" to arrayListOf("C", "A"),
                "dwHistoryTimes" to longArrayOf(200, 100),
                "dwRotation" to 405f,
                "dwResultVisible" to false,
            ),
        )
        val model = model(saved = saved)
        assertEquals(listOf(WheelOption("A", 2), WheelOption("C", 4)), model.state.value.settings.options)
        assertFalse(model.state.value.settings.avoidRepeat)
        assertFalse(model.state.value.settings.haptics)
        assertEquals(listOf("C", "A"), model.state.value.history.map { it.label })
        assertEquals("C", model.state.value.lastResult?.label)
        assertEquals(45f, model.state.value.rotation, 0.01f)
        assertFalse(model.state.value.resultVisible)
    }

    @Test fun invalidRestoredOptionsFallBackAndHistoryCanBeCleared() {
        val saved = SavedStateHandle(mapOf("dwLabels" to arrayListOf("Only one")))
        val model = model(saved = saved)
        assertEquals(listOf("A", "B"), model.state.value.settings.options.map { it.label })
        model.spin(0f)
        model.complete(model.state.value.spin!!.id, model.state.value.spin!!.targetRotation)
        assertEquals(1, model.state.value.history.size)
        model.clearHistory()
        assertTrue(model.state.value.history.isEmpty())
        assertNull(model.state.value.lastResult)
    }

    @Test fun hapticsAndRepeatPreferencesPersist() {
        val saved = SavedStateHandle()
        val model = model(saved = saved)
        model.setAvoidRepeat(false)
        model.setHaptics(false)
        assertEquals(false, saved.get<Boolean>("dwAvoidRepeat"))
        assertEquals(false, saved.get<Boolean>("dwHaptics"))
    }
}
