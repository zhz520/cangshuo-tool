package com.cangshuo.toolbox.feature.subscription.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.cangshuo.toolbox.MainActivity
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.database.ToolboxDatabaseProvider
import com.cangshuo.toolbox.feature.subscription.data.toDomain
import com.cangshuo.toolbox.feature.subscription.domain.SubscriptionPolicy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SubscriptionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SubscriptionReminderAlarmStore.ACTION_CHECK) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val subscriptions = ToolboxDatabaseProvider.get(context).subscriptionItemDao()
                    .getAll().map { it.toDomain() }
                val remindersEnabled = subscriptions.any { it.active && it.reminderEnabled }
                if (!remindersEnabled) {
                    SubscriptionReminderAlarmStore.setEnabled(context, false)
                    SubscriptionReminderAlarmStore.cancel(context)
                    return@launch
                }
                val today = LocalDate.now().toEpochDay()
                val due = subscriptions.filter { it.isReminderDue(today) }
                    .sortedWith(
                        compareBy(
                            { SubscriptionPolicy.effectiveNextPayment(it, today) },
                            { it.name },
                        ),
                    )
                if (due.isNotEmpty()) {
                    postReminder(
                        context,
                        due.map { it.name },
                        due.any { SubscriptionPolicy.effectiveNextPayment(it, today) < today },
                    )
                }
            } catch (error: Exception) {
                Log.w(TAG, "Subscription reminder check failed", error)
                // A failed check must not crash the process; the next daily alarm retries.
            } finally {
                if (SubscriptionReminderAlarmStore.isEnabled(context)) {
                    SubscriptionReminderAlarmStore.scheduleNext(context)
                }
                pending.finish()
            }
        }
    }

    private fun postReminder(context: Context, names: List<String>, hasOverdue: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.subscription_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.subscription_notification_channel_desc)
            },
        )
        val title = context.getString(
            if (hasOverdue) R.string.subscription_notification_overdue_title
            else R.string.subscription_notification_title,
        )
        val locale = context.resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val detail = names.take(3).joinToString(context.getString(R.string.subscription_notification_separator))
        val message = context.resources.getQuantityString(
            R.plurals.subscription_notification_message,
            names.size,
            names.size,
            detail,
        )
        val open = PendingIntent.getActivity(
            context,
            OPEN_REQUEST,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_subscription_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "subscription_reminders"
        const val NOTIFICATION_ID = 8100
        const val OPEN_REQUEST = 8102
        const val TAG = "SubscriptionReminder"
    }
}
