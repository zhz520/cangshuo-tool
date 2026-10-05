package com.cangshuo.toolbox.feature.compass.data

import android.content.Context
import android.hardware.*
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import com.cangshuo.toolbox.feature.compass.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class AndroidCompassRepository(context: Context) : CompassRepository {
    private val context = context.applicationContext
    private val lock = Mutex()
    override fun observe(): Flow<CompassReading> = flow {
        lock.withLock { emitAll(readings()) }
    }.flowOn(Dispatchers.IO)

    private fun readings(): Flow<CompassReading> = callbackFlow {
        val manager = context.getSystemService(SensorManager::class.java) ?: throw CompassUnavailable()
        val vector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acceleration = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetic = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (vector == null && (acceleration == null || magnetic == null)) throw CompassUnavailable()
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        var gravity: FloatArray? = null
        var field: FloatArray? = null
        var gravityTime = 0L
        var fieldTime = 0L
        var magneticAccuracy = -1
        var nextUpdate = 0L
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy = accuracy
            }
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3 || event.values.take(3).any { !it.isFinite() }) return
                val now = SystemClock.elapsedRealtimeNanos()
                fun smooth(old: FloatArray?): FloatArray = FloatArray(3) { index ->
                    val value = event.values[index]
                    old?.let { it[index] + .2f * (value - it[index]) } ?: value
                }
                if (vector == null) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) { gravity = smooth(gravity); gravityTime = now }
                    if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) { field = smooth(field); fieldTime = now; magneticAccuracy = event.accuracy }
                }
                if (now < nextUpdate) return
                nextUpdate = now + 100_000_000L
                try {
                    val matrix = FloatArray(9)
                    val valid = if (vector != null) {
                        val values = event.values.take(4).toFloatArray()
                        if (values.any { !it.isFinite() }) return
                        SensorManager.getRotationMatrixFromVector(matrix, values); true
                    } else gravity != null && field != null && now-gravityTime <= 1_000_000_000L && now-fieldTime <= 1_000_000_000L &&
                        SensorManager.getRotationMatrix(matrix, null, gravity, field)
                    if (!valid || matrix.any { !it.isFinite() }) {
                        trySend(CompassReading(null, magneticAccuracy, null, false, vector != null)); return
                    }
                    val axes = when(display?.rotation) {
                        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    val adjusted = FloatArray(9)
                    check(SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted))
                    val angles = SensorManager.getOrientation(adjusted, FloatArray(3))
                    val flat = abs(angles[1]) <= Math.PI/3 && abs(angles[2]) <= Math.PI/3
                    val error = if (vector != null && event.values.size >= 5) event.values[4].toDouble().takeIf { it.isFinite() && it in 0.0..Math.PI }?.let(Math::toDegrees) else null
                    trySend(CompassReading(if (flat) CompassAngles.normalize(Math.toDegrees(angles[0].toDouble())) else null,
                        if (vector != null) event.accuracy else magneticAccuracy, error, !flat, vector != null))
                } catch (e: Exception) { close(e) }
            }
        }
        try {
            val handler = Handler(Looper.getMainLooper())
            val registered = if (vector != null) manager.registerListener(listener, vector, 100_000, handler)
                else manager.registerListener(listener, acceleration, 100_000, handler) && manager.registerListener(listener, magnetic, 100_000, handler)
            check(registered)
        } catch (e: Exception) { manager.unregisterListener(listener); close(e) }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
