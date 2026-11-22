package com.qingji.app.domain

/**
 * 数字密码失败限制（纯逻辑，内存态；不持久化，进程重启即清零）。
 * 连续 MAX_ATTEMPTS 次失败后进入 COOLDOWN_MS 冷却；冷却期间即使密码正确也拒绝。
 */
class DiaryPasscodeGuard(
    private val maxAttempts: Int = 5,
    private val cooldownMs: Long = 30_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var failed = 0
    private var lockedUntil = 0L

    val remainingAttempts: Int get() = (maxAttempts - failed).coerceAtLeast(0)
    val isCoolingDown: Boolean get() = clock() < lockedUntil
    val cooldownRemainingMs: Long get() = (lockedUntil - clock()).coerceAtLeast(0L)

    /** 冷却中返回 false 且不消耗尝试次数；否则返回密码校验是否允许进行。 */
    fun canAttempt(): Boolean = !isCoolingDown

    fun onSuccess() { failed = 0; lockedUntil = 0L }

    fun onFailure() {
        failed++
        if (failed >= maxAttempts) {
            lockedUntil = clock() + cooldownMs
            failed = 0 // 冷却结束后重新计数
        }
    }

    fun reset() { failed = 0; lockedUntil = 0L }
}
