package com.qingji.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.qingji.app.MainActivity
import com.qingji.app.R
import com.qingji.app.widget.WidgetRefresher

/**
 * ============================================================
 * 断食常驻通知（断食四项之一）
 * ------------------------------------------------------------
 * 和 Reminders 那套「到点响一次」的提醒完全不同：
 *   · 这是**无声、不可划掉**的进行中通知，挂在通知栏当计时器；
 *   · 用 IMPORTANCE_LOW + 关声音震动，不打扰人；
 *   · 用 setUsesChronometer(true) 让系统自己走秒，
 *     不需要服务每秒醒来改文案，省电；
 *   · 点一下直接进轻断食页。
 * ============================================================
 */
object FastingNotifier {

    /** 独立渠道：用户可以单独关掉断食计时，不影响其它提醒。 */
    const val CHANNEL_ID = "qingji_fasting_ongoing"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "断食计时",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "断食进行中的常驻计时，不响铃、不震动"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * @param startAtMillis 计时起点（吃进 setWhen，Chronometer 从这里开始走）
     * @param chronometer true = 显示成「00:12:34」这种自动走时的计时器
     */
    fun build(
        context: Context,
        title: String,
        text: String,
        startAtMillis: Long,
        chronometer: Boolean
    ): Notification {
        ensureChannel(context)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WidgetRefresher.EXTRA_TARGET, "fasting")
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(startAtMillis)
            .setUsesChronometer(chronometer)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(pi)
            .build()
    }
}
