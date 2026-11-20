package com.qingji.app.domain

import kotlin.math.round

/**
 * ============================================================
 * 份量快捷倍数（M3.1）
 * ------------------------------------------------------------
 * 需求要求 AI 结果里每一条都能一键 0.5x / 1x / 1.5x / 2x。
 * 关键：**永远以 AI 最初给的那一份为基准**缩放，不能拿当前值再乘，
 * 否则连点两次 2x 会变成 4x，营养值也越滚越偏。
 * 抽成纯函数方便单测。
 * ============================================================
 */
object PortionRules {

    val MULTIPLIERS = listOf(0.5, 1.0, 1.5, 2.0)

    fun label(multiplier: Double): String = when (multiplier) {
        0.5 -> "0.5x"
        1.0 -> "1x"
        1.5 -> "1.5x"
        2.0 -> "2x"
        else -> "${multiplier}x"
    }

    /** 按倍数缩放一个数值，保留 1 位小数，负数/异常输入兜底成 0。 */
    fun scaled(base: Double, multiplier: Double): Double {
        if (base < 0 || multiplier < 0) return 0.0
        return round(base * multiplier * 10) / 10.0
    }
}
