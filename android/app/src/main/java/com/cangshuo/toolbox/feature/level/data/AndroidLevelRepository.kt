package com.cangshuo.toolbox.feature.level.data

import android.content.Context
import android.hardware.*
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import com.cangshuo.toolbox.feature.level.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidLevelRepository(context: Context) : LevelRepository {
    private val context = context.applicationContext
    private val lock = Mutex()
    override fun observe(): Flow<LevelReading?> = flow { lock.withLock { emitAll(readings()) } }.flowOn(Dispatchers.IO)
    private fun readings(): Flow<LevelReading?> = callbackFlow {
        val manager = context.getSystemService(SensorManager::class.java) ?: throw LevelUnavailable()
        val gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val sensor = gravity ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: throw LevelUnavailable()
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        var filtered: DoubleArray? = null
        var nextTime = 0L
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3 || event.values.take(3).any { !it.isFinite() }) { trySend(null); return }
                val now = SystemClock.elapsedRealtimeNanos()
                if (now < nextTime) return
                nextTime = now + 100_000_000L
                val values = DoubleArray(3) { index ->
                    val raw = event.values[index].toDouble()
                    if (gravity != null) raw else filtered?.let { it[index] + .15 * (raw - it[index]) } ?: raw
                }
                filtered = values
                val rotation = display?.rotation ?: 0
                val angles = LevelMath.fromGravity(values[0],values[1],values[2],rotation)
                trySend(angles?.let { LevelReading(it,rotation,event.accuracy,gravity == null) })
            }
        }
        try { check(manager.registerListener(listener, sensor, 100_000, Handler(Looper.getMainLooper()))) }
        catch (e: Exception) { manager.unregisterListener(listener); close(e) }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
