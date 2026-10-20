package com.cangshuo.toolbox.feature.fish.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cangshuo.toolbox.feature.fish.domain.FishFeedback
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Generates a short wooden click locally; no third-party audio asset is bundled or downloaded. */
class SyntheticFishFeedback(private val context: Context) : FishFeedback {
    private val random = SecureRandom()
    private val loaded = CompletableDeferred<Boolean>()
    private var pool: SoundPool? = null
    private var soundId = 0
    private var loadStarted = false
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override suspend fun prepare(): Boolean {
        if (!loadStarted) {
            loadStarted = true
            val file = withContext(Dispatchers.IO) { writeWave() }
            if (file == null) { loaded.complete(false); return false }
            val soundPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
            soundPool.setOnLoadCompleteListener { _, _, status -> loaded.complete(status == 0) }
            pool = soundPool
            soundId = soundPool.load(file.absolutePath, 1)
            if (soundId == 0) loaded.complete(false)
        }
        return loaded.await()
    }

    override fun tap(sound: Boolean, haptic: Boolean): Boolean {
        var played = false
        if (sound && soundId != 0) {
            val result = pool?.play(soundId, .85f, .85f, 1, 0, .96f + random.nextFloat() * .08f) ?: 0
            played = result != 0
        }
        if (haptic && vibrator?.hasVibrator() == true) {
            val amplitude = if (vibrator.hasAmplitudeControl()) 105 else VibrationEffect.DEFAULT_AMPLITUDE
            vibrator.vibrate(VibrationEffect.createOneShot(18, amplitude)); played = true
        }
        return played
    }

    override fun stop() { pool?.autoPause() }
    override fun release() { pool?.release(); pool = null }

    private fun writeWave(): File? = runCatching {
        val sampleRate = 44_100
        val frames = 2_400
        val data = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-58.0 * t)
            val tone = sin(2.0 * PI * 470.0 * t) * .52 + sin(2.0 * PI * 1_130.0 * t) * .22 + sin(2.0 * PI * 2_050.0 * t) * .08
            val noise = (random.nextDouble() * 2.0 - 1.0) * .04
            val value = ((tone + noise) * envelope * Short.MAX_VALUE * .72).toInt().coerceIn(-32768, 32767)
            data[i * 2] = (value and 0xff).toByte()
            data[i * 2 + 1] = ((value shr 8) and 0xff).toByte()
        }
        val file = File(context.cacheDir, "wooden_fish_tick_v1.wav")
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { out ->
            fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
            fun leInt(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff); out.write((value shr 16) and 0xff); out.write((value shr 24) and 0xff) }
            fun leShort(value: Int) { out.write(value and 0xff); out.write((value shr 8) and 0xff) }
            ascii("RIFF"); leInt(36 + data.size); ascii("WAVEfmt "); leInt(16); leShort(1); leShort(1)
            leInt(sampleRate); leInt(sampleRate * 2); leShort(2); leShort(16); ascii("data"); leInt(data.size); out.write(data)
        }
        file
    }.getOrNull()
}
