package com.relationship.graph.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 生日/忌日提醒的调度与通知渠道管理。 */
object BirthdayReminderScheduler {
    const val CHANNEL_ID = "birthday_reminders"
    private const val PERIODIC_WORK_NAME = "birthday_reminder_daily"
    private const val ONESHOT_WORK_NAME = "birthday_reminder_now"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "生日与忌日提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "家人生日和忌日的本地提醒，不联网" },
        )
    }

    /** 应用启动时调用：每天上午 9 点前后跑一次；已注册则保留（KEEP，避免反复推迟）。 */
    fun ensureDailyWork(context: Context) {
        val delay = Duration.between(LocalTime.now(), nextOccurrence(9, 0))
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 生日资料变化后调用：尽快补跑一次，保证当天改动当天生效。 */
    fun requestReschedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONESHOT_WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun nextOccurrence(hour: Int, minute: Int): LocalTime {
        val today = LocalTime.of(hour, minute)
        return if (LocalTime.now().isBefore(today)) today else today.plusHours(24)
    }
}
