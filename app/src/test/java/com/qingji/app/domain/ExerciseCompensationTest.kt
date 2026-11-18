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
}
