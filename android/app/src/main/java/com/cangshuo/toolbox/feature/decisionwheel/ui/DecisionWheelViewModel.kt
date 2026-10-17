package com.cangshuo.toolbox.feature.decisionwheel.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelError
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelPolicy
import com.cangshuo.toolbox.feature.decisionwheel.domain.DecisionWheelUseCases
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelOption
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelPreset
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelResult
import com.cangshuo.toolbox.feature.decisionwheel.domain.WheelSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class DecisionWheelViewModel(
    private val useCases: DecisionWheelUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val defaults = useCases.defaults()
    private val initialOptions = restoreOptions()
    private val initialHistory = restoreHistory()
    private val initialRotation = DecisionWheelPolicy.floorMod(saved.get<Float>("dwRotation") ?: 0f, 360f)
    private var spinId = saved.get<Long>("dwSpinId") ?: 0L
    private val mutable = MutableStateFlow(
        DecisionWheelUiState(
            settings = WheelSettings(
                options = initialOptions,
                avoidRepeat = saved["dwAvoidRepeat"] ?: true,
                haptics = saved["dwHaptics"] ?: true,
            ),
            lastResult = initialHistory.firstOrNull(),
            resultVisible = saved["dwResultVisible"] ?: initialHistory.isNotEmpty(),
            rotation = initialRotation,
            history = initialHistory,
        ),
    )
    val state: StateFlow<DecisionWheelUiState> = mutable.asStateFlow()

    fun spin(currentRotation: Float) {
        val current = mutable.value
        if (current.spinning) return
        val nextId = ++spinId
        saved["dwSpinId"] = nextId
        val plan = useCases.plan(
            options = current.settings.options,
            currentRotation = currentRotation,
            avoidRepeat = current.settings.avoidRepeat,
            lastLabel = current.lastResult?.label,
            spinId = nextId,
        ) ?: run {
            mutable.value = current.copy(editError = DecisionWheelError.NOT_ENOUGH)
            return
        }
        saved["dwResultVisible"] = false
        mutable.value = current.copy(spin = plan, resultVisible = false, editError = null)
    }

    fun complete(id: Long, finalRotation: Float) {
        val current = mutable.value
        val spin = current.spin?.takeIf { it.id == id } ?: return
        val result = useCases.result(spin, current.settings.options) ?: return
        val history = (listOf(result) + current.history)
            .distinctBy { it.timestampMillis to it.label }
            .take(DecisionWheelPolicy.MAX_HISTORY)
        val rotation = DecisionWheelPolicy.floorMod(finalRotation, 360f)
        saved["dwRotation"] = rotation
        saved["dwResultVisible"] = true
        mutable.value = current.copy(
            spin = null,
            lastResult = result,
            resultVisible = true,
            rotation = rotation,
            history = history,
        )
        persistHistory(history)
    }

    fun applyPreset(preset: WheelPreset) {
        if (mutable.value.spinning) return
        updateOptions(useCases.preset(preset))
    }

    fun resetOptions() {
        if (mutable.value.spinning) return
        updateOptions(useCases.defaults())
    }

    fun saveOption(editingIndex: Int?, label: String, weight: Int): Boolean {
        val current = mutable.value
        if (current.spinning) return false
        val error = useCases.validate(current.settings.options, label, weight, editingIndex)
        if (error != null) {
            mutable.value = current.copy(editError = error)
            return false
        }
        val option = useCases.option(label, weight)
        val options = current.settings.options.toMutableList().apply {
            if (editingIndex == null) add(option) else set(editingIndex, option)
        }
        updateOptions(options)
        return true
    }

    fun removeOption(index: Int): Boolean {
        val current = mutable.value
        if (current.spinning || current.settings.options.size <= DecisionWheelPolicy.MIN_OPTIONS) return false
        if (index !in current.settings.options.indices) return false
        updateOptions(current.settings.options.filterIndexed { optionIndex, _ -> optionIndex != index })
        return true
    }

    fun setAvoidRepeat(enabled: Boolean) {
        if (mutable.value.spinning) return
        saved["dwAvoidRepeat"] = enabled
        mutable.value = mutable.value.copy(settings = mutable.value.settings.copy(avoidRepeat = enabled))
    }

    fun setHaptics(enabled: Boolean) {
        saved["dwHaptics"] = enabled
        mutable.value = mutable.value.copy(settings = mutable.value.settings.copy(haptics = enabled))
    }

    fun clearEditError() {
        if (mutable.value.editError != null) mutable.value = mutable.value.copy(editError = null)
    }

    fun clearHistory() {
        if (mutable.value.spinning) return
        saved["dwResultVisible"] = false
        mutable.value = mutable.value.copy(lastResult = null, resultVisible = false, history = emptyList())
        persistHistory(emptyList())
    }

    fun dismissResult() {
        saved["dwResultVisible"] = false
        mutable.value = mutable.value.copy(resultVisible = false)
    }

    fun setRotation(value: Float) {
        if (mutable.value.spinning) return
        val rotation = DecisionWheelPolicy.floorMod(value, 360f)
        saved["dwRotation"] = rotation
        mutable.value = mutable.value.copy(rotation = rotation)
    }

    private fun updateOptions(options: List<WheelOption>) {
        val normalized = options.take(DecisionWheelPolicy.MAX_OPTIONS)
        val settings = mutable.value.settings.copy(options = normalized)
        mutable.value = mutable.value.copy(settings = settings, editError = null)
        saved["dwLabels"] = ArrayList(normalized.map { it.label })
        saved["dwWeights"] = normalized.map { it.weight.coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT) }.toIntArray()
    }

    private fun restoreOptions(): List<WheelOption> {
        val labels = saved.get<ArrayList<String>>("dwLabels")
        val weights = saved.get<IntArray>("dwWeights")
        val restored = labels.orEmpty().mapIndexedNotNull { index, raw ->
            val label = DecisionWheelPolicy.cleanLabel(raw)
            if (label.isBlank()) null else WheelOption(
                label = label,
                weight = (weights?.getOrNull(index) ?: 1)
                    .coerceIn(DecisionWheelPolicy.MIN_WEIGHT, DecisionWheelPolicy.MAX_WEIGHT),
            )
        }.distinctBy { it.label.lowercase(Locale.ROOT) }.take(DecisionWheelPolicy.MAX_OPTIONS)
        return restored.takeIf { it.size >= DecisionWheelPolicy.MIN_OPTIONS } ?: defaults
    }

    private fun restoreHistory(): List<WheelResult> {
        val labels = saved.get<ArrayList<String>>("dwHistoryLabels").orEmpty()
        val times = saved.get<LongArray>("dwHistoryTimes") ?: LongArray(0)
        return labels.mapIndexedNotNull { index, label ->
            times.getOrNull(index)?.let { time -> WheelResult(label, time) }
        }.take(DecisionWheelPolicy.MAX_HISTORY)
    }

    private fun persistHistory(history: List<WheelResult>) {
        saved["dwHistoryLabels"] = ArrayList(history.map { it.label })
        saved["dwHistoryTimes"] = history.map { it.timestampMillis }.toLongArray()
    }

    companion object {
        fun factory(useCases: DecisionWheelUseCases): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    DecisionWheelViewModel(useCases, extras.createSavedStateHandle()) as T
            }
    }
}
