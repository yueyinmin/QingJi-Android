package com.qingji.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 小组件「已成功添加到桌面」回调。
 *
 * requestPinAppWidget 的 successCallback 会在用户确认固定后发这个广播；
 * 收到即主动刷新，用当前 APK 的代码/数据立即重绘，不再等 30 分钟系统周期。
 *
 * 工单 B3：pendingIntent 携带 EXTRA_PIN_TARGET —— 能可靠识别新增组件类型时
 * 只定向刷新该类；无法识别（系统/旧调用未带 extra）时保留安全的 ALL 刷新。
 * 用显式 component 发送，Receiver 不必 exported。
 */
class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetPerf.mark("pin_callback", detail = intent.getStringExtra(EXTRA_PIN_TARGET) ?: "ALL")
        val targetName = intent.getStringExtra(EXTRA_PIN_TARGET)
        val target = targetName?.let { name ->
            runCatching { WidgetTarget.valueOf(name) }.getOrNull()
        }
        if (target != null) {
            WidgetRefresher.refresh(context.applicationContext, setOf(target))
        } else {
            // 无法可靠识别组件身份 → 安全兜底，不猜测。
            WidgetRefresher.refresh(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_PINNED = "com.qingji.app.widget.WIDGET_PINNED"
        const val EXTRA_PIN_TARGET = "com.qingji.app.widget.PIN_TARGET"

        /** @param target 新增的组件类型；null = 无法识别，回调里走 ALL 安全兜底。 */
        fun pendingIntent(context: Context, target: WidgetTarget? = null): android.app.PendingIntent {
            val intent = Intent(context, WidgetPinReceiver::class.java).setAction(ACTION_PINNED)
            if (target != null) intent.putExtra(EXTRA_PIN_TARGET, target.name)
            // 不同 target 用不同 requestCode，避免 FLAG_UPDATE_CURRENT 互相覆盖 extra。
            // V3：固定稳定映射，不绑定枚举顺序
            val requestCode = when (target) {
                com.qingji.app.widget.WidgetTarget.QUICK -> 1
                com.qingji.app.widget.WidgetTarget.OVERVIEW -> 2
                com.qingji.app.widget.WidgetTarget.WEIGHT -> 3
                null -> 0
            }
            return android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
