package com.cangshuo.toolbox.feature.sensors.data

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.os.Looper
import com.cangshuo.toolbox.feature.deviceinfo.domain.DeviceInfoFormat
import com.cangshuo.toolbox.feature.sensors.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class AndroidSensorsRepository(context: Context) : SensorsRepository {
    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private var byId = emptyMap<Int, Sensor>()
    override suspend fun catalog(): SensorCatalog = withContext(Dispatchers.IO) {
        val sensors = checkNotNull(manager).getSensorList(Sensor.TYPE_ALL)
        val bounded = sensors.take(128)
        byId = bounded.mapIndexed { index, sensor -> index to sensor }.toMap()
        SensorCatalog(bounded.mapIndexed { index, sensor ->
            SensorDescriptor(index, DeviceInfoFormat.text(sensor.name) ?: sensor.stringType,
                DeviceInfoFormat.text(sensor.vendor).orEmpty(), sensor.type, sensor.version, sensor.maximumRange,
                sensor.resolution, sensor.power, sensor.minDelay, sensor.isWakeUpSensor,
                sensor.type in SensorUnits.liveTypes)
        }, sensors.size > 128)
    }
    override fun observe(id: Int): Flow<SensorReading> = callbackFlow {
        val sensor = byId[id] ?: error("Sensor unavailable")
        check(sensor.type in SensorUnits.liveTypes)
        val service = checkNotNull(manager)
        var lastTime = Long.MIN_VALUE
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                // Hardware timestamps are monotonic, independent of wall-clock changes.
                if (lastTime != Long.MIN_VALUE && event.timestamp >= lastTime && event.timestamp-lastTime < 100_000_000L) return
                lastTime = event.timestamp
                trySend(SensorReading(event.values.take(16).map { it.takeIf(Float::isFinite) }, event.accuracy,
                    event.timestamp, event.values.size > 16))
            }
        }
        try {
            check(service.registerListener(listener, sensor, 100_000, Handler(Looper.getMainLooper())))
        } catch (e: Exception) { service.unregisterListener(listener); close(e) }
        awaitClose { service.unregisterListener(listener) }
    }.conflate().flowOn(Dispatchers.IO)
}
