package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 体重曲线的边界：空数据、只有 1 条、点按命中、变化量文案。 */
class ChartMathTest {

    @Test
    fun `空数据不会除零`() {
        val bounds = ChartMath.paddedBounds(emptyList())
        assertTrue(bounds.span >= 1f)
        assertTrue(ChartMath.points(emptyList(), bounds, 300f, 200f).isEmpty())
    }

    @Test
    fun `只有一个数值时上下留余量`() {
        val bounds = ChartMath.paddedBounds(listOf(70f))
        assertTrue(bounds.min < 70f)
        assertTrue(bounds.max > 70f)
        assertTrue(bounds.span >= 1f)
    }

    @Test
    fun `目标体重会被算进可见范围`() {
        val bounds = ChartMath.paddedBounds(listOf(90f, 89f), target = 70f)
        assertTrue("目标线必须在可见范围内", bounds.min <= 70f)
    }

    @Test
    fun `只有一条记录时点落在水平中点`() {
        val bounds = ChartMath.paddedBounds(listOf(70f))
        val points = ChartMath.points(listOf(70f), bounds, 300f, 200f)
        assertEquals(1, points.size)
        assertEquals(150f, points[0].x, 0.01f)
    }

    @Test
    fun `多条记录首尾贴边`() {
        val values = listOf(70f, 71f, 72f)
        val bounds = ChartMath.paddedBounds(values)
        val points = ChartMath.points(values, bounds, 300f, 200f)
        assertEquals(0f, points.first().x, 0.01f)
        assertEquals(300f, points.last().x, 0.01f)
        assertEquals(150f, points[1].x, 0.01f)
    }

    @Test
    fun `值越大越靠上`() {
        val bounds = ChartMath.Bounds(60f, 100f)
        assertTrue(ChartMath.yOf(90f, bounds, 200f) < ChartMath.yOf(70f, bounds, 200f))
    }

    @Test
    fun `目标轨迹首末正确且单点不崩`() {
        val line = ChartMath.targetTrajectory(90f, 70f, 5)
        assertEquals(5, line.size)
        assertEquals(90f, line.first(), 0.01f)
        assertEquals(70f, line.last(), 0.01f)
        assertEquals(listOf(70f), ChartMath.targetTrajectory(90f, 70f, 1))
        assertTrue(ChartMath.targetTrajectory(90f, 70f, 0).isEmpty())
    }

    @Test
    fun `点按命中最近的点`() {
        assertEquals(-1, ChartMath.nearestIndex(50f, 0, 300f))
        assertEquals(0, ChartMath.nearestIndex(999f, 1, 300f))
        assertEquals(0, ChartMath.nearestIndex(0f, 3, 300f))
        assertEquals(2, ChartMath.nearestIndex(300f, 3, 300f))
        assertEquals(1, ChartMath.nearestIndex(150f, 3, 300f))
    }

    @Test
    fun `变化量文案`() {
        assertEquals("--", ChartMath.changeText(null, 70f))
        assertEquals("持平", ChartMath.changeText(70f, 70f))
        assertTrue(ChartMath.changeText(72f, 70f).contains("2.0"))
        assertTrue(ChartMath.changeText(70f, 72f).contains("2.0"))
    }
}
