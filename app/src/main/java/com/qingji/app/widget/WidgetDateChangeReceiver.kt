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
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 时区变化后重算业务时区；日期变化沿用已恢复的 zone。
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    com.qingji.app.domain.BusinessClock.restoreBlocking(context.applicationContext)
                }
                // 强制使日期口径失效：快照 dataDate 与 AppClock.today() 不符会在读取时作废。
                WidgetSettingsCache.invalidate()
                WidgetRefresher.refresh(context.applicationContext)
            }
            Intent.ACTION_BOOT_COMPLETED,
            android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                com.qingji.app.domain.BusinessClock.restoreBlocking(context.applicationContext)
                WidgetRefresher.refresh(context.applicationContext)
            }
        }
    }
}
