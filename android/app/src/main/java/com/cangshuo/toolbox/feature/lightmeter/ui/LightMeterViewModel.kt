package com.cangshuo.toolbox.feature.lightmeter.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.feature.lightmeter.domain.*
import com.cangshuo.toolbox.feature.sensors.domain.SensorDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class LightStatus { IDLE, WAITING, READY, UNSUPPORTED, NO_READING, FAILED }

data class LightMeterUiState(
    val status: LightStatus = LightStatus.IDLE,
    val sensor: SensorDescriptor? = null,
    val sample: LightSample? = null,
    val session: LightSession = LightSession(),
    val paused: Boolean = false,
    val invalidReading: Boolean = false,
    val unit: LightUnit = LightUnit.LUX,
    val windowSeconds: Int = 30,
    val factor: Double = 1.0,
    val reference: String = "",
    val calibrationError: LightCalibrationError? = null,
    val csvReport: Boolean = false,
)

class LightMeterViewModel(
    private val observeLight: ObserveLightUseCase,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(LightMeterUiState(
        unit = saved.get<String>("lightUnit")?.let { name -> LightUnit.entries.find { it.name == name } } ?: LightUnit.LUX,
        windowSeconds = saved.get<Int>("lightWindow")?.takeIf { it in LightPolicy.WINDOWS } ?: 30,
        factor = saved.get<Double>("lightFactor")?.takeIf(LightPolicy::validFactor) ?: 1.0,
        reference = saved.get<String>("lightReference")?.takeIf { it.length <= 16 }.orEmpty(),
        csvReport = saved["lightCsv"] ?: false,
    ))
    val state = mutable.asStateFlow()
    private var active = false
    private var sampling: Job? = null
    private var generation = 0L

    fun start() { active = true; subscribe() }
    fun stop() { active = false; cancelSampling() }
    private fun cancelSampling() { generation++; sampling?.cancel(); sampling = null }

    fun retry() { cancelSampling(); subscribe() }
    fun togglePause() {
        mutable.update { it.copy(paused = !it.paused) }
        cancelSampling()
        subscribe()
    }
    fun clearSession() { mutable.update { it.copy(session = LightSession()) } }
    fun selectUnit(unit: LightUnit) {
        saved["lightUnit"] = unit.name
        mutable.update { it.copy(unit = unit) }
    }
    fun selectWindow(seconds: Int) {
        if (seconds !in LightPolicy.WINDOWS) return
        saved["lightWindow"] = seconds
        mutable.update { it.copy(windowSeconds = seconds) }
    }
    fun updateReference(text: String) {
        if (text.length > 16) {
            mutable.update { it.copy(calibrationError = LightCalibrationError.INVALID_REFERENCE) }
            return
        }
        saved["lightReference"] = text
        mutable.update { it.copy(reference = text, calibrationError = null) }
    }
    fun calibrate() {
        // A held reading is usable while paused; an error or waiting state must not calibrate stale data.
        val current = mutable.value
        val reading = current.sample.takeIf { current.status == LightStatus.READY && !current.invalidReading }
        when (val result = LightPolicy.calibrate(current.reference, reading)) {
            is LightCalibration.Applied -> {
                saved["lightFactor"] = result.factor
                mutable.update { it.copy(factor = result.factor, calibrationError = null) }
            }
            is LightCalibration.Rejected -> mutable.update { it.copy(calibrationError = result.error) }
        }
    }
    fun resetCalibration() {
        saved["lightFactor"] = 1.0
        mutable.update { it.copy(factor = 1.0, calibrationError = null) }
    }
    fun selectReport(csv: Boolean) {
        saved["lightCsv"] = csv
        mutable.update { it.copy(csvReport = csv) }
    }

    private fun subscribe() {
        if (!active || mutable.value.paused || sampling?.isActive == true) return
        val token = ++generation
        mutable.update { it.copy(status = LightStatus.WAITING, invalidReading = false, calibrationError = null) }
        sampling = viewModelScope.launch {
            var received = false
            val timeout = launch {
                delay(LightPolicy.FIRST_READING_TIMEOUT_MS)
                if (!received && generation == token) {
                    mutable.update { it.copy(status = LightStatus.NO_READING) }
                    sampling?.cancel()
                }
            }
            try {
                observeLight().collect { event ->
                    ensureActive()
                    if (token != generation) return@collect
                    when (event) {
                        is LightEvent.Source -> mutable.update { it.copy(sensor = event.sensor) }
                        is LightEvent.Reading -> {
                            if (!LightPolicy.valid(event.sample)) {
                                mutable.update { it.copy(invalidReading = true) }
                                return@collect
                            }
                            val previous = mutable.value.sample
                            if (previous != null && event.sample.timestampNanos < previous.timestampNanos) return@collect
                            received = true
                            timeout.cancel()
                            mutable.update {
                                it.copy(status = LightStatus.READY, sample = event.sample,
                                    session = it.session.add(event.sample), invalidReading = false)
                            }
                        }
                        LightEvent.InvalidReading -> mutable.update { it.copy(invalidReading = true) }
                    }
                }
                // A listener should stay open until cancelled; a normal completion is a recoverable failure.
                if (token == generation) mutable.update { it.copy(status = LightStatus.FAILED) }
            } catch (e: CancellationException) { throw e }
            catch (_: LightSensorUnavailable) { if (token == generation) mutable.update { it.copy(status = LightStatus.UNSUPPORTED) } }
            catch (_: Exception) { if (token == generation) mutable.update { it.copy(status = LightStatus.FAILED) } }
            finally { timeout.cancel() }
        }
    }

    companion object {
        fun factory(observe: ObserveLightUseCase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                LightMeterViewModel(observe, extras.createSavedStateHandle()) as T
        }
    }
}
