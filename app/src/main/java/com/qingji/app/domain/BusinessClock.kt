package com.qingji.app.domain

import android.content.Context
import com.qingji.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.TimeZone

/**
 * 业务时区的统一恢复入口（QJ-GOV-2026-01 / P1-05）。
 *
 * 问题：AppClock.zone 原来只在 MainActivity 的 Compose SideEffect 里恢复，
 * 后台入口（Receiver / Alarm / Service / Widget 独立进程）可能在 UI 之前甚至永远
 * 不经过 MainActivity，导致 today()/now() 用系统时区，日期归属错乱。
 *
 * DataStore 是异步的，Application.onCreate 不能假设同步可读；因此提供两种入口：
 * - [restore] 挂起版：协程上下文（Widget provideGlance / ViewModel）用；
 * - [restoreBlocking] 阻塞版：BroadcastReceiver.onReceive / Service.onStartCommand
 *   这类同步入口用（已在系统后台线程，runBlocking 读 DataStore 可接受）。
 *
 * 恢复是幂等的：AppClock.zone 已经是目标时区时不重复 setZone。
 */
object BusinessClock {

    suspend fun restore(context: Context) {
        val zone = SettingsStore.observe(context.applicationContext).first().timeZone
        if (zone.isBlank()) return
        val target = try {
            java.time.ZoneId.of(zone)
        } catch (e: Exception) {
            return // 非法时区配置：保持现状，不崩
        }
        if (AppClock.zone.id != target.id) AppClock.setZone(zone)
    }

    fun restoreBlocking(context: Context) {
        runBlocking { restore(context) }
    }

    /**
     * 按业务时区构造闹钟触发时刻的 Calendar（替代 Calendar.getInstance() 的系统时区）。
     * hour/minute 是用户在设置里选的「业务时区下的时刻」。
     */
    fun calendarForBusinessZone(context: Context, hour: Int, minute: Int): Calendar {
        restoreBlocking(context)
        val zone = AppClock.zone
        return Calendar.getInstance(TimeZone.getTimeZone(zone.id)).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
