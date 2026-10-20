package com.cangshuo.toolbox.feature.vibrator.domain

enum class VibrationPreset { SINGLE, DOUBLE, TRIPLE, HEARTBEAT, ALARM, RAMP, WAVE }
enum class VibrationPhase { IDLE, PLAYING, FINISHED, CANCELLED, INTERRUPTED }
data class VibrationPattern(val preset: VibrationPreset = VibrationPreset.SINGLE,
    val durationMs: Int = 200, val strengthPercent: Int = 70) {
    fun validate() { require(durationMs in MIN_DURATION..MAX_DURATION && strengthPercent in MIN_STRENGTH..MAX_STRENGTH) }
    companion object {
        const val MIN_DURATION = 50
        const val MAX_DURATION = 2_000
        const val MIN_STRENGTH = 5
        const val MAX_STRENGTH = 100
        const val MAX_WAVEFORM_MS = 30_000
    }
}

data class VibrationWaveform(val timings: LongArray, val amplitudes: IntArray, val totalMs: Long) {
    val amplitudeSupported: Boolean get() = amplitudes.isNotEmpty()
}

data class VibrationRecord(val id: String, val at: Long, val preset: VibrationPreset,
    val durationMs: Int, val strengthPercent: Int, val actualDurationMs: Long, val amplitudeControlled: Boolean)
data class DeviceVibrationCapability(val hasVibrator: Boolean, val amplitudeControl: Boolean)

object VibrationPolicy {
    val presets = VibrationPreset.entries
    const val HISTORY_LIMIT = 20

    fun waveform(pattern: VibrationPattern): VibrationWaveform {
        pattern.validate()
        fun run(ms: Long) = ms.coerceIn(10L, pattern.durationMs.toLong())
        fun amp(percent: Int = pattern.strengthPercent) = (percent * 255 / 100).coerceIn(1, 255)
        val full = amp()
        val half = amp(35)
        val third = amp(22)
        val (timings, amplitudes) = when (pattern.preset) {
            VibrationPreset.SINGLE -> longArrayOf(0, run(pattern.durationMs.toLong())) to intArrayOf(0, full)
            VibrationPreset.DOUBLE -> longArrayOf(0, run(90), 80, run(90)) to intArrayOf(0, full, 0, full)
            VibrationPreset.TRIPLE -> longArrayOf(0, run(70), 70, run(70), 70, run(70)) to intArrayOf(0, full, 0, full, 0, full)
            VibrationPreset.HEARTBEAT -> longArrayOf(0, 90, 120, 140, 400, 90, 120, 140) to
                intArrayOf(0, full, 0, half, 0, full, 0, half)
            VibrationPreset.ALARM -> longArrayOf(0, 160, 100, 160, 100, 400, 160, 100, 160) to
                intArrayOf(0, full, 0, full, 0, 0, full, 0, full)
            VibrationPreset.RAMP -> longArrayOf(0, 80, 40, 100, 40, 130, 40, 170) to
                intArrayOf(0, amp(20), 0, amp(40), 0, amp(65), 0, full)
            VibrationPreset.WAVE -> longArrayOf(0, 120, 70, 120, 70, 120, 70, 120, 70, 120) to
                intArrayOf(0, third, 0, half, 0, full, 0, half, 0, third)
        }
        require(timings.size == amplitudes.size && timings.size >= 2 && timings[0] == 0L)
        val total = timings.sum().also { require(it in 1..VibrationPattern.MAX_WAVEFORM_MS) }
        return VibrationWaveform(timings, amplitudes, total)
    }

    fun noAmplitudeWaveform(pattern: VibrationPattern): VibrationWaveform {
        val w = waveform(pattern)
        return w.copy(amplitudes = intArrayOf())
    }

    fun valid(record: VibrationRecord): Boolean = record.id.matches(Regex("[A-Za-z0-9-]{1,64}")) &&
        record.at > 0 && record.preset in presets && record.durationMs in VibrationPattern.MIN_DURATION..VibrationPattern.MAX_DURATION &&
        record.strengthPercent in VibrationPattern.MIN_STRENGTH..VibrationPattern.MAX_STRENGTH &&
        record.actualDurationMs in 1..VibrationPattern.MAX_WAVEFORM_MS.toLong()

    fun csv(records: List<VibrationRecord>) = "time,preset,duration_ms,strength_percent,actual_ms,amplitude_controlled\n" +
        records.joinToString("\n") { "${it.at},${it.preset},${it.durationMs},${it.strengthPercent},${it.actualDurationMs},${it.amplitudeControlled}" }
}

interface VibrationHardware {
    fun capability(): DeviceVibrationCapability
    fun play(waveform: VibrationWaveform)
    fun cancel()
    fun now(): Long
    fun wall(): Long
}

interface VibrationHistoryRepository {
    suspend fun history(): List<VibrationRecord>
    suspend fun save(record: VibrationRecord): List<VibrationRecord>
    suspend fun clear(): List<VibrationRecord>
}
class VibrationUseCases(private val hardware: VibrationHardware, private val history: VibrationHistoryRepository) {
    fun capability() = hardware.capability()
    fun play(waveform: VibrationWaveform) = hardware.play(waveform)
    fun cancel() = hardware.cancel()
    fun now() = hardware.now()
    fun wall() = hardware.wall()
    suspend fun history() = history.history()
    suspend fun save(record: VibrationRecord) = history.save(record)
    suspend fun clear() = history.clear()
}
