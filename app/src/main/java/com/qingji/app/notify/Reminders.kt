package com.qingji.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.qingji.app.MainActivity
import com.qingji.app.R
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * ============================================================
 * 提醒（M7「提醒」三组开关）+ 通知
 * ------------------------------------------------------------
 * 为什么用系统闹钟（AlarmManager）而不是引第三方后台任务库：
 * 这个 App 只需要「每天某个点响一声」，系统自带的能力完全够用，
 * 少一个依赖就少一处编译不过、少 1MB 包体积。
 *
 * 三个提醒：
 * - weigh   称重提醒：每周固定星期几 + 几点
 * - fasting 断食提醒：每天固定时间（开始/结束用同一时刻先占位）
 * - kcal    热量提醒：每天固定时间，如果当天一条饮食都没记才提醒
 *
 * 注意：安卓为了省电会做「非精确」投递，闹钟可能晚几分钟，
 * 这对提醒类功能完全可接受（也免得被杀后台）。
 * ============================================================
 */
object Reminders {

    const val TYPE_WEIGH = "weigh"
    const val TYPE_FASTING = "fasting"
    const val TYPE_KCAL = "kcal"

    private const val CHANNEL_ID = "qingji_reminder"
    const val EXTRA_TYPE = "reminder_type"

    /** 通知渠道：安卓 8.0 以上必须先建渠道，通知才显示得出来。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "轻记提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "称重、断食、记饮食的到点提醒"
        }
        manager.createNotificationChannel(channel)
    }

    private fun requestCode(type: String): Int = when (type) {
        TYPE_WEIGH -> 1001
        TYPE_FASTING -> 1002
        else -> 1003
    }

    private fun alarmIntent(context: Context, type: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.qingji.app.reminder.$type"
            putExtra(EXTRA_TYPE, type)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(type),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 每天 hour:minute 响一次（首次如果今天这个点过了，就顺延到明天）。 */
    fun scheduleDaily(context: Context, type: String, hour: Int, minute: Int = 0) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger,
                AlarmManager.INTERVAL_DAY,
                alarmIntent(context, type)
            )
        } catch (e: SecurityException) {
            // 个别 ROM 不给建闹钟权限；这里吞掉，不影响 App 其它功能
        }
    }

    fun cancel(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(context, type)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    /** 设置一改就整体重排一次：开着的重设，关掉的取消。最简单也最不容易错。 */
    fun apply(context: Context, settings: QingJiSettings) {
        // v3.3.1：滚轮可选小时 + 分钟，这里把分钟一起交给系统闹钟
        if (settings.weighReminder) {
            scheduleDaily(context, TYPE_WEIGH, settings.reminderHour, settings.reminderMinute)
        } else {
            cancel(context, TYPE_WEIGH)
        }
        if (settings.fastingReminder) {
            scheduleDaily(context, TYPE_FASTING, settings.fastStartHour, settings.fastStartMinute)
        } else {
            cancel(context, TYPE_FASTING)
        }
        if (settings.kcalReminder) {
            scheduleDaily(context, TYPE_KCAL, settings.kcalReminderHour, settings.kcalReminderMinute)
        } else {
            cancel(context, TYPE_KCAL)
        }
    }
}

/** 闹钟到点 → 发通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(Reminders.EXTRA_TYPE) ?: return
        var settings = QingJiSettings()
        try {
            settings = runBlocking { SettingsStore.observe(context).first() }
        } catch (e: Exception) {
            // 读设置失败就用默认值，至少别崩
        }

        // 称重提醒是「每周固定星期几」：不是今天就直接返回，不打扰
        if (type == Reminders.TYPE_WEIGH) {
            val today = AppClock.today().dayOfWeek.value
            if (today != settings.weighWeekday) return
        }

        // 热量提醒：今天已经有饮食记录就不提醒了
        if (type == Reminders.TYPE_KCAL) {
            val hasLog = try {
                runBlocking {
                    com.qingji.app.data.repository.QingJiRepository(context)
                        .hasFoodLogToday()
                }
            } catch (e: Exception) {
                true // 查不到就当已记过，宁可不提醒，也不乱提醒
            }
            if (hasLog) return
        }

        showNotification(context, type)
    }

    private fun showNotification(context: Context, type: String) {
        Reminders.ensureChannel(context)
        val (title, body) = when (type) {
            Reminders.TYPE_WEIGH -> "该称体重了" to "花 30 秒记一下今天的体重，趋势才有意义"
            Reminders.TYPE_FASTING -> "断食时间到了" to "打开轻记看一眼今天的进度"
            else -> "今天还没记饮食" to "点开记一笔，一句话就能记完"
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            type.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "qingji_reminder")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(type.hashCode(), notification)
        } catch (e: SecurityException) {
            // 安卓 13 以上没给通知权限，静默失败即可（设置页里会引导开启）
        }
    }
}

/** 手机重启后闹钟会全丢，这里重新排一遍。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val settings = runBlocking { SettingsStore.observe(context).first() }
            Reminders.apply(context, settings)
        } catch (e: Exception) {
            // 重启后读不到设置就算了，下次打开 App 会再排一次
        }
    }
}
