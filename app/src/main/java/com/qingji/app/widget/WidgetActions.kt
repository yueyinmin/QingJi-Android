package com.qingji.app.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ============================================================
 * 小组件的公共动作
 * ------------------------------------------------------------
 * 1. TARGET_KEY：点小组件按钮时把「去哪个页面」塞进 Intent extra，
 *    MainActivity 读出来直接导航（key 字面值 qingji_target 未变）。
 * 2. AddWaterAction：点「喝水」直接往 Room 写 +200ml。
 *
 * W-04：显式 inFlight + lastSuccessAt 状态机（进程级，跨 ActionCallback 重建有效），
 * 写失败释放执行权且不消耗成功窗口 → 用户可立即重试；成功后 600ms 防误触合并。
 * 唯一刷新责任方仍是 QingJiRepository.addWater（内部 refresh OVERVIEW），Action 不重复刷新。
 * ============================================================
 */

/** ActionParameters 的 name 会原样变成 Intent extra 的 key。 */
val TARGET_KEY = ActionParameters.Key<String>("qingji_target")

/**
 * 喝水防误触状态机（W-04，纯逻辑可单测）。
 * @param inFlight 是否正在执行一次写入
 * @param lastSuccessAt 最近一次【成功】写入时间（elapsedRealtime）
 */
internal data class WaterGuardState(
    val inFlight: Boolean = false,
    val lastSuccessAt: Long = 0L
)

internal object WaterGuard {
    const val WINDOW_MS = 600L

    /** 是否可以发起一次写入：正在写入 → 拒绝；距上次成功不足窗口 → 拒绝。 */
    fun canStart(state: WaterGuardState, now: Long): Boolean =
        !state.inFlight && (state.lastSuccessAt == 0L || now - state.lastSuccessAt >= WINDOW_MS)

    /** 占用执行权（canStart 通过后调用）。 */
    fun onStart(state: WaterGuardState): WaterGuardState = state.copy(inFlight = true)

    /** 写成功：释放执行权 + 推进成功时间（开始 600ms 窗口）。 */
    fun onSuccess(now: Long): WaterGuardState = WaterGuardState(false, now)

    /** 写失败：释放执行权，成功时间不变 → 用户可立即重试。 */
    fun onFailure(state: WaterGuardState): WaterGuardState =
        state.copy(inFlight = false)
}

/** 点「喝水」直接记账，不打开界面。 */
class AddWaterAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Mutex 保证「检查→占用→写库→释放」原子串行：并发点击不会同时通过检查。
        STATE_MUTEX.withLock {
            val now = SystemClock.elapsedRealtime()
            if (!WaterGuard.canStart(STATE, now)) return@withLock
            STATE = WaterGuard.onStart(STATE)
            val succeeded = try {
                // addWater 内部已写库并 refresh(OVERVIEW)，这里不再 refreshNow，避免双重刷新。
                QingJiRepository(context).addWater(200, AppClock.today())
                true
            } catch (e: Exception) {
                // 写失败：不刷新、不假装成功
                false
            }
            STATE = if (succeeded) WaterGuard.onSuccess(now) else WaterGuard.onFailure(STATE)
        }
    }

    private companion object {
        val STATE_MUTEX = Mutex()
        /** 进程级状态（跨 ActionCallback 重建有效），仅在上述 Mutex 内读写。 */
        var STATE = WaterGuardState()
    }
}
