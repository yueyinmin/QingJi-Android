package com.qingji.app.domain.input

/**
 * ============================================================
 * 训练输入的「界面单位 ↔ 数据单位」换算（QJ-RECORD-INPUT-UNIFIED-V3 / B2）
 * ------------------------------------------------------------
 * 修复的两个真实数据损坏缺陷：
 * · 时长：数据库 durationSec 存**秒**，界面填**分钟**。旧代码直接把秒
 *   塞进分钟输入框（1800 秒显示成 1800 分钟），用户一动键盘就被
 *   coerceIn(0,600) 截成 600 分钟 —— 静默把 30 分钟写成 10 小时。
 * · 距离：数据库 distanceMeters 存**米**，界面填**公里**。旧代码把米
 *   当公里显示（5000 米显示 5000 公里），一动就超 500 上限，距离被丢掉。
 *
 * 口径写死在这里，界面和测试都只从这里取，避免两处各写一份换算。
 * 注意：这里只做单位换算与上限约束，**不碰** MET、热量公式、
 * 会话总时长与单项时长的独立关系。
 * ============================================================
 */
object TrainingInputRules {

    /** 单项实际时长上限（分钟），与旧界面 coerceIn(0, 600) 保持一致。 */
    const val MAX_MINUTES = 600

    /** 公里输入上限，沿用旧业务约束（500 公里），不为了修单位而放宽。 */
    const val MAX_KM = 500.0

    /** 步数上限：只是挡误填（20 万步 ≈ 马拉松级），不是业务统计口径。 */
    const val MAX_STEPS = 200_000.0

    /** HIIT 单轮工作/休息秒数上限，沿用旧 coerceIn(0, 3600)。 */
    const val MAX_ROUND_SEC = 3600.0

    /** HIIT 轮数上限，沿用旧 coerceIn(0, 100)。 */
    const val MAX_ROUNDS = 100.0

    /** 秒 → 分钟（用于回填输入框）。 */
    fun secondsToMinutes(seconds: Long): Double = seconds / 60.0

    /** 分钟 → 秒（用于保存）。非整数分钟按整数分钟处理，和旧保存口径一致。 */
    fun minutesToSeconds(minutes: Double): Long =
        minutes.toLong().coerceIn(0L, MAX_MINUTES.toLong()) * 60L

    /** 秒 → 输入框文本（1800 → "30"；1850 → "31"，取整只影响显示，原值由调用方保留）。 */
    fun secondsToMinutesText(seconds: Long): String =
        NumericInputState.formatValue(secondsToMinutes(seconds), 0)

    /** 米 → 公里（用于回填输入框）。 */
    fun metersToKm(meters: Double?): Double = (meters ?: 0.0) / 1000.0

    /**
     * 米 → 输入框文本：最多 3 位小数、去掉无意义的尾零。
     * 5000 → "5"，5123 → "5.123"，不出现 "5000.0" 这种别扭显示。
     */
    fun metersToKmText(meters: Double?): String =
        NumericInputState.formatValue(metersToKm(meters), 3)

    /**
     * 公里 → 米（用于保存）。<= 0 视为「没填」，返回 null；
     * 超过 [MAX_KM] 返回 null（旧逻辑就是丢弃超限距离，这里不改业务口径）。
     */
    fun kmToMeters(km: Double?): Double? {
        val v = km ?: return null
        if (v <= 0.0 || v > MAX_KM) return null
        return v * 1000.0
    }
}
