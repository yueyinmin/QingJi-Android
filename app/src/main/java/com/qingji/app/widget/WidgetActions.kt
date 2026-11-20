package com.qingji.app.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.sync.withLock

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

    /**
     * 写库结果出来后的新时间戳：成功→now（开始 600ms 窗口）；
     * 失败→清零（清除窗口，用户可立即重试，不必等上次成功后的 600ms）。
     * 纯函数便于单测。
     */
    fun nextAfterWrite(success: Boolean, previous: Long, now: Long): Long =
        if (success) now else 0L
}

/** 点「喝水」直接记账，不打开界面。 */
class AddWaterAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // 整个「检查→写库→置位」都放在 Mutex 里串行：
        // ① 同一操作的重复投递（并发/快速连点）只落库一次；
        // ② 只在写成功后置位，写失败回滚 —— 用户可立即重试（审查点2）；
        // ③ 不依赖 ActionCallback 实例字段（进程级），跨回调重建仍有效。
        DEBOUNCE_MUTEX.withLock {
            val now = SystemClock.elapsedRealtime()
            val previous = lastClickAt
            if (!WaterDebounce.shouldAllow(now, previous, DEBOUNCE_MS)) return@withLock
            try {
                // addWater 内部已写库并触发 WidgetRefresher.refresh(OVERVIEW)，
                // 这里不再 refreshNow，避免同一次点击双重刷新、也统一走 250ms 合并。
                QingJiRepository(context).addWater(200, AppClock.today())
                lastClickAt = WaterDebounce.nextAfterWrite(true, previous, now)
            } catch (e: Exception) {
                // 写库失败：时间戳保持旧值，用户可立即重试；不刷新、不假装成功。
                lastClickAt = WaterDebounce.nextAfterWrite(false, previous, now)
                return@withLock
            }
        }
    }

    private companion object {
        /** 防误触合并：600ms 内的连点视为同一次。 */
        const val DEBOUNCE_MS = 600L
        val DEBOUNCE_MUTEX = kotlinx.coroutines.sync.Mutex()

        /** 进程级时间戳（替代原实例字段，跨 ActionCallback 重建仍有效）。仅在上述 Mutex 内读写。 */
        var lastClickAt: Long = 0L
    }
}
