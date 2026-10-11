package com.cangshuo.toolbox.feature.morse.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.cangshuo.toolbox.feature.morse.domain.MorseTone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/**
 * Streams a Morse timeline as a sine tone. One Morse unit is 1200 / wpm ms (PARIS standard).
 * Safe to call from a background coroutine; [stop] cancels the current playback.
 */
class MorseAudioPlayer {

    private val cancelled = AtomicBoolean(false)
    @Volatile private var track: AudioTrack? = null

    /** Plays [tones]; [onFinished] receives true when playback completed, false when cancelled. */
    fun play(tones: List<MorseTone>, wpm: Int, onFinished: (Boolean) -> Unit) {
        cancelled.set(false)
        val safeWpm = wpm.coerceIn(MIN_WPM, MAX_WPM)
        val unitMs = 1200.0 / safeWpm
        val sampleRate = SAMPLE_RATE
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onFinished(false)
            return
        }
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer.coerceAtLeast(BLOCK_SAMPLES * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        return try {
            track = created
            created.play()
            val block = ShortArray(BLOCK_SAMPLES)
            var phase = 0.0
            var completed = true
            outer@ for (tone in tones) {
                val durationMs = tone.units * unitMs
                var remainingSamples = (durationMs * sampleRate / 1000.0).toInt()
                val sounding = tone is MorseTone.Sound
                while (remainingSamples > 0) {
                    if (cancelled.get()) {
                        completed = false
                        break@outer
                    }
                    val chunk = minOf(remainingSamples, block.size)
                    for (i in 0 until chunk) {
                        if (sounding) {
                            block[i] = (sin(phase) * Short.MAX_VALUE * 0.6).toInt().toShort()
                            phase += 2.0 * PI * TONE_HZ / sampleRate
                            if (phase > 2.0 * PI) phase -= 2.0 * PI
                        } else {
                            block[i] = 0
                        }
                    }
                    val written = created.write(block, 0, chunk, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        completed = false
                        break@outer
                    }
                    remainingSamples -= chunk
                }
            }
            if (completed) {
                created.stop()
            }
            onFinished(completed)
            Unit
        } catch (_: Exception) {
            onFinished(false)
        } finally {
            try {
                created.release()
            } catch (_: Exception) {
                // Already released.
            }
            track = null
        }
    }

    fun stop() {
        cancelled.set(true)
        try {
            track?.pause()
        } catch (_: Exception) {
            // Ignore races with release().
        }
    }

    companion object {
        const val MIN_WPM = 5
        const val MAX_WPM = 30
        const val DEFAULT_WPM = 15
        private const val SAMPLE_RATE = 8000
        private const val TONE_HZ = 700.0
        private const val BLOCK_SAMPLES = 1024
    }
}
