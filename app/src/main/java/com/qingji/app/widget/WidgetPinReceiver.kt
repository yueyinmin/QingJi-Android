package com.qingji.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 小组件「已成功添加到桌面」回调。
 *
 * requestPinAppWidget 的 successCallback 会在用户确认固定后发这个广播；
 * 收到即主动刷新全部小组件，用当前 APK 的代码/数据立即重绘，
 * 不再等 30 分钟系统周期——解决「删掉重加后看起来没变化」的体感。
 *
 * 用显式 component 发送，Receiver 不必 exported。
 */
class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetRefresher.refresh(context.applicationContext)
    }

    companion object {
        const val ACTION_PINNED = "com.qingji.app.widget.WIDGET_PINNED"

        fun pendingIntent(context: Context): android.app.PendingIntent {
            val intent = Intent(context, WidgetPinReceiver::class.java).setAction(ACTION_PINNED)
            return android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
