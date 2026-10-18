package com.cangshuo.toolbox.feature.noise.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.cangshuo.toolbox.MainActivity
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.noise.data.NoiseAudioEngine
import com.cangshuo.toolbox.feature.noise.data.NoisePlaybackRuntime
import com.cangshuo.toolbox.feature.noise.data.NoiseSettingsStore
import com.cangshuo.toolbox.feature.noise.domain.NoiseError
import com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackPolicy
import com.cangshuo.toolbox.feature.noise.domain.NoisePlaybackState
import com.cangshuo.toolbox.feature.noise.domain.NoisePolicy
import com.cangshuo.toolbox.feature.noise.domain.NoiseSettings
import com.cangshuo.toolbox.feature.noise.domain.NoiseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NoisePlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: NoiseSettingsStore
    private val notificationManager get() = getSystemService(NotificationManager::class.java)
    private val audioManager get() = getSystemService(AudioManager::class.java)
    private var engine: NoiseAudioEngine? = null
    private var ticker: Job? = null
    private var remainingMillis: Long? = null
    private var deadlineMillis: Long? = null
    private var focusHeld = false
    private var playing = false
    private var paused = false
    private var sessionGeneration = 0L
    private var tickerGeneration = Long.MIN_VALUE
    private var lastStartId = 0
    private var engineAttemptId: Long? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            scope.launch { pausePlayback() }
        }
    }
    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                scope.launch { pausePlayback() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = NoiseSettingsStore(this)
        createChannel()
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_RELOAD -> reloadSettings()
            ACTION_SET_TIMER -> applyTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_STOP -> stopPlayback(startId = startId)
            ACTION_START -> startPlayback(resetTimer = true)
            null -> restorePlaybackState()
        }
        return START_STICKY
    }

    private fun restorePlaybackState() {
        val preferences = servicePreferences()
        val mode = preferences.getString(KEY_STATE, null)
            ?: if (preferences.getBoolean(KEY_SHOULD_PLAY, false)) STATE_PLAYING else STATE_IDLE
        when (mode) {
            STATE_PLAYING -> {
                val storedDeadline = preferences.getLong(KEY_DEADLINE, 0L)
                val storedRemaining = preferences.getLong(KEY_REMAINING, -1L).takeIf { it >= 0L }
                remainingMillis = if (storedDeadline > 0L) {
                    NoisePlaybackPolicy.remaining(storedDeadline, SystemClock.elapsedRealtime())
                } else storedRemaining
                if (storedDeadline > 0L && remainingMillis == 0L) {
                    stopPlayback()
                    return
                }
                startPlayback(resetTimer = false)
            }
            STATE_PAUSED -> {
                remainingMillis = preferences.getLong(KEY_REMAINING, -1L).takeIf { it >= 0L }
                if (remainingMillis == 0L) {
                    stopPlayback()
                    return
                }
                playing = false
                paused = true
                persistPlaybackState(STATE_PAUSED)
                publish(playing = false, paused = true)
                startForeground(NOTIFICATION_ID, buildNotification(store.read()))
            }
            else -> stopSelfResult(lastStartId)
        }
    }

    private fun startPlayback(resetTimer: Boolean) {
        sessionGeneration++
        val settings = startableSettings()
        playing = true
        paused = false
        if (resetTimer) {
            remainingMillis = NoisePlaybackPolicy.timerMillis(settings.timerMinutes)
        }
        deadlineMillis = NoisePlaybackPolicy.deadline(remainingMillis, SystemClock.elapsedRealtime())
        persistPlaybackState(STATE_PLAYING)
        startForeground(NOTIFICATION_ID, buildNotification(settings))
        if (!requestAudioFocus()) {
            playing = false
            paused = false
            remainingMillis = null
            deadlineMillis = null
            persistPlaybackState(STATE_IDLE)
            publish(playing = false, paused = false, error = NoiseError.AUDIO_UNAVAILABLE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(lastStartId)
            return
        }
        startActiveEngine(settings)
        publish(playing = true, paused = false)
        ensureTicker()
    }

    private fun pausePlayback() {
        if (!playing) {
            if (!paused) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(lastStartId)
            }
            return
        }
        sessionGeneration++
        freezeRemaining()
        playing = false
        paused = true
        persistPlaybackState(STATE_PAUSED)
        engine?.stop()
        engineAttemptId = null
        ticker?.cancel()
        ticker = null
        abandonAudioFocus()
        publish(playing = false, paused = true)
        startForeground(NOTIFICATION_ID, buildNotification(store.read()))
    }

    private fun resumePlayback() {
        if (!paused) {
            if (!playing) stopSelfResult(lastStartId)
            return
        }
        if (!requestAudioFocus()) {
            publish(playing = false, paused = true, error = NoiseError.AUDIO_UNAVAILABLE)
            return
        }
        remainingMillis?.let { remaining ->
            if (remaining <= 0L) {
                stopPlayback()
                return
            }
            deadlineMillis = NoisePlaybackPolicy.deadline(remaining, SystemClock.elapsedRealtime())
        }
        playing = true
        paused = false
        persistPlaybackState(STATE_PLAYING)
        val settings = startableSettings()
        startActiveEngine(settings)
        startForeground(NOTIFICATION_ID, buildNotification(settings))
        publish(playing = true, paused = false)
        ensureTicker()
    }

    private fun reloadSettings() {
        val settings = store.read()
        if (settings.activeTypes.isEmpty()) {
            if (playing || paused) stopPlayback()
            return
        }
        engine?.update(settings)
        if (playing || paused) startForeground(NOTIFICATION_ID, buildNotification(settings))
    }

    private fun applyTimer(minutes: Int) {
        val safeMinutes = minutes.takeIf { it in NoisePolicy.timerOptions } ?: store.read().timerMinutes
        remainingMillis = NoisePlaybackPolicy.timerMillis(safeMinutes)
        deadlineMillis = if (playing) {
            NoisePlaybackPolicy.deadline(remainingMillis, SystemClock.elapsedRealtime())
        } else null
        engine?.setFadeMultiplier(NoisePlaybackPolicy.fadeMultiplier(remainingMillis))
        if (playing || paused) persistPlaybackState(if (playing) STATE_PLAYING else STATE_PAUSED)
        publish(playing, paused)
        if (playing || paused) {
            startForeground(NOTIFICATION_ID, buildNotification(store.read()))
        }
        if (playing) {
            ensureTicker()
        }
    }

    private fun ensureTicker() {
        val generation = sessionGeneration
        if (ticker?.isActive == true && tickerGeneration == generation) return
        ticker?.cancel()
        tickerGeneration = generation
        ticker = scope.launch {
            var notificationTick = 0
            while (isActive) {
                if (!playing || generation != sessionGeneration) break
                val deadline = deadlineMillis
                if (deadline == null) {
                    engine?.setFadeMultiplier(1f)
                    publish(playing = true, paused = false)
                    break
                }
                remainingMillis = NoisePlaybackPolicy.remaining(deadline, SystemClock.elapsedRealtime())
                engine?.setFadeMultiplier(NoisePlaybackPolicy.fadeMultiplier(remainingMillis))
                if (remainingMillis == 0L) {
                    stopPlayback()
                    break
                }
                publish(playing = true, paused = false)
                notificationTick++
                if (notificationTick % 5 == 0) {
                    notificationManager?.notify(NOTIFICATION_ID, buildNotification(store.read()))
                }
                delay(1_000L)
            }
        }
    }

    private fun freezeRemaining() {
        val deadline = deadlineMillis ?: return
        remainingMillis = NoisePlaybackPolicy.remaining(deadline, SystemClock.elapsedRealtime())
        deadlineMillis = null
    }

    private fun stopPlayback(discardEngine: Boolean = false, startId: Int = lastStartId) {
        sessionGeneration++
        playing = false
        paused = false
        remainingMillis = null
        deadlineMillis = null
        ticker?.cancel()
        ticker = null
        engine?.stop()
        engineAttemptId = null
        if (discardEngine) engine = null
        persistPlaybackState(STATE_IDLE)
        abandonAudioFocus()
        publish(playing = false, paused = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    private fun publish(playing: Boolean, paused: Boolean, error: NoiseError? = null) {
        val seconds = when {
            playing -> NoisePlaybackPolicy.remainingSeconds(
                NoisePlaybackPolicy.remaining(deadlineMillis, SystemClock.elapsedRealtime()),
            ) ?: NoisePlaybackPolicy.remainingSeconds(remainingMillis)
            paused -> NoisePlaybackPolicy.remainingSeconds(remainingMillis)
            else -> null
        }
        NoisePlaybackRuntime.publish(
            NoisePlaybackState(
                playing = playing,
                paused = paused,
                remainingSeconds = seconds,
                error = error,
            ),
        )
    }

    private fun startableSettings(): NoiseSettings {
        val settings = store.read()
        val normalized = if (settings.activeTypes.isEmpty()) {
            settings.copy(levels = settings.levels + (NoiseType.WHITE to 0.55f))
        } else settings
        store.update(normalized)
        return normalized
    }

    private fun persistPlaybackState(state: String) {
        servicePreferences().edit(commit = true) {
            putString(KEY_STATE, state)
            putBoolean(KEY_SHOULD_PLAY, state == STATE_PLAYING)
            val remaining = remainingMillis
            if (remaining == null) remove(KEY_REMAINING) else putLong(KEY_REMAINING, remaining)
            val deadline = deadlineMillis
            if (deadline == null) remove(KEY_DEADLINE) else putLong(KEY_DEADLINE, deadline)
        }
    }

    private fun newEngine(): NoiseAudioEngine {
        var created: NoiseAudioEngine? = null
        return NoiseAudioEngine { attempt ->
            scope.launch {
                if (engine === created && engineAttemptId == attempt && playing) {
                    stopPlayback(discardEngine = true)
                    NoisePlaybackRuntime.publish(NoisePlaybackState(error = NoiseError.AUDIO_UNAVAILABLE))
                }
            }
        }.also { created = it }
    }

    private fun startActiveEngine(settings: NoiseSettings) {
        var active = engine ?: newEngine().also { engine = it }
        active.update(settings)
        active.setFadeMultiplier(1f)
        var attempt = active.start()
        if (attempt == null) {
            active.close()
            active = newEngine().also { engine = it }
            active.update(settings)
            active.setFadeMultiplier(1f)
            attempt = active.start()
        }
        engineAttemptId = requireNotNull(attempt)
    }

    private fun requestAudioFocus(): Boolean {
        if (focusHeld) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusHeld
    }

    private fun abandonAudioFocus() {
        if (!focusHeld) return
        audioManager.abandonAudioFocusRequest(focusRequest)
        focusHeld = false
    }

    private fun buildNotification(settings: NoiseSettings): Notification {
        val mix = settings.activeTypes.joinToString(" · ") { type ->
            getString(
                when (type) {
                    NoiseType.WHITE -> R.string.noise_white
                    NoiseType.PINK -> R.string.noise_pink
                    NoiseType.BROWN -> R.string.noise_brown
                    NoiseType.RAIN -> R.string.noise_rain
                    NoiseType.OCEAN -> R.string.noise_ocean
                    NoiseType.FAN -> R.string.noise_fan
                },
            )
        }.ifBlank { getString(R.string.noise_white) }
        val timerSeconds = when {
            playing -> NoisePlaybackPolicy.remainingSeconds(
                NoisePlaybackPolicy.remaining(deadlineMillis, SystemClock.elapsedRealtime()),
            ) ?: NoisePlaybackPolicy.remainingSeconds(remainingMillis)
            else -> NoisePlaybackPolicy.remainingSeconds(remainingMillis)
        }
        val content = if (timerSeconds == null) mix else getString(
            R.string.noise_notification_with_timer,
            mix,
            formatTimer(timerSeconds),
        )
        val open = PendingIntent.getActivity(
            this,
            OPEN_REQUEST,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleTitle = getString(if (playing) R.string.noise_pause else R.string.noise_resume)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_noise_notification)
            .setContentTitle(getString(if (playing) R.string.noise_notification_playing else R.string.noise_notification_paused))
            .setContentText(content)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    ),
                    toggleTitle,
                    servicePending(if (playing) ACTION_PAUSE else ACTION_RESUME, TOGGLE_REQUEST),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.noise_stop),
                    servicePending(ACTION_STOP, STOP_REQUEST),
                ).build(),
            )
            .build()
    }

    private fun formatTimer(seconds: Long): String {
        val minutes = (seconds / 60).coerceAtLeast(0)
        return if (minutes >= 60) "%d:%02d".format(minutes / 60, minutes % 60)
        else "%d:%02d".format(minutes, seconds % 60)
    }

    private fun servicePending(action: String, request: Int): PendingIntent = PendingIntent.getService(
        this,
        request,
        Intent(this, NoisePlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.noise_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.noise_notification_channel_desc)
                setShowBadge(false)
            },
        )
    }

    private fun servicePreferences() =
        getSharedPreferences(SERVICE_PREFERENCES, Context.MODE_PRIVATE)

    override fun onDestroy() {
        ticker?.cancel()
        engine?.close()
        engine = null
        engineAttemptId = null
        abandonAudioFocus()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {
            // Receiver was not registered.
        }
        scope.cancel()
        val error = NoisePlaybackRuntime.state.value.error
        NoisePlaybackRuntime.publish(NoisePlaybackState(error = error))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "noise_playback"
        private const val NOTIFICATION_ID = 8301
        private const val OPEN_REQUEST = 8302
        private const val TOGGLE_REQUEST = 8303
        private const val STOP_REQUEST = 8304
        private const val SERVICE_PREFERENCES = "noise_service_v1"
        private const val KEY_SHOULD_PLAY = "should_play"
        private const val KEY_STATE = "state"
        private const val KEY_REMAINING = "remaining_millis"
        private const val KEY_DEADLINE = "deadline_elapsed_millis"
        private const val STATE_PLAYING = "PLAYING"
        private const val STATE_PAUSED = "PAUSED"
        private const val STATE_IDLE = "IDLE"
        private const val ACTION_START = "com.cangshuo.toolbox.noise.START"
        private const val ACTION_PAUSE = "com.cangshuo.toolbox.noise.PAUSE"
        private const val ACTION_RESUME = "com.cangshuo.toolbox.noise.RESUME"
        private const val ACTION_STOP = "com.cangshuo.toolbox.noise.STOP"
        private const val ACTION_RELOAD = "com.cangshuo.toolbox.noise.RELOAD"
        private const val ACTION_SET_TIMER = "com.cangshuo.toolbox.noise.SET_TIMER"
        private const val EXTRA_MINUTES = "minutes"

        fun start(context: Context) = send(context, ACTION_START)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun reload(context: Context) = send(context, ACTION_RELOAD)
        fun updateTimer(context: Context, minutes: Int) {
            context.startService(
                Intent(context, NoisePlaybackService::class.java)
                    .setAction(ACTION_SET_TIMER)
                    .putExtra(EXTRA_MINUTES, minutes),
            )
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, NoisePlaybackService::class.java).setAction(action)
            if (action == ACTION_START) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
