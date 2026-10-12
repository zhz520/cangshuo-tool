package com.cangshuo.toolbox.feature.lightmeter.domain

import com.cangshuo.toolbox.feature.sensors.domain.SensorDescriptor
import kotlinx.coroutines.flow.Flow

enum class LightUnit {
    LUX, FOOT_CANDLE;

    fun convert(lux: Double): Double = if (this == LUX) lux else lux * 0.3048 * 0.3048
}

data class LightSample(val lux: Double, val timestampNanos: Long, val accuracy: Int)

sealed interface LightEvent {
    data class Source(val sensor: SensorDescriptor) : LightEvent
    data class Reading(val sample: LightSample) : LightEvent
    data object InvalidReading : LightEvent
}

class LightSensorUnavailable : IllegalStateException()

interface LightMeterRepository {
    fun observe(): Flow<LightEvent>
}

class ObserveLightUseCase(private val repository: LightMeterRepository) {
    operator fun invoke(): Flow<LightEvent> = repository.observe()
}

/** Raw samples stay unchanged so changing calibration never mixes measurement scales. */
data class LightSession(
    val count: Long = 0,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val mean: Double = 0.0,
    val latest: LightSample? = null,
    val history: List<LightSample> = emptyList(),
) {
    fun add(sample: LightSample): LightSession {
        if (!LightPolicy.valid(sample) || (latest != null && sample.timestampNanos <= latest.timestampNanos)) return this
        val nextCount = count + 1
        return copy(
            count = nextCount,
            minimum = minOf(minimum ?: sample.lux, sample.lux),
            maximum = maxOf(maximum ?: sample.lux, sample.lux),
            mean = mean + (sample.lux - mean) / nextCount,
            latest = sample,
            history = (history + sample).takeLast(LightPolicy.HISTORY_LIMIT),
        )
    }

    fun window(seconds: Int): List<LightSample> {
        require(seconds in LightPolicy.WINDOWS)
        val end = latest?.timestampNanos ?: return emptyList()
        return history.filter { end - it.timestampNanos <= seconds * 1_000_000_000L }
    }
}

enum class LightBand { DARK, DIM, INDOOR, BRIGHT, VERY_BRIGHT }
enum class LightCalibrationError { INVALID_REFERENCE, NO_READING, FACTOR_OUT_OF_RANGE }
sealed interface LightCalibration {
    data class Applied(val factor: Double) : LightCalibration
    data class Rejected(val error: LightCalibrationError) : LightCalibration
}

object LightPolicy {
    const val HISTORY_LIMIT = 600
    const val FIRST_READING_TIMEOUT_MS = 5_000L
    const val MAX_LUX = 1_000_000_000.0
    val WINDOWS = listOf(15, 30, 60)

    fun valid(sample: LightSample): Boolean = sample.lux.isFinite() && sample.lux in 0.0..MAX_LUX && sample.timestampNanos > 0
    fun validFactor(factor: Double): Boolean = factor.isFinite() && factor in 0.1..10.0

    fun calibrate(reference: String, sample: LightSample?): LightCalibration {
        val text = reference.trim()
        if (text.length > 16 || !Regex("[0-9]{1,10}(?:[.,][0-9]{1,4})?").matches(text)) {
            return LightCalibration.Rejected(LightCalibrationError.INVALID_REFERENCE)
        }
        val expected = text.replace(',', '.').toDoubleOrNull()
        if (expected == null || !expected.isFinite() || expected <= 0 || expected > MAX_LUX) {
            return LightCalibration.Rejected(LightCalibrationError.INVALID_REFERENCE)
        }
        if (sample == null || !valid(sample) || sample.lux == 0.0) {
            return LightCalibration.Rejected(LightCalibrationError.NO_READING)
        }
        val factor = expected / sample.lux
        return if (validFactor(factor)) LightCalibration.Applied(factor)
        else LightCalibration.Rejected(LightCalibrationError.FACTOR_OUT_OF_RANGE)
    }

    fun band(lux: Double): LightBand = when {
        lux < 10 -> LightBand.DARK
        lux < 100 -> LightBand.DIM
        lux < 500 -> LightBand.INDOOR
        lux < 10_000 -> LightBand.BRIGHT
        else -> LightBand.VERY_BRIGHT
    }

    /** Locale-independent CSV: relative monotonic time, raw lux, corrected lux. */
    fun csv(samples: List<LightSample>, factor: Double): String {
        require(validFactor(factor))
        val bounded = samples.takeLast(HISTORY_LIMIT).filter(::valid)
        val start = bounded.firstOrNull()?.timestampNanos ?: 0L
        return buildString {
            append("elapsed_s,raw_lux,corrected_lux\n")
            bounded.forEach {
                append((it.timestampNanos - start) / 1e9).append(',')
                append(it.lux).append(',').append(it.lux * factor).append('\n')
            }
        }
    }
}
