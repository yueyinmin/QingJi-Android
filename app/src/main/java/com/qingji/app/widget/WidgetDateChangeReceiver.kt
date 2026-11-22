package com.qingji.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingji.app.domain.AppClock

/**
 * 阶段B：跨日/时间/时区变化时主动刷新两个投影（工单 B8）。
 *
 * 只监听系统已广播的事件，不引入高频 Alarm、不常驻后台。
 * 注册在 Manifest（ACTION_DATE_CHANGED / TIME_SET / TIMEZONE_CHANGED / BOOT_COMPLETED）。
 * 业务日期统一走 AppClock，系统时区变化时重新恢复记录时区。
 */
class WidgetDateChangeReceiver : BroadcastReceiver() {
    companion object {
        /** V2.1阶段D：同一 action 1 秒内重复广播只处理一次，避免多次入队。 */
        private const val DEDUP_MS = 1_000L
        private val lastHandled = mutableMapOf<String, Long>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED,
                android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
            )
        ) return
        val now = System.currentTimeMillis()
        synchronized(lastHandled) {
            val last = lastHandled[action] ?: 0L
            if (now - last < DEDUP_MS) return
            lastHandled[action] = now
        }
        val app = context.applicationContext
        // V2.1阶段D：任何生命周期事件都重新恢复业务时区，
        // 保证 App 未启动 / 进程被回收后跨日仍按记录时区算 today。
        com.qingji.app.domain.BusinessClock.restoreBlocking(app)
        // 使设置快照与日期口径失效：两个投影的 dataDate 与新 today 不符会在 load 时作废。
        WidgetSettingsCache.invalidate()
        // refresh(ALL) 内部 250ms 合并，两个目标一次批量刷新，不产生高频轮询。
        WidgetRefresher.refresh(app)
    }
}
