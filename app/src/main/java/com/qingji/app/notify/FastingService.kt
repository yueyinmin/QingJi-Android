package com.qingji.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FastingRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/**
 * ============================================================
 * 断食后台计时（断食四项之一：常驻通知）
 * ------------------------------------------------------------
 * 为什么之前刻意不做后台服务、现在又要做：
 *   断食「状态」仍然完全由当前时间推出来（FastingRules.status 是纯函数），
 *   这个服务只负责一件事 —— 把状态挂到通知栏上，并让 Chronometer 自己走秒。
 *   所以它不是「计时器真相来源」，杀掉它也不会算错时间；
 *   下次打开 App 重新算一遍即可，永远不会出现「服务没跑所以断食时长错了」。
 *
 * 三个设计点：
 *   1. 每 15 分钟才重算一次文案（时段切换、剩余时间），
 *      秒数交给系统 Chronometer，不唤醒 CPU；
 *   2. 用户在前台打开/关闭开关时才启动，避免 Android 12+ 后台启动
 *      前台服务被系统拒绝；开机广播里允许再启动一次；
 *   3. 任何启动失败都吞掉异常 —— 通知挂不上是小事，
 *      不能因此让 App 崩掉（宁可没有通知，也不能闪退）。
 * ============================================================
 */
class FastingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var alive = false

    private val refresher = object : Runnable {
        override fun run() {
            if (!alive) return
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        FastingNotifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val settings = readSettings()

        // 硬性要求：用 startForegroundService 拉起来之后必须在 5 秒内调用 startForeground，
        // 否则系统抛 RemoteServiceException 直接把整个进程干掉 ——
        // 哪怕这个服务下一秒就该停，也必须先 startForeground 再 stopForeground。
        val notification = if (settings != null) {
            buildNotification(settings)
        } else {
            FastingNotifier.build(this, "轻断食", "正在准备断食计时", System.currentTimeMillis(), false)
        }
        val started = try {
            ServiceCompat.startForeground(
                this,
                FastingNotifier.NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
            true
        } catch (e: Exception) {
            // 通知权限被关、厂商 ROM 限制后台服务……安静退出，不抛给用户
            false
        }
        if (!started) {
            shutdown()
            return START_NOT_STICKY
        }

        // 开关关了就没什么可挂的了，正常收工
        if (settings == null || !settings.fastingOngoingNotification) {
            shutdown()
            return START_NOT_STICKY
        }

        handler.removeCallbacks(refresher)
        handler.postDelayed(refresher, REFRESH_INTERVAL_MS)
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(refresher)
        super.onDestroy()
    }

    private fun readSettings(): QingJiSettings? = try {
        runBlocking { SettingsStore.observe(this@FastingService).first() }
    } catch (e: Exception) {
        null
    }

    /** 重算并刷新同一条通知；状态没变就是原地改字，不会刷屏打扰。 */
    private fun refresh() {
        val settings = readSettings() ?: return
        if (!settings.fastingOngoingNotification) {
            shutdown()
            return
        }
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(FastingNotifier.NOTIFICATION_ID, buildNotification(settings))
        } catch (e: Exception) {
            // 刷不动就等下一次，别让服务崩
        }
    }

    private fun buildNotification(settings: QingJiSettings): Notification {
        val now = System.currentTimeMillis()

        // ① 自由断食优先：没有方案，只有开始时刻
        if (settings.fastingFreeStart > 0L) {
            val minutes = ((now - settings.fastingFreeStart) / 60_000L).toInt().coerceAtLeast(0)
            val phase = FastingRules.phase(minutes / 60.0).first
            return FastingNotifier.build(
                this,
                "自由断食 · $phase",
                "点开可以结束并记录",
                settings.fastingFreeStart,
                chronometer = true
            )
        }

        // ② 方案断食：状态由时间推出来
        val days = settings.fastingDays.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        val isFastingDay = AppClock.today().dayOfWeek.value in days
        val status = FastingRules.status(
            now = LocalTime.now(),
            planHours = settings.fastingPlanHours,
            eatStartMinute = settings.fastingEatStartMinute,
            eatEndMinute = settings.fastingEatEndMinute,
            enabled = settings.fastingEnabled && isFastingDay
        )
        return when {
            !status.enabled -> FastingNotifier.build(
                this, "轻断食未开启", "点开可以选方案，或直接开始自由断食", now, false
            )
            status.active -> {
                // 计时起点 = 现在 - 已断食分钟数，Chronometer 从那一刻开始走
                val startAt = now - status.elapsedMinutes * 60_000L
                FastingNotifier.build(
                    this,
                    "断食中 · ${FastingRules.planLabel(status.planHours)}",
                    "还差 ${FastingRules.durationLabel(status.remainMinutes)} 到进食时间 · 点开看详情",
                    startAt,
                    chronometer = true
                )
            }
            else -> FastingNotifier.build(
                this,
                "今天进食窗口",
                "${FastingRules.minuteLabel(status.eatStartMinute)} - " +
                    "${FastingRules.minuteLabel(status.eatEndMinute)} · 之后进入断食",
                now,
                chronometer = false
            )
        }
    }

    private fun shutdown() {
        alive = false
        handler.removeCallbacks(refresher)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // ignore
        }
        stopSelf()
    }

    companion object {
        private const val ACTION_STOP = "com.qingji.app.action.FASTING_TIMER_STOP"
        private const val REFRESH_INTERVAL_MS = 15 * 60_000L

        /** 设置里开关改了、或 App 回到前台时调：按当前设置启动或停掉。 */
        fun sync(context: Context) {
            val settings = try {
                runBlocking { SettingsStore.observe(context).first() }
            } catch (e: Exception) {
                return
            }
            if (settings.fastingOngoingNotification) start(context) else stop(context)
        }

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, FastingService::class.java))
            } catch (e: Exception) {
                // Android 12+ 后台启动被拒：等下次进前台再试
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, FastingService::class.java))
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
