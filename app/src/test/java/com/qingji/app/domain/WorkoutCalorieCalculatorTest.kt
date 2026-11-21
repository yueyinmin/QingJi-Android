package com.qingji.app.domain

import com.qingji.app.data.local.entity.WorkoutCardioDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一热量分发器（P0）单测。
 * 验证：力量+有氧汇总、纯力量/纯有氧/混合方法标记、缺参数不虚构被跳过、
 * 不重算有氧（直接用录入确认值）。
 */
class WorkoutCalorieCalculatorTest {

    private fun cardio(kcal: Double, name: String = "跑步") = WorkoutCardioDetail(
        exerciseNameSnapshot = name,
        durationSeconds = 600,
        calculatedKcal = kcal,
        calculationMethod = "user_direct",
        calculationVersion = "v1"
    )

    @Test
    fun `纯力量只结算力量方法标记为力量`() {
        val r = WorkoutCalorieCalculator.combine(120.0, emptyList())
        assertEquals(120.0, r.totalKcal, 0.01)
        assertEquals(120.0, r.strengthKcal, 0.01)
        assertEquals(0.0, r.cardioKcal, 0.01)
        assertEquals(WorkoutCalorieCalculator.METHOD_STRENGTH_ONLY, r.method)
        assertEquals(0, r.cardioItems)
        assertEquals(0, r.notComputableItems)
        assertFalse(r.hasSkipped)
    }

    @Test
    fun `纯有氧用录入确认值不重算`() {
        val r = WorkoutCalorieCalculator.combine(0.0, listOf(cardio(55.5), cardio(40.0, "骑行")))
        assertEquals(95.5, r.totalKcal, 0.01)
        assertEquals(0.0, r.strengthKcal, 0.01)
        assertEquals(95.5, r.cardioKcal, 0.01)
        assertEquals(WorkoutCalorieCalculator.METHOD_CARDIO_ONLY, r.method)
        assertEquals(2, r.cardioItems)
    }

    @Test
    fun `混合训练汇总力量与有氧为一条整场`() {
        val r = WorkoutCalorieCalculator.combine(120.0, listOf(cardio(55.5), cardio(40.0, "骑行")))
        assertEquals(215.5, r.totalKcal, 0.01)
        assertEquals(120.0, r.strengthKcal, 0.01)
        assertEquals(95.5, r.cardioKcal, 0.01)
        assertEquals(WorkoutCalorieCalculator.METHOD_MIXED, r.method)
        assertEquals(WorkoutCalorieCalculator.VERSION, r.version)
    }

    @Test
    fun `缺参数的有氧项目被跳过且不虚构热量`() {
        // 一条无有效热量（缺参数）、一条负值、一条正常
        val r = WorkoutCalorieCalculator.combine(
            50.0,
            listOf(cardio(0.0, "缺参数"), cardio(-10.0, "异常值"), cardio(30.0, "正常"))
        )
        assertEquals(80.0, r.totalKcal, 0.01)
        assertEquals(1, r.cardioItems)
        assertEquals(2, r.notComputableItems)
        assertTrue(r.hasSkipped)
    }

    @Test
    fun `全部项目不可计算时整场为零且标记跳过`() {
        val r = WorkoutCalorieCalculator.combine(0.0, listOf(cardio(0.0), cardio(-1.0)))
        assertEquals(0.0, r.totalKcal, 0.01)
        assertEquals(0, r.cardioItems)
        assertEquals(2, r.notComputableItems)
        assertTrue(r.hasSkipped)
    }

    @Test
    fun `非有限热量值不计入`() {
        val nan = cardio(Double.NaN, "NaN")
        val inf = cardio(Double.POSITIVE_INFINITY, "Inf")
        assertFalse(WorkoutCalorieCalculator.isComputable(nan))
        assertFalse(WorkoutCalorieCalculator.isComputable(inf))
        val r = WorkoutCalorieCalculator.combine(0.0, listOf(nan, inf, cardio(20.0)))
        assertEquals(20.0, r.totalKcal, 0.01)
    }

    @Test
    fun `isComputable与combine计入口径一致`() {
        val ok = cardio(12.0)
        val bad = cardio(0.0)
        assertTrue(WorkoutCalorieCalculator.isComputable(ok))
        assertFalse(WorkoutCalorieCalculator.isComputable(bad))
    }
}
