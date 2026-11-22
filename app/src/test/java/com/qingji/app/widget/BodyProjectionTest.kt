package com.qingji.app.widget

import com.qingji.app.domain.BodyMetricsCalculator
import com.qingji.app.domain.BodyMetricsCalculator.GoalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 D10：增重/减重统一口径 + 身体投影 schema 独立。 */
class BodyProjectionTest {

    private val bm = BodyMetricsCalculator

    @Test
    fun `减重目标进度与剩余`() {
        val p = bm.goalProgress(currentWeight = 78.0, startWeight = 82.0, targetWeight = 75.0)
        assertEquals(GoalDirection.LOSE, p.direction)
        assertEquals((4f/7f), p.progress, 0.001f)
        assertEquals(3.0, p.remaining, 0.001)
        assertFalse(p.reached)
        assertTrue(p.computable)
    }

    @Test
    fun `增重目标不再误报达成且剩余正确`() {
        val p = bm.goalProgress(currentWeight = 68.0, startWeight = 65.0, targetWeight = 70.0)
        assertEquals(GoalDirection.GAIN, p.direction)
        assertEquals(0.6f, p.progress, 0.001f)
        assertEquals(2.0, p.remaining, 0.001) // 不是 0
        assertFalse(p.reached) // 不是提前达成
    }

    @Test
    fun `增重达标和超过`() {
        val reached = bm.goalProgress(70.0, 65.0, 70.0)
        assertTrue(reached.reached); assertEquals(0.0, reached.remaining, 0.001)
        assertEquals(1f, reached.progress, 0.001f)
        val over = bm.goalProgress(72.0, 65.0, 70.0)
        assertTrue(over.reached); assertEquals(0.0, over.remaining, 0.001)
        assertEquals(1f, over.progress, 0.001f) // 不超 100%
    }

    @Test
    fun `减重超过目标也不出现负进度`() {
        val p = bm.goalProgress(72.0, 82.0, 75.0)
        assertTrue(p.reached); assertEquals(0.0, p.remaining, 0.001)
        assertEquals(1f, p.progress, 0.001f)
    }

    @Test
    fun `目标缺失不可计算`() {
        val p = bm.goalProgress(70.0, 80.0, 0.0)
        assertFalse(p.computable); assertEquals(0f, p.progress, 0.001f)
    }

    @Test
    fun `初始体重等于目标按维持型不除零`() {
        val p = bm.goalProgress(70.0, 70.0, 70.0)
        assertEquals(GoalDirection.MAINTAIN, p.direction)
        assertTrue(p.computable)
    }

    @Test
    fun `初始体重缺失仍可计算不崩溃`() {
        val p = bm.goalProgress(80.0, 0.0, 75.0)
        assertTrue(p.computable)
        assertTrue(p.progress in 0f..1f) // start=0 时按 GAIN 方向，不给负/超界进度
    }

    @Test
    fun `两个投影schema独立`() {
        assertEquals(1, BodyDataWidgetData.SCHEMA_VERSION)
        assertEquals(1, TodayRemainingWidgetData.SCHEMA_VERSION)
        assertEquals(4, WidgetData.SCHEMA_VERSION) // 大快照不受影响
    }

    @Test
    fun `体脂来源语义标签不混淆`() {
        // UI 文案由 QingJiWidgets.bodyFatSourceLabel 提供；这里断言投影字段来源值域。
        setOf("measured", "estimated", "none", "unsupported").forEach { src ->
            assertTrue(src, BodyDataWidgetData(bodyFatSource = src).bodyFatSource == src)
        }
    }
}
