package com.cangshuo.toolbox.feature.noise.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.cangshuo.toolbox.feature.noise.domain.NoiseLayerState
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings
import com.cangshuo.toolbox.feature.noise.domain.NoiseSynthesis
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

class NoiseAudioEngine(private val onFailure: ((Long) -> Unit)? = null) {
    private val settings = AtomicReference(NoiseSettings())
    @Volatile private var fadeMultiplier = 1f
    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var attempt = 0L
    private val failureReported = AtomicBoolean(false)

    @Synchronized
    fun start(): Long? {
        if (failureReported.get()) return null
        running = true
        attempt++
        if (thread?.isAlive != true) launchAudioThreadLocked()
        return attempt
    }

    @Synchronized
    private fun launchAudioThreadLocked() {
        thread = Thread(::runLoop, "noise-audio").apply {
            isDaemon = true
            start()
        }
    }

    fun update(settings: NoiseSettings) {
        this.settings.set(settings)
    }

    fun setFadeMultiplier(multiplier: Float) {
        fadeMultiplier = multiplier.coerceIn(0f, 1f)
    }

    @Synchronized
    fun stop() {
        running = false
    }

    fun close() {
        running = false
        try {
            track?.stop()
        } catch (_: Exception) {
            // The output may already be stopping or released.
        }
        try {
            thread?.join(500L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBuffer = AudioTrack.getMinBufferSize(
            NoiseSynthesis.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            running = false
            reportFailure()
            return
        }
        val created = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(NoiseSynthesis.SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer, NoiseSynthesis.RENDER_CHUNK_FRAMES * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            running = false
            reportFailure()
            return
        }
        track = created
        try {
            created.play()
            val states = mutableMapOf<NoiseType, NoiseLayerState>()
            val shorts = ShortArray(NoiseSynthesis.RENDER_CHUNK_FRAMES * 2)
            var gain = 0f
            var smoothedFade = 1f
            val step = 1f / (NoiseSynthesis.SAMPLE_RATE * 0.18f)
            val fadeStep = 1f / (NoiseSynthesis.SAMPLE_RATE * 0.25f)
            while (true) {
                val target = if (running) 1f else 0f
                val fadeTarget = fadeMultiplier
                val mono = NoiseSynthesis.renderMixed(settings.get(), states)
                for (frame in mono.indices) {
                    gain = if (gain < target) minOf(target, gain + step) else maxOf(target, gain - step)
                    smoothedFade += (fadeTarget - smoothedFade).coerceIn(-fadeStep, fadeStep)
                    val value = (mono[frame] * gain * smoothedFade * Short.MAX_VALUE)
                        .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    shorts[frame * 2] = value
                    shorts[frame * 2 + 1] = value
                }
                val canExit = synchronized(this) {
                    !running && gain <= 0.0001f
                }
                if (canExit) break
                val written = created.write(shorts, 0, shorts.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    reportFailure()
                    break
                }
            }
            if (running) created.stop()
        } catch (_: Exception) {
            if (running) reportFailure()
        } finally {
            try {
                created.release()
            } catch (_: Exception) {
                // Already released.
            }
            synchronized(this) {
                track = null
                if (running && !failureReported.get()) {
                    launchAudioThreadLocked()
                } else {
                    running = false
                    thread = null
                }
            }
        }
    }

    private fun reportFailure() {
        if (failureReported.compareAndSet(false, true)) onFailure?.invoke(attempt)
    }
}
