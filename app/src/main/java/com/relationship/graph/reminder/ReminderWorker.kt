package com.relationship.graph.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import com.relationship.graph.data.calendar.LunarDates
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.preferences.ReminderPreferencesStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.first
import android.content.Intent
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.relationship.graph.MainActivity
import com.relationship.graph.RelationshipApplication
import com.relationship.graph.R
import kotlin.math.abs

/** 每日检查当天生日/忌日并发本地通知；数据全部来自本地数据库，不联网。 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RelationshipApplication ?: return Result.success()
        if (!ReminderPreferencesStore(applicationContext).enabled.first()) {
            return Result.success()
        }
        val manager = NotificationManagerCompat.from(applicationContext)
        // API 33+ 未授予 POST_NOTIFICATIONS 时 areNotificationsEnabled() 也会返回 false，
        // 这里再显式校验一次权限，避免误用未授权 API。
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val people = app.container.repository.people.first()
        val today = LocalDate.now()
        people.forEach { person ->
            val reminder = todayReminder(person, today) ?: return@forEach
            notify(manager, person, reminder)
        }
        return Result.success()
    }

    private sealed class Reminder {
        data class Birthday(val age: Int?) : Reminder()
        data class DeathAnniversary(val years: Int?) : Reminder()
    }

    private fun todayReminder(person: PersonEntity, today: LocalDate): Reminder? {
        return if (person.deceased) {
            val matches = if (person.usesLunarDeathDay) {
                lunarMatchesToday(
                    person.lunarDeathMonth ?: return null,
                    person.lunarDeathDay ?: return null,
                    person.isLeapDeathMonth == true,
                    today,
                )
            } else {
                solarMatchesToday(person.deathDate, today)
            }
            if (!matches) return null
            val years = person.deathDate
                ?.takeIf(String::isNotBlank)
                ?.runCatching { LocalDate.parse(this).year }
                ?.getOrNull()
                ?.let { today.year - it }
            Reminder.DeathAnniversary(years)
        } else {
            val matches = if (person.usesLunarBirthday) {
                lunarMatchesToday(
                    person.lunarMonth ?: return null,
                    person.lunarDay ?: return null,
                    person.isLeapMonth == true,
                    today,
                )
            } else {
                solarMatchesToday(person.birthday, today)
            }
            if (!matches) return null
            val age = person.birthday
                ?.takeIf(String::isNotBlank)
                ?.runCatching { LocalDate.parse(this).year }
                ?.getOrNull()
                ?.let { today.year - it }
            Reminder.Birthday(age)
        }
    }

    private fun solarMatchesToday(isoDate: String?, today: LocalDate): Boolean {
        val date = isoDate
            ?.takeIf(String::isNotBlank)
            ?.runCatching { LocalDate.parse(this) }
            ?.getOrNull()
            ?: return false
        return date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth
    }

    /**
     * 农历命中规则：月日相同且闰月标记一致；若本人过的是闰月生日而今年没有该闰月，
     * 则退回按平月提醒（民间通行做法）。
     */
    private fun lunarMatchesToday(
        lunarMonth: Int,
        lunarDay: Int,
        isLeapMonth: Boolean,
        today: LocalDate,
    ): Boolean {
        val lunar = runCatching {
            Solar.fromYmd(today.year, today.monthValue, today.dayOfMonth).lunar
        }.getOrNull() ?: return false
        val todayMonth = abs(lunar.month)
        val todayIsLeap = lunar.month < 0
        if (lunarDay != lunar.day) return false
        if (todayMonth != lunarMonth) return false
        if (todayIsLeap == isLeapMonth) return true
        if (isLeapMonth && !todayIsLeap) {
            // 本人过闰月生日，今年没有对应闰月时退回平月提醒。
            val leapMonthThisYear = runCatching {
                LunarYear.fromYear(lunar.year).leapMonth
            }.getOrDefault(0)
            return leapMonthThisYear != lunarMonth
        }
        return false
    }

    // 通知权限与开关均在 doWork 中校验过，此处直接发送。
    @SuppressLint("MissingPermission")
    private fun notify(manager: NotificationManagerCompat, person: PersonEntity, reminder: Reminder) {
        val title: String
        val text: String
        when (reminder) {
            is Reminder.Birthday -> {
                title = "今天是 ${person.name} 的生日"
                text = reminder.age?.let { "祝 ${person.name} $it 岁生日快乐" } ?: "记得送上祝福"
            }
            is Reminder.DeathAnniversary -> {
                title = "今天是 ${person.name} 的忌日"
                text = reminder.years?.let { "${person.name} 逝世 $it 周年" } ?: "缅怀 ${person.name}"
            }
        }
        val intent = Intent(applicationContext, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        val pending = PendingIntent.getActivity(
            applicationContext,
            person.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, BirthdayReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(person.id.hashCode(), notification) }
    }
}
