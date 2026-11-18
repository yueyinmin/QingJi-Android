package com.qingji.app.domain

/**
 * ============================================================
 * v1.4 健康体系：组间休息倒计时状态（纯逻辑，可单测）
 * ------------------------------------------------------------
 * 时间戳驱动：只存「倒计时到哪一刻结束」，页面重组、每秒刷新都不丢状态；
 * 暂停时把剩余毫秒冻结下来，恢复时从「当下 + 剩余」重新算结束时间。
 * 放在 domain 层是为了不依赖 Android 环境就能跑单测。
 * ============================================================
 */
data class RestTimerState(
    /** 0 = 没在计时；否则是倒计时结束那一刻的 epoch 毫秒 */
    val endsAt: Long = 0L,
    /** 本次休息总时长（毫秒），用于显示进度 */
    val totalMs: Long = 0L,
    /** true = 暂停中；pausedRemainingMs 是按下暂停那一刻的剩余毫秒 */
    val paused: Boolean = false,
    val pausedRemainingMs: Long = 0L
) {
    /** 剩余秒数（向上取整，显示层用）。没在计时返回 0。 */
    fun remainingSeconds(now: Long): Long {
        val remainingMs = if (paused) pausedRemainingMs else (endsAt - now).coerceAtLeast(0L)
        return if (totalMs <= 0L) 0L else (remainingMs + 999L) / 1000L
    }

    /** 倒计时是否已走完（还在展示状态里，等用户跳过或再开）。 */
    fun isFinished(now: Long): Boolean =
        totalMs > 0L && !paused && endsAt in 1..now

    /** 暂停：冻结当前剩余毫秒。没在计时或已暂停返回原状态。 */
    fun pausedAt(now: Long): RestTimerState {
        if (totalMs <= 0L || paused) return this
        return copy(
            paused = true,
            pausedRemainingMs = (endsAt - now).coerceAtLeast(0L)
        )
    }

    /** 继续：从「当下 + 冻结的剩余」重新算结束时间。 */
    fun resumedAt(now: Long): RestTimerState {
        if (totalMs <= 0L || !paused) return this
        return copy(paused = false, endsAt = now + pausedRemainingMs)
    }
}
