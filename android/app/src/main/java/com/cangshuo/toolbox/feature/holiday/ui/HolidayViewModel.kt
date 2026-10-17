package com.cangshuo.toolbox.feature.holiday.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.holiday.domain.BuildHolidayCountdownUseCase
import com.cangshuo.toolbox.feature.holiday.domain.HolidayCountdownItem
import com.cangshuo.toolbox.feature.holiday.domain.HolidayDataset
import com.cangshuo.toolbox.feature.holiday.domain.HolidayFilter
import com.cangshuo.toolbox.feature.holiday.domain.HolidayId
import com.cangshuo.toolbox.feature.holiday.domain.HolidayLoadResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HolidayViewModel(
    private val useCases: BuildHolidayCountdownUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var filter = savedStateHandle.get<String>(KEY_FILTER)
        ?.let { name -> HolidayFilter.entries.firstOrNull { it.name == name } }
        ?: HolidayFilter.ALL
    private var selected = savedStateHandle.get<String>(KEY_SELECTED)
        ?.let { name -> HolidayId.entries.firstOrNull { it.name == name } }
    private var dataset: HolidayDataset? = null
    private var foreground = false
    private var ticker: Job? = null
    private val mutable = MutableStateFlow(HolidayUiState(filter = filter, selected = selected))
    val state: StateFlow<HolidayUiState> = mutable.asStateFlow()

    init {
        refresh()
    }

    fun start() {
        foreground = true
        refreshTimings()
        runTicker()
    }

    fun stop() {
        foreground = false
        ticker?.cancel()
        ticker = null
    }

    fun select(holiday: HolidayId) {
        if (!filter.includes(holiday)) return
        if (dataset?.occurrences?.get(holiday).isNullOrEmpty()) return
        selected = holiday
        persist()
        rebuild()
    }

    fun setFilter(value: HolidayFilter) {
        if (filter == value) return
        filter = value
        if (selected == null || !filter.includes(selected!!)) {
            selected = dataset?.occurrences
                ?.filterKeys(filter::includes)
                ?.values
                ?.mapNotNull { occurrences -> occurrences.firstOrNull() }
                ?.minWithOrNull(compareBy({ it.date }, { it.id.ordinal }))
                ?.id
        }
        persist()
        rebuild()
    }

    fun refresh() {
        dataset = when (val result = useCases.load()) {
            is HolidayLoadResult.Success -> result.dataset
            HolidayLoadResult.Failure -> null
        }
        rebuild()
    }

    private fun rebuild() {
        val loaded = dataset
        if (loaded == null) {
            mutable.value = HolidayUiState(filter = filter, selected = selected, error = HolidayError.CALCULATION_FAILED)
            return
        }
        val now = useCases.now()
        val matching = loaded.occurrences
            .filterKeys(filter::includes)
            .mapNotNull { (holiday, occurrences) ->
                occurrences.firstOrNull()?.let { occurrence ->
                    HolidayCountdownItem(occurrence, useCases.timing(occurrence, now, loaded))
                }
            }
            .sortedWith(compareBy({ it.occurrence.date }, { it.occurrence.id.ordinal }))

        var chosen = selected
        if (chosen == null || !filter.includes(chosen) ||
            loaded.occurrences[chosen].isNullOrEmpty()
        ) {
            chosen = matching.firstOrNull()?.occurrence?.id
        }
        selected = chosen
        val selectedUpcoming = chosen?.let { id ->
            loaded.occurrences[id].orEmpty().map { occurrence ->
                HolidayCountdownItem(occurrence, useCases.timing(occurrence, now, loaded))
            }
        }.orEmpty()
        persist()
        mutable.value = HolidayUiState(
            today = loaded.today,
            filter = filter,
            selected = chosen,
            selectedUpcoming = selectedUpcoming,
            items = matching,
            error = null,
        )
    }

    private fun refreshTimings() {
        val loaded = dataset ?: return refresh()
        val now = useCases.now()
        val today = now.atZone(useCases.zone()).toLocalDate()
        if (today != loaded.today) {
            refresh()
            return
        }
        rebuild()
    }

    private fun persist() {
        savedStateHandle[KEY_FILTER] = filter.name
        savedStateHandle[KEY_SELECTED] = selected?.name
    }

    private fun runTicker() {
        ticker?.cancel()
        ticker = null
        if (!foreground) return
        ticker = viewModelScope.launch {
            while (isActive && foreground) {
                val millis = useCases.now().toEpochMilli()
                val remainder = Math.floorMod(millis, BuildHolidayCountdownUseCase.TICK_INTERVAL_MS)
                delay(
                    if (remainder == 0L) BuildHolidayCountdownUseCase.TICK_INTERVAL_MS
                    else BuildHolidayCountdownUseCase.TICK_INTERVAL_MS - remainder,
                )
                refreshTimings()
            }
        }
    }

    companion object {
        private const val KEY_FILTER = "holidayFilter"
        private const val KEY_SELECTED = "holidaySelected"

        fun factory(useCases: BuildHolidayCountdownUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    HolidayViewModel(useCases, extras.createSavedStateHandle()) as T
            }
    }
}
