package com.cangshuo.toolbox.feature.subscription.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionReminderScheduler
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AndroidSubscriptionReminderScheduler(context: Context) : SubscriptionReminderScheduler {
    private val appContext = context.applicationContext

    override fun setEnabled(enabled: Boolean) {
        SubscriptionReminderAlarmStore.setEnabled(appContext, enabled)
        if (enabled) SubscriptionReminderAlarmStore.scheduleNext(appContext)
        else SubscriptionReminderAlarmStore.cancel(appContext)
    }
}

internal object SubscriptionReminderAlarmStore {
    const val ACTION_CHECK = "com.cangshuo.toolbox.subscription.CHECK_REMINDERS"
    private const val PREFERENCES = "subscription_reminders_v1"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NEXT_TRIGGER = "next_trigger"
    private const val REQUEST_CODE = 8101

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun scheduleNext(context: Context) {
        if (!isEnabled(context)) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(context))
        preferences(context).edit { putLong(KEY_NEXT_TRIGGER, millis) }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        preferences(context).edit { remove(KEY_NEXT_TRIGGER) }
    }

    fun nextTrigger(context: Context): Long = preferences(context).getLong(KEY_NEXT_TRIGGER, 0L)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SubscriptionReminderReceiver::class.java).setAction(ACTION_CHECK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
