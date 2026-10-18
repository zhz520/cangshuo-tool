package com.cangshuo.toolbox.feature.period.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.period.domain.PeriodCycle
import com.cangshuo.toolbox.feature.period.domain.PeriodCycleInput
import com.cangshuo.toolbox.feature.period.domain.PeriodError
import com.cangshuo.toolbox.feature.period.domain.PeriodFlow
import com.cangshuo.toolbox.feature.period.domain.PeriodMood
import com.cangshuo.toolbox.feature.period.domain.PeriodPolicy
import com.cangshuo.toolbox.feature.period.domain.PeriodSettings
import com.cangshuo.toolbox.feature.period.domain.PeriodSymptom
import com.cangshuo.toolbox.feature.period.domain.PeriodUseCases
import com.cangshuo.toolbox.feature.period.domain.PeriodValidation
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PeriodViewModel(
    private val useCases: PeriodUseCases,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var today = useCases.today()
    private var cycles = emptyList<PeriodCycle>()
    private var periodSettings = PeriodSettings()
    private val initialMonth = saved.get<String>("periodMonth")
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.from(LocalDate.ofEpochDay(today))
    private val mutable = MutableStateFlow(
        PeriodUiState(
            todayEpochDay = today,
            visibleMonth = PeriodPolicy.clampMonth(initialMonth),
            selectedEpochDay = saved.get<Long>("periodSelectedEpochDay")
                ?: today,
        ),
    )
    val state: StateFlow<PeriodUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(useCases.observeAll(), useCases.observeSettings()) { values, settings ->
                    values to settings
                }.collect { (values, settings) ->
                    cycles = values
                    periodSettings = settings
                    publish(loading = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false)
                emitMessage(PeriodMessage.STORAGE_FAILED)
            }
        }
        publish(loading = true)
    }

    fun refreshToday() {
        val next = useCases.today()
        if (next == today) return
        today = next
        publish(mutable.value.loading)
    }

    fun selectDay(epochDay: Long) {
        val month = PeriodPolicy.clampMonth(YearMonth.from(LocalDate.ofEpochDay(epochDay)))
        saved["periodSelectedEpochDay"] = epochDay
        saved["periodMonth"] = month.toString()
        mutable.value = mutable.value.copy(selectedEpochDay = epochDay, visibleMonth = month)
        publish(mutable.value.loading)
    }

    fun previousMonth() = moveMonth(-1L)
    fun nextMonth() = moveMonth(1L)

    fun goToday() {
        val now = LocalDate.ofEpochDay(today)
        saved["periodMonth"] = YearMonth.from(now).toString()
        saved["periodSelectedEpochDay"] = today
        mutable.value = mutable.value.copy(visibleMonth = YearMonth.from(now), selectedEpochDay = today)
        publish(mutable.value.loading)
    }

    fun openEditorForSelectedDay() {
        val epochDay = mutable.value.selectedEpochDay ?: today
        if (epochDay > today) {
            emitMessage(PeriodMessage.FUTURE_DATE)
            return
        }
        val selected = cycleAt(epochDay)
        mutable.value = mutable.value.copy(editor = editorFor(selected, epochDay))
    }

    fun editCycle(id: Long) {
        val cycle = cycles.firstOrNull { it.id == id } ?: return
        mutable.value = mutable.value.copy(
            selectedEpochDay = cycle.startEpochDay,
            editor = editorFor(cycle, cycle.startEpochDay),
        )
        publish(mutable.value.loading)
    }

    fun dismissEditor() {
        if (!mutable.value.saving) mutable.value = mutable.value.copy(editor = null)
    }

    fun setEditorStart(epochDay: Long) = updateEditor { editor ->
        when {
            epochDay > today -> editor.copy(error = PeriodError.START_DATE)
            editor.endEpochDay != null && epochDay > editor.endEpochDay -> editor.copy(error = PeriodError.DATE_ORDER)
            else -> editor.copy(startEpochDay = epochDay, error = null)
        }
    }

    fun setEditorEnd(epochDay: Long?) = updateEditor { editor ->
        if (epochDay != null && epochDay > today) {
            editor.copy(error = PeriodError.END_DATE)
        } else {
            editor.copy(endEpochDay = epochDay, error = null)
        }
    }

    fun setFlow(flow: PeriodFlow) = updateEditor { it.copy(flow = flow, error = null) }

    fun toggleSymptom(symptom: PeriodSymptom) = updateEditor { editor ->
        val next = if (symptom in editor.symptoms) editor.symptoms - symptom else editor.symptoms + symptom
        editor.copy(symptoms = next, error = null)
    }

    fun setMood(mood: PeriodMood?) = updateEditor { it.copy(mood = mood, error = null) }

    fun setNotes(value: String) = updateEditor { it.copy(notes = value, error = null) }

    fun saveEditor() {
        val editor = mutable.value.editor ?: return
        if (mutable.value.saving) return
        val existing = editor.cycleId?.let { id -> cycles.firstOrNull { it.id == id } }
        if (editor.cycleId != null && existing == null) {
            mutable.value = mutable.value.copy(editor = editor.copy(error = PeriodError.STORAGE))
            return
        }
        val input = PeriodCycleInput(
            startEpochDay = editor.startEpochDay,
            endEpochDay = editor.endEpochDay,
            flow = editor.flow,
            symptoms = editor.symptoms,
            mood = editor.mood,
            notes = editor.notes,
        )
        when (val validation = useCases.validate(existing, input, cycles)) {
            is PeriodValidation.Invalid -> mutable.value = mutable.value.copy(
                editor = editor.copy(error = validation.error),
            )
            is PeriodValidation.Valid -> {
                mutable.value = mutable.value.copy(saving = true, editor = editor.copy(error = null))
                viewModelScope.launch {
                    try {
                        val error = useCases.save(existing, input, cycles)
                        if (error == null) {
                            saved["periodSelectedEpochDay"] = validation.cycle.startEpochDay
                            mutable.value = mutable.value.copy(
                                saving = false,
                                editor = null,
                                selectedEpochDay = validation.cycle.startEpochDay,
                                visibleMonth = YearMonth.from(LocalDate.ofEpochDay(validation.cycle.startEpochDay)),
                            )
                            emitMessage(PeriodMessage.SAVED)
                        } else {
                            mutable.value = mutable.value.copy(
                                saving = false,
                                editor = editor.copy(error = error),
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutable.value = mutable.value.copy(
                            saving = false,
                            editor = editor.copy(error = PeriodError.STORAGE),
                        )
                    }
                }
            }
        }
    }

    fun deleteEditorCycle() {
        val editor = mutable.value.editor ?: return
        val id = editor.cycleId ?: return
        if (mutable.value.saving) return
        mutable.value = mutable.value.copy(saving = true)
        viewModelScope.launch {
            try {
                val error = useCases.delete(id)
                if (error == null) {
                    mutable.value = mutable.value.copy(saving = false, editor = null)
                    emitMessage(PeriodMessage.DELETED)
                } else {
                    mutable.value = mutable.value.copy(
                        saving = false,
                        editor = editor.copy(error = error),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    saving = false,
                    editor = editor.copy(error = PeriodError.STORAGE),
                )
            }
        }
    }

    fun openSettings() {
        val settings = mutable.value.settings
        mutable.value = mutable.value.copy(
            settingsEditor = PeriodSettingsEditor(settings.cycleDays.toString(), settings.periodDays.toString()),
        )
    }

    fun dismissSettings() {
        if (!mutable.value.saving) mutable.value = mutable.value.copy(settingsEditor = null)
    }

    fun setCycleDays(value: String) = updateSettingsEditor { it.copy(cycleDays = value.filter(Char::isDigit).take(2), error = null) }
    fun setPeriodDays(value: String) = updateSettingsEditor { it.copy(periodDays = value.filter(Char::isDigit).take(2), error = null) }

    fun saveSettings() {
        val editor = mutable.value.settingsEditor ?: return
        val cycle = editor.cycleDays.toIntOrNull()
        val period = editor.periodDays.toIntOrNull()
        if (cycle == null || period == null || !PeriodPolicy.validSettings(PeriodSettings(cycle, period))) {
            mutable.value = mutable.value.copy(settingsEditor = editor.copy(error = PeriodError.SETTINGS))
            return
        }
        val error = useCases.updateSettings(PeriodSettings(cycle, period))
        if (error == null) {
            mutable.value = mutable.value.copy(settingsEditor = null)
            emitMessage(PeriodMessage.SETTINGS_SAVED)
        } else {
            mutable.value = mutable.value.copy(settingsEditor = editor.copy(error = error))
        }
    }

    fun clearEditorError() = updateEditor { it.copy(error = null) }
    fun clearSettingsError() = updateSettingsEditor { it.copy(error = null) }

    fun consumeMessage() {
        if (mutable.value.message != null) mutable.value = mutable.value.copy(message = null)
    }

    private fun moveMonth(delta: Long) {
        val month = PeriodPolicy.clampMonth(mutable.value.visibleMonth.plusMonths(delta))
        val todayDate = LocalDate.ofEpochDay(today)
        val selected = if (YearMonth.from(todayDate) == month) today else month.atDay(1).toEpochDay()
        saved["periodMonth"] = month.toString()
        saved["periodSelectedEpochDay"] = selected
        mutable.value = mutable.value.copy(visibleMonth = month, selectedEpochDay = selected)
        publish(mutable.value.loading)
    }

    private fun editorFor(cycle: PeriodCycle?, epochDay: Long): PeriodEditorState =
        if (cycle == null) PeriodEditorState(
            cycleId = null,
            startEpochDay = epochDay,
            endEpochDay = epochDay,
            flow = PeriodFlow.MEDIUM,
        ) else PeriodEditorState(
            cycleId = cycle.id,
            startEpochDay = cycle.startEpochDay,
            endEpochDay = cycle.endEpochDay,
            flow = cycle.flow,
            symptoms = cycle.symptoms,
            mood = cycle.mood,
            notes = cycle.notes,
        )

    private fun cycleAt(epochDay: Long): PeriodCycle? = cycles.firstOrNull { cycle ->
        val end = cycle.endEpochDay ?: today.coerceAtLeast(cycle.startEpochDay)
        epochDay in cycle.startEpochDay..end
    }

    private fun updateEditor(transform: (PeriodEditorState) -> PeriodEditorState) {
        val editor = mutable.value.editor ?: return
        if (!mutable.value.saving) mutable.value = mutable.value.copy(editor = transform(editor))
    }

    private fun updateSettingsEditor(transform: (PeriodSettingsEditor) -> PeriodSettingsEditor) {
        val editor = mutable.value.settingsEditor ?: return
        if (!mutable.value.saving) mutable.value = mutable.value.copy(settingsEditor = transform(editor))
    }

    private fun publish(loading: Boolean) {
        val current = mutable.value
        val insights = useCases.insights(cycles, periodSettings)
        val selected = current.selectedEpochDay
        mutable.value = current.copy(
            loading = loading,
            todayEpochDay = today,
            cycles = cycles.sortedByDescending { it.startEpochDay },
            settings = periodSettings,
            insights = insights,
            calendarDays = PeriodPolicy.calendarMonth(current.visibleMonth, cycles, insights, selected, today),
            selectedCycle = selected?.let(::cycleAt),
        )
    }

    private fun emitMessage(message: PeriodMessage) {
        mutable.value = mutable.value.copy(
            message = message,
            messageRevision = mutable.value.messageRevision + 1,
        )
    }

    companion object {
        fun factory(useCases: PeriodUseCases): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                PeriodViewModel(useCases, extras.createSavedStateHandle()) as T
        }
    }
}
