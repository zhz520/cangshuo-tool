package com.cangshuo.toolbox.feature.timestamp.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.timestamp.domain.ConvertDateTimeUseCase
import com.cangshuo.toolbox.feature.timestamp.domain.ConvertTimestampUseCase
import com.cangshuo.toolbox.feature.timestamp.domain.GetCurrentTimeUseCase
import com.cangshuo.toolbox.feature.timestamp.domain.SupportedTimeZone
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampResult
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TimestampViewModel(
    private val getCurrentTimeUseCase: GetCurrentTimeUseCase,
    private val convertTimestampUseCase: ConvertTimestampUseCase,
    private val convertDateTimeUseCase: ConvertDateTimeUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val tsInput = savedStateHandle.getStateFlow(KEY_TS_INPUT, "")
    private val tsUnitName = savedStateHandle.getStateFlow(KEY_TS_UNIT, TimestampUnit.SECONDS.name)
    private val tsTzName = savedStateHandle.getStateFlow(KEY_TS_TZ, SupportedTimeZone.SYSTEM.name)

    private val dateInput = savedStateHandle.getStateFlow(KEY_DATE_INPUT, "")
    private val dateTzName = savedStateHandle.getStateFlow(KEY_DATE_TZ, SupportedTimeZone.SYSTEM.name)

    private val isLive = savedStateHandle.getStateFlow(KEY_IS_LIVE, true)

    private val currentTick = MutableStateFlow(getCurrentSnapshot())

    init {
        viewModelScope.launch {
            while (isActive) {
                if (isLive.value) {
                    currentTick.value = getCurrentSnapshot()
                }
                delay(1000L)
            }
        }
    }

    private data class CurrentSnapshot(
        val seconds: Long,
        val millis: Long,
        val formatted: String,
    )

    private fun getCurrentSnapshot(): CurrentSnapshot {
        val now = getCurrentTimeUseCase.getInstant()
        val zdt = now.atZone(ZoneId.systemDefault())
        return CurrentSnapshot(
            seconds = now.epochSecond,
            millis = now.toEpochMilli(),
            formatted = zdt.format(STANDARD_FORMATTER),
        )
    }

    private data class TsState(
        val input: String,
        val unit: TimestampUnit,
        val timeZone: SupportedTimeZone,
        val resultFormatted: String,
        val resultIso: String,
        val isError: Boolean,
    )

    private data class DateState(
        val input: String,
        val timeZone: SupportedTimeZone,
        val resultSeconds: String,
        val resultMillis: String,
        val error: TimestampDateError?,
    )

    private val tsState = combine(tsInput, tsUnitName, tsTzName) { inVal, unitName, tzName ->
        val unit = runCatching { TimestampUnit.valueOf(unitName) }.getOrDefault(TimestampUnit.SECONDS)
        val tz = runCatching { SupportedTimeZone.valueOf(tzName) }.getOrDefault(SupportedTimeZone.SYSTEM)
        val (formatted, iso, error) = when (val res = convertTimestampUseCase(inVal, unit, tz.toZoneId())) {
            is TimestampResult.Success -> Triple(res.conversion.formattedDateTime, res.conversion.iso8601, false)
            is TimestampResult.Empty -> Triple("", "", false)
            is TimestampResult.InvalidFormat, is TimestampResult.InvalidDate,
            is TimestampResult.OutOfRange -> Triple("", "", true)
        }
        TsState(inVal, unit, tz, formatted, iso, error)
    }

    private val dateState = combine(dateInput, dateTzName) { inVal, tzName ->
        val tz = runCatching { SupportedTimeZone.valueOf(tzName) }.getOrDefault(SupportedTimeZone.SYSTEM)
        val (sec, millis, error) = when (val res = convertDateTimeUseCase(inVal, tz.toZoneId())) {
            is TimestampResult.Success -> Triple(res.conversion.epochSeconds.toString(), res.conversion.epochMillis.toString(), null)
            is TimestampResult.Empty -> Triple("", "", null)
            is TimestampResult.InvalidFormat -> Triple("", "", TimestampDateError.INVALID_FORMAT)
            is TimestampResult.InvalidDate -> Triple("", "", TimestampDateError.INVALID_DATE)
            is TimestampResult.OutOfRange -> Triple("", "", TimestampDateError.OUT_OF_RANGE)
        }
        DateState(inVal, tz, sec, millis, error)
    }

    val uiState: StateFlow<TimestampUiState> = combine(
        currentTick,
        isLive,
        tsState,
        dateState,
    ) { current, live, ts, date ->
        TimestampUiState(
            currentSeconds = current.seconds,
            currentMillis = current.millis,
            currentFormatted = current.formatted,
            isLive = live,
            tsInput = ts.input,
            tsUnit = ts.unit,
            tsTimeZone = ts.timeZone,
            tsResultFormatted = ts.resultFormatted,
            tsResultIso = ts.resultIso,
            tsIsError = ts.isError,
            dateInput = date.input,
            dateTimeZone = date.timeZone,
            dateResultSeconds = date.resultSeconds,
            dateResultMillis = date.resultMillis,
            dateError = date.error,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        TimestampUiState(
            currentSeconds = currentTick.value.seconds,
            currentMillis = currentTick.value.millis,
            currentFormatted = currentTick.value.formatted,
        ),
    )

    fun updateTimestampInput(text: String) {
        if (text.length <= MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_TS_INPUT] = text
        }
    }

    fun updateTimestampUnit(unit: TimestampUnit) {
        savedStateHandle[KEY_TS_UNIT] = unit.name
    }

    fun updateTimestampTimeZone(timeZone: SupportedTimeZone) {
        savedStateHandle[KEY_TS_TZ] = timeZone.name
    }

    fun fillCurrentTimestamp() {
        val unit = runCatching { TimestampUnit.valueOf(tsUnitName.value) }.getOrDefault(TimestampUnit.SECONDS)
        val value = if (unit == TimestampUnit.SECONDS) {
            currentTick.value.seconds.toString()
        } else {
            currentTick.value.millis.toString()
        }
        savedStateHandle[KEY_TS_INPUT] = value
    }

    fun updateDateInput(text: String) {
        if (text.length <= MAX_INPUT_LENGTH) {
            savedStateHandle[KEY_DATE_INPUT] = text
        }
    }

    fun updateDateTimeZone(timeZone: SupportedTimeZone) {
        savedStateHandle[KEY_DATE_TZ] = timeZone.name
    }

    fun fillCurrentDateTime() {
        savedStateHandle[KEY_DATE_INPUT] = currentTick.value.formatted
    }

    fun toggleLive() {
        savedStateHandle[KEY_IS_LIVE] = !isLive.value
    }

    fun refreshNow() {
        currentTick.value = getCurrentSnapshot()
    }

    companion object {
        private const val KEY_TS_INPUT = "ts_input"
        private const val KEY_TS_UNIT = "ts_unit"
        private const val KEY_TS_TZ = "ts_tz"
        private const val KEY_DATE_INPUT = "date_input"
        private const val KEY_DATE_TZ = "date_tz"
        private const val KEY_IS_LIVE = "is_live"
        private const val MAX_INPUT_LENGTH = 64

        private val STANDARD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun factory(
            getCurrentTimeUseCase: GetCurrentTimeUseCase,
            convertTimestampUseCase: ConvertTimestampUseCase,
            convertDateTimeUseCase: ConvertDateTimeUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val handle = extras.createSavedStateHandle()
                return TimestampViewModel(
                    getCurrentTimeUseCase = getCurrentTimeUseCase,
                    convertTimestampUseCase = convertTimestampUseCase,
                    convertDateTimeUseCase = convertDateTimeUseCase,
                    savedStateHandle = handle,
                ) as T
            }
        }
    }
}
