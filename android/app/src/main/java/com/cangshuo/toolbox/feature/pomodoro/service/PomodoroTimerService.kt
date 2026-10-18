package com.cangshuo.toolbox.feature.pomodoro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import com.cangshuo.toolbox.MainActivity
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.ToolboxApplication
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPhase
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroPolicy
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroSession
import com.cangshuo.toolbox.feature.pomodoro.domain.PomodoroTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PomodoroTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private val repository get() = (application as ToolboxApplication).container.pomodoroRepository
    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ONGOING_NOTIFICATION_ID, buildOngoingNotification(repository.observeTimer().value))
        when (intent?.action) {
            ACTION_TOGGLE -> scope.launch {
                if (repository.observeTimer().value.running) repository.pause() else repository.start()
                ensureTicker()
            }
            ACTION_SKIP -> scope.launch {
                repository.skip()
                ensureTicker()
            }
            ACTION_START, ACTION_SYNC, null -> ensureTicker()
        }
        return START_STICKY
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val advance = repository.tick()
                advance.records.forEach(::postCompletionNotification)
                if (!advance.state.running) break
                notificationManager.notify(
                    ONGOING_NOTIFICATION_ID,
                    buildOngoingNotification(advance.state),
                )
                delay(PomodoroPolicy.TICK_MILLIS)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildOngoingNotification(state: PomodoroTimerState): Notification {
        val phase = phaseTitle(state.phase)
        val remaining = PomodoroPolicy.formatClock(state.remainingAt(System.currentTimeMillis()))
        val text = if (state.label.isBlank()) remaining else remaining + " · " + state.label
        val toggleTitle = if (state.running) getString(R.string.pomodoro_pause)
        else getString(R.string.pomodoro_resume)
        return Notification.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_pomodoro_notification)
            .setContentTitle(phase)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_pause, toggleTitle, ACTION_TOGGLE, 11))
            .addAction(action(android.R.drawable.ic_media_next, getString(R.string.pomodoro_skip), ACTION_SKIP, 12))
            .build()
    }

    private fun postCompletionNotification(session: PomodoroSession) {
        if (session.phase != PomodoroPhase.FOCUS && !session.completed) return
        val title = when (session.phase) {
            PomodoroPhase.FOCUS -> getString(R.string.pomodoro_focus_complete)
            PomodoroPhase.SHORT_BREAK -> getString(R.string.pomodoro_short_break_complete)
            PomodoroPhase.LONG_BREAK -> getString(R.string.pomodoro_long_break_complete)
        }
        val current = repository.observeTimer().value
        val next = when (current.phase) {
            PomodoroPhase.FOCUS -> getString(R.string.pomodoro_focus_next)
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> getString(R.string.pomodoro_break_next)
        }
        notificationManager.notify(
            COMPLETION_NOTIFICATION_ID,
            Notification.Builder(this, COMPLETION_CHANNEL)
                .setSmallIcon(R.drawable.ic_pomodoro_notification)
                .setContentTitle(title)
                .setContentText(next)
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build(),
        )
    }

    private fun action(icon: Int, title: String, action: String, requestCode: Int): Notification.Action {
        val intent = Intent(this, PomodoroTimerService::class.java).setAction(action)
        val pending = PendingIntent.getService(
            this,
            requestCode,
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
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun phaseTitle(phase: PomodoroPhase): String = when (phase) {
        PomodoroPhase.FOCUS -> getString(R.string.pomodoro_focus)
        PomodoroPhase.SHORT_BREAK -> getString(R.string.pomodoro_short_break)
        PomodoroPhase.LONG_BREAK -> getString(R.string.pomodoro_long_break)
    }

    private fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CHANNEL,
                getString(R.string.pomodoro_notification_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.pomodoro_notification_title_desc)
                setShowBadge(false)
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                COMPLETION_CHANNEL,
                getString(R.string.pomodoro_notification_complete_title),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.pomodoro_notification_complete_desc)
            },
        )
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ONGOING_CHANNEL = "pomodoro_ongoing"
        private const val COMPLETION_CHANNEL = "pomodoro_completion"
        private const val ONGOING_NOTIFICATION_ID = 7401
        private const val COMPLETION_NOTIFICATION_ID = 7402
        private const val ACTION_START = "com.cangshuo.toolbox.pomodoro.START"
        private const val ACTION_SYNC = "com.cangshuo.toolbox.pomodoro.SYNC"
        private const val ACTION_TOGGLE = "com.cangshuo.toolbox.pomodoro.TOGGLE"
        private const val ACTION_SKIP = "com.cangshuo.toolbox.pomodoro.SKIP"

        fun start(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }
    }
}
