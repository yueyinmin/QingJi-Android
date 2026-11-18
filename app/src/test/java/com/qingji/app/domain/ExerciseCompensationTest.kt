package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 运动补偿口径单测：全 App 只允许这一处算补偿（首页额度卡、周小结共用）。 */
class ExerciseCompensationTest {

    @Test
    fun `开关关闭时补偿为 0 消耗统计照常保留`() {
        assertEquals(0, ExerciseCompensation.bonusKcal(300.0, enabled = false, pct = 50))
    }

    @Test
    fun `默认比例 50 按 300 千卡消耗加成 150`() {
        assertEquals(150, ExerciseCompensation.bonusKcal(300.0, enabled = true, pct = 50))
    }

    @Test
    fun `比例为 0 时不加成`() {
        assertEquals(0, ExerciseCompensation.bonusKcal(300.0, enabled = true, pct = 0))
    }

    @Test
    fun `没有消耗时给 0 不产生负数`() {
        assertEquals(0, ExerciseCompensation.bonusKcal(0.0, enabled = true, pct = 50))
        assertEquals(0, ExerciseCompensation.bonusKcal(-50.0, enabled = true, pct = 50))
    }

    @Test
    fun `比例 100 全额加成 比例 25 四分之一`() {
        assertEquals(300, ExerciseCompensation.bonusKcal(300.0, enabled = true, pct = 100))
        assertEquals(75, ExerciseCompensation.bonusKcal(300.0, enabled = true, pct = 25))
    }

    // ---- 2026-11-18：运动补偿接入首页主数字后的断言 ----

    @Test
    fun `今日剩余含补偿 预算2000吃1200运动300补偿50等于950`() {
        // 2000 − 1200 + (300 × 50%) = 950
        assertEquals(950, ExerciseCompensation.remainingWithBonus(2000, 1200, 300.0, true, 50))
    }

    @Test
    fun `补偿关闭时今日剩余退回预算减已吃`() {
        assertEquals(800, ExerciseCompensation.remainingWithBonus(2000, 1200, 300.0, false, 50))
    }

    @Test
    fun `纯饮食剩余永远不含补偿`() {
        assertEquals(800, ExerciseCompensation.remainingFoodOnly(2000, 1200))
        // 主数字 − 纯饮食剩余 = 运动加成 150
        val main = ExerciseCompensation.remainingWithBonus(2000, 1200, 300.0, true, 50)
        assertEquals(150, main - ExerciseCompensation.remainingFoodOnly(2000, 1200))
    }

    @Test
    fun `吃超预算时今日剩余为负 主数字和小字都不含糊`() {
        // 2200 已吃 > 2000 预算，纯饮食 −200；运动加成 150 → 主数字 −50（仍反超）
        assertEquals(-200, ExerciseCompensation.remainingFoodOnly(2000, 2200))
        assertEquals(-50, ExerciseCompensation.remainingWithBonus(2000, 2200, 300.0, true, 50))
    }
}
