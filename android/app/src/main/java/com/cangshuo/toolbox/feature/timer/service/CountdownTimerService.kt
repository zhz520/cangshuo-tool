package com.cangshuo.toolbox.feature.timer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import com.cangshuo.toolbox.MainActivity
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.ToolboxApplication
import com.cangshuo.toolbox.feature.timer.data.LocalCountdownRepository
import com.cangshuo.toolbox.feature.timer.domain.CountdownPolicy
import com.cangshuo.toolbox.feature.timer.domain.CountdownStatus
import com.cangshuo.toolbox.feature.timer.domain.CountdownTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CountdownTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository get() = (application as ToolboxApplication).container.countdownRepository
    private val notificationManager get() = getSystemService(NotificationManager::class.java)
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0
    private var foregrounded = false
    private var lastNotifiedSecond = Long.MIN_VALUE

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val current = repository.state.value
        val needsForeground = CountdownPolicy.requiresForegroundService(current) ||
            (current.status == CountdownStatus.FINISHED && !current.completionNotified)
        if (!needsForeground) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!foregrounded) {
            startForeground(ONGOING_ID, buildOngoing(current))
            foregrounded = true
        } else {
            notifyNow(current)
        }
        if (current.status == CountdownStatus.FINISHED && !current.completionNotified) {
            postCompletionIfNeeded(current)
            finishService()
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> {
                repository.pause()
                ticker?.cancel()
                ticker = null
                releaseWakeLock()
                val paused = repository.state.value
                if (paused.status == CountdownStatus.PAUSED) notifyNow(paused) else finishService()
            }
            ACTION_RESUME -> {
                repository.resume()
                ensureTicker()
            }
            ACTION_ADD_MINUTE -> {
                repository.extend()
                notifyNow(repository.state.value)
                ensureTicker()
            }
            ACTION_START, null -> ensureTicker()
        }
        return START_STICKY
    }

    private fun ensureTicker() {
        val initial = repository.state.value
        if (initial.status == CountdownStatus.PAUSED) {
            releaseWakeLock()
            notifyNow(initial)
            return
        }
        if (initial.status != CountdownStatus.RUNNING) {
            finishService()
            return
        }
        acquireWakeLock()
        if (ticker?.isActive == true) return
        lastNotifiedSecond = Long.MIN_VALUE
        ticker = scope.launch {
            while (isActive) {
                val state = repository.state.value
                var nextDelay = 250L
                when (state.status) {
                    CountdownStatus.RUNNING -> {
                        val remaining = state.remainingAt(repository.nowElapsedMillis())
                        if (remaining <= 0L) {
                            repository.finish()
                            postCompletionIfNeeded(repository.state.value)
                            finishService()
                            break
                        }
                        val seconds = state.remainingSecondsAt(repository.nowElapsedMillis())
                        if (seconds != lastNotifiedSecond) {
                            lastNotifiedSecond = seconds
                            notifyNow(state.copy(remainingMillis = remaining))
                        }
                        if (remaining <= 1_000L) nextDelay = 100L
                    }
                    CountdownStatus.PAUSED -> {
                        releaseWakeLock()
                        notifyNow(state)
                        break
                    }
                    CountdownStatus.IDLE, CountdownStatus.FINISHED -> {
                        postCompletionIfNeeded(state)
                        finishService()
                        break
                    }
                }
                delay(nextDelay)
            }
            ticker = null
        }
    }

    private fun notifyNow(state: CountdownTimerState) {
        notificationManager?.notify(ONGOING_ID, buildOngoing(state))
    }

    private fun finishService() {
        ticker?.cancel()
        ticker = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregrounded = false
        stopSelfResult(lastStartId)
    }

    private fun buildOngoing(state: CountdownTimerState): Notification {
        val remaining = CountdownPolicy.format(state.remainingAt(repository.nowElapsedMillis()))
        val paused = state.status == CountdownStatus.PAUSED
        val content = if (state.label.isBlank()) remaining else remaining + " · " + state.label
        val title = getString(if (paused) R.string.countdown_notification_paused else R.string.countdown_notification_running)
        val toggleTitle = getString(if (paused) R.string.countdown_resume else R.string.countdown_pause)
        return Notification.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                action(
                    if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    toggleTitle,
                    if (paused) ACTION_RESUME else ACTION_PAUSE,
                    REQUEST_TOGGLE,
                ),
            )
            .addAction(action(android.R.drawable.ic_input_add, getString(R.string.countdown_add_minute), ACTION_ADD_MINUTE, REQUEST_ADD))
            .build()
    }

    private fun postCompletion(state: CountdownTimerState) {
        val text = if (state.label.isBlank()) getString(R.string.countdown_finished_message)
        else state.label
        notificationManager?.notify(
            COMPLETION_ID,
            Notification.Builder(this, COMPLETION_CHANNEL)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setContentTitle(getString(R.string.countdown_finished_title))
                .setContentText(text)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .build(),
        )
    }

    private fun postCompletionIfNeeded(state: CountdownTimerState) {
        if (state.status != CountdownStatus.FINISHED || state.completionNotified) return
        postCompletion(state)
        repository.markCompletionNotified()
    }

    private fun action(icon: Int, title: String, action: String, request: Int): Notification.Action {
        val intent = Intent(this, CountdownTimerService::class.java).setAction(action)
        val pending = PendingIntent.getForegroundService(
            this,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pending).build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                ONGOING_CHANNEL,
                getString(R.string.countdown_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.countdown_notification_channel_desc)
                setShowBadge(false)
            },
        )
        notificationManager?.createNotificationChannel(
            NotificationChannel(
                COMPLETION_CHANNEL,
                getString(R.string.countdown_complete_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.countdown_complete_channel_desc)
                enableVibration(true)
            },
        )
    }

    // The lock belongs to one visible timer session and every pause, finish,
    // reset and service-destruction path releases it explicitly.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java)
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        ticker?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ONGOING_CHANNEL = "countdown_ongoing"
        private const val COMPLETION_CHANNEL = "countdown_completion"
        private const val ONGOING_ID = 8501
        private const val COMPLETION_ID = 8502
        private const val REQUEST_TOGGLE = 8503
        private const val REQUEST_ADD = 8504
        private const val REQUEST_OPEN = 8506
        private const val WAKE_LOCK_TAG = "cangshuo:countdown-timer"
        private const val ACTION_START = "com.cangshuo.toolbox.timer.START"
        private const val ACTION_PAUSE = "com.cangshuo.toolbox.timer.PAUSE"
        private const val ACTION_RESUME = "com.cangshuo.toolbox.timer.RESUME"
        private const val ACTION_ADD_MINUTE = "com.cangshuo.toolbox.timer.ADD_MINUTE"
        fun start(context: Context) = sendForeground(context, ACTION_START)
        fun stop(context: Context) {
            context.stopService(Intent(context, CountdownTimerService::class.java))
        }

        private fun sendForeground(context: Context, action: String) {
            context.startForegroundService(Intent(context, CountdownTimerService::class.java).setAction(action))
        }
    }
}
