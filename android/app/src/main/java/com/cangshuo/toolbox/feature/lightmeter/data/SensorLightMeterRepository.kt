package com.cangshuo.toolbox.feature.lightmeter.data

import com.cangshuo.toolbox.feature.lightmeter.domain.*
import com.cangshuo.toolbox.feature.sensors.domain.SensorsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Reuses the bounded inventory and lifecycle-cleaned sensor listener. No extra permission. */
class SensorLightMeterRepository(private val sensors: SensorsRepository) : LightMeterRepository {
    override fun observe(): Flow<LightEvent> = flow {
        val sensor = sensors.catalog().sensors.firstOrNull { it.type == 5 && it.liveSupported }
            ?: throw LightSensorUnavailable()
        emit(LightEvent.Source(sensor))
        sensors.observe(sensor.id).collect { reading ->
            val sample = reading.values.firstOrNull()?.let {
                LightSample(it.toDouble(), reading.elapsedNanos, reading.accuracy)
            }
            emit(if (sample != null && LightPolicy.valid(sample)) LightEvent.Reading(sample) else LightEvent.InvalidReading)
        }
    }
}
