package com.qingji.app.widget

/**
 * 营养进度比例的纯计算（工单 C1.5 / E1.6），可在 JVM 单测覆盖所有边界，
 * Glance 与设置页预览共用，避免两处算法漂移。
 *
 * 规则：
 * - goal <= 0（目标缺失/非法）→ 0f，显示空轨道，不伪造进度；
 * - current <= 0 → 0f；
 * - current 超过 goal → 1f（钳制，不溢出轨道）；
 * - 非有限输入（NaN / Infinity）→ 0f。
 */
internal object WidgetProgress {
    fun fraction(current: Int, goal: Int): Float {
        if (goal <= 0 || current <= 0) return 0f
        val ratio = current.toFloat() / goal.toFloat()
        if (!ratio.isFinite()) return 0f
        return ratio.coerceIn(0f, 1f)
    }
}
