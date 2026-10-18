package com.cangshuo.toolbox.feature.noise.domain

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Small deterministic DSP core. It intentionally creates no waveform assets. */
class NoiseLayerState(seed: Long) {
    private var randomState = if (seed == 0L) 0x9E3779B97F4A7C15uL else seed.toULong()
    private var pink0 = 0.0
    private var pink1 = 0.0
    private var pink2 = 0.0
    private var pink3 = 0.0
    private var pink4 = 0.0
    private var pink5 = 0.0
    private var pink6 = 0.0
    private var brown = 0.0
    private var lowPass = 0.0
    private var dropEnvelope = 0.0
    private var oceanPhase = 0.0
    private var fanPhase = 0.0

    fun next(type: NoiseType, sampleRate: Int): Float {
        val white = random()
        return when (type) {
            NoiseType.WHITE -> (white * 0.42).toFloat()
            NoiseType.PINK -> {
                pink0 = 0.99886 * pink0 + white * 0.0555179
                pink1 = 0.99332 * pink1 + white * 0.0750759
                pink2 = 0.96900 * pink2 + white * 0.1538520
                pink3 = 0.86650 * pink3 + white * 0.3104856
                pink4 = 0.55000 * pink4 + white * 0.5329522
                pink5 = -0.7616 * pink5 - white * 0.0168980
                val value = pink0 + pink1 + pink2 + pink3 + pink4 + pink5 + pink6 + white * 0.5362
                pink6 = white * 0.115926
                (value * 0.11).toFloat().coerceIn(-0.9f, 0.9f)
            }
            NoiseType.BROWN -> {
                brown = (brown + white * 0.042) / 1.018
                (brown * 3.2).toFloat().coerceIn(-0.9f, 0.9f)
            }
            NoiseType.RAIN -> {
                dropEnvelope *= 0.99965
                val trigger = random()
                if (trigger > 0.99972) dropEnvelope = max(dropEnvelope, 0.25 + random() * 0.55)
                lowPass = lowPass * 0.82 + white * 0.18
                (lowPass * 0.34 + dropEnvelope * 0.42).toFloat().coerceIn(-0.9f, 0.9f)
            }
            NoiseType.OCEAN -> {
                brown = (brown + white * 0.025) / 1.025
                lowPass = lowPass * 0.995 + brown * 0.005
                oceanPhase += 2.0 * PI * 0.065 / sampleRate
                if (oceanPhase > 2.0 * PI) oceanPhase -= 2.0 * PI
                val swell = 0.28 + 0.72 * (0.5 + 0.5 * sin(oceanPhase))
                (lowPass * 2.4 * swell).toFloat().coerceIn(-0.9f, 0.9f)
            }
            NoiseType.FAN -> {
                brown = (brown + white * 0.02) / 1.02
                fanPhase += 2.0 * PI * 58.0 / sampleRate
                if (fanPhase > 2.0 * PI) fanPhase -= 2.0 * PI
                (brown * 0.72 + white * 0.09 + sin(fanPhase) * 0.055).toFloat().coerceIn(-0.9f, 0.9f)
            }
        }
    }

    private fun random(): Double {
        var x = randomState
        x = x xor (x shl 13)
        x = x xor (x shr 7)
        x = x xor (x shl 17)
        randomState = x
        return ((x shr 40).toLong() and 0xFFFFFFL) / 8_388_608.0 - 1.0
    }
}

object NoiseSynthesis {
    const val SAMPLE_RATE = 44_100
    const val RENDER_CHUNK_FRAMES = 1024

    fun renderMixed(settings: NoiseSettings, states: MutableMap<NoiseType, NoiseLayerState>): FloatArray {
        val active = NoisePolicy.activeTypes(settings.levels)
        val output = FloatArray(RENDER_CHUNK_FRAMES)
        if (active.isEmpty()) return output
        val weight = NoisePolicy.mixWeight(settings.levels)
        for (index in output.indices) {
            var sample = 0.0
            for (type in active) {
                val level = settings.levels[type] ?: 0f
                val state = states.getOrPut(type) { NoiseLayerState(seedFor(type)) }
                sample += state.next(type, SAMPLE_RATE) * level
            }
            val normalized = (sample / weight * settings.masterVolume).toFloat()
            output[index] = kotlin.math.tanh(normalized * 1.1f) * 0.82f
        }
        return output
    }

    fun seedFor(type: NoiseType): Long = when (type) {
        NoiseType.WHITE -> 0x1234ABCDL
        NoiseType.PINK -> 0x2345BCDEL
        NoiseType.BROWN -> 0x3456CDEFL
        NoiseType.RAIN -> 0x4567DEF1L
        NoiseType.OCEAN -> 0x5678EF12L
        NoiseType.FAN -> 0x6789F123L
    }

    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        return sqrt(samples.sumOf { it.toDouble() * it }.div(samples.size))
    }
}
