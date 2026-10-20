package com.cangshuo.toolbox.feature.vibrator.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cangshuo.toolbox.feature.vibrator.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AndroidVibrationHardware(private val context: Context) : VibrationHardware {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    override fun capability() = DeviceVibrationCapability(vibrator?.hasVibrator() == true,
        vibrator?.hasVibrator() == true && vibrator.hasAmplitudeControl())
    override fun play(waveform: VibrationWaveform) {
        val motor = vibrator ?: error("No vibrator")
        check(motor.hasVibrator())
        motor.cancel()
        val effect = if (waveform.amplitudeSupported && motor.hasAmplitudeControl())
            VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, -1)
        else VibrationEffect.createWaveform(waveform.timings, -1)
        motor.vibrate(effect)
    }
    override fun cancel() { vibrator?.cancel() }
    override fun now() = SystemClock.elapsedRealtime()
    override fun wall() = System.currentTimeMillis()
}

class LocalVibrationHistoryRepository(context: Context) : VibrationHistoryRepository {
    private val preferences = context.getSharedPreferences("vibration_tests", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    override suspend fun history() = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun save(record: VibrationRecord) = withContext(Dispatchers.IO) {
        require(VibrationPolicy.valid(record))
        mutex.withLock {
            val records = (listOf(record) + read().filterNot { it.id == record.id }).take(VibrationPolicy.HISTORY_LIMIT)
            write(records); records
        }
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { write(emptyList()); emptyList<VibrationRecord>() } }
    private fun read(): List<VibrationRecord> {
        val raw = preferences.getString("history", "[]") ?: "[]"
        check(raw.length <= 32_768)
        val array = JSONArray(raw)
        check(array.length() <= VibrationPolicy.HISTORY_LIMIT)
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            VibrationRecord(it.getString("id"), it.getLong("at"), VibrationPreset.valueOf(it.getString("preset")),
                it.getInt("duration"), it.getInt("strength"), it.getLong("actual"), it.getBoolean("amplitude"))
        }.also { check(VibrationPolicy.valid(it)) } }.also { check(it.map { r -> r.id }.distinct().size == it.size) }
    }
    @SuppressLint("UseKtx")
    private fun write(records: List<VibrationRecord>) {
        val array = JSONArray()
        records.forEach { array.put(JSONObject().put("id", it.id).put("at", it.at).put("preset", it.preset.name)
            .put("duration", it.durationMs).put("strength", it.strengthPercent)
            .put("actual", it.actualDurationMs).put("amplitude", it.amplitudeControlled)) }
        check(preferences.edit().putString("history", array.toString()).commit())
    }
}
