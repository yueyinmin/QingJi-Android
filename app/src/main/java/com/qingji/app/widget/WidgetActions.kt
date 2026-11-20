package com.qingji.app.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock

/**
 * ============================================================
 * 小组件的公共动作
 * ------------------------------------------------------------
 * 1. TARGET_KEY：点小组件按钮时，把「要去哪个页面」塞进 Intent extra，
 *    MainActivity 读出来直接导航（key 字面值 qingji_target 未变）。
 * 2. AddWaterAction：点「喝水」不打开 App，直接往 Room 写 +200ml。
 *
 * 阶段 0：WidgetRefresher / TARGET_* / WidgetTarget 已拆到独立文件，
 *   本文件只留 Action，调用方无需改动（同包）。
 * 阶段 2：防抖状态从实例字段改为【进程级 @Volatile】——Glance 每次点击可能
 *   重建 ActionCallback 实例，实例字段防抖会失效；进程级字段跨实例仍有效。
 *   防误触合并：600ms 内连点只记一次。去掉原来的 refreshNow，避免与
 *   QingJiRepository.addWater 内部的 refresh(OVERVIEW) 形成双重刷新。
 * ============================================================
 */

/** ActionParameters 的 name 会原样变成 Intent extra 的 key。 */
val TARGET_KEY = ActionParameters.Key<String>("qingji_target")

/** 喝水防误触合并的纯判定（可单测）：距上次不足窗口则拒绝，首次（last=0）放行。 */
internal object WaterDebounce {
    fun shouldAllow(now: Long, last: Long, windowMs: Long): Boolean =
        last == 0L || now - last >= windowMs
}

/** 点「喝水」直接记账，不打开界面。 */
class AddWaterAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val now = SystemClock.elapsedRealtime()
        // 进程级防抖：同步比较+置位，杜绝两个协程/两次点击同时通过检查
        synchronized(LOCK) {
            if (!WaterDebounce.shouldAllow(now, lastClickAt, DEBOUNCE_MS)) return
            lastClickAt = now
        }
        try {
            // addWater 内部已写库并触发 WidgetRefresher.refresh(OVERVIEW)，
            // 这里不再 refreshNow，避免同一次点击双重刷新、也统一走 250ms 合并。
            QingJiRepository(context).addWater(200, AppClock.today())
        } catch (e: Exception) {
            // 写库失败就到此为止：桌面不刷新，用户看到的仍是旧数字，
            // 绝不出现「点了但没记上还以为记上了」。
            return
        }
    }

    private companion object {
        /** 防误触合并：600ms 内的连点视为同一次。 */
        const val DEBOUNCE_MS = 600L
        val LOCK = Any()

        /** 进程级时间戳（替代原实例字段，跨 ActionCallback 重建仍有效）。 */
        @Volatile
        var lastClickAt: Long = 0L
    }
}
