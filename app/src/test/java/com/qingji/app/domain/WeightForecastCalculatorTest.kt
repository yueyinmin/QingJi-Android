package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 体重趋势预测纯逻辑单测（v3.4.0 · R3）。 */
class WeightForecastCalculatorTest {

    private val today = LocalDate.of(2026, 11, 8)

    /** 连续 n 天，每天变化 delta 公斤。 */
    private fun series(n: Int, start: Double, delta: Double, endAt: LocalDate = today) =
        (0 until n).map { i ->
            WeightForecastCalculator.Point(
                date = endAt.minusDays((n - 1 - i).toLong()),
                weightKg = start + delta * i
            )
        }

    private fun forecast(
        points: List<WeightForecastCalculator.Point>,
        current: Double,
        target: Double = 65.0
    ) = WeightForecastCalculator.forecast(points, current, target, today)

    @Test
    fun steadyDeclineGivesWeeksAndDate() {
        val points = series(7, 70.0, -0.1)
        val r = forecast(points, 69.4)
        assertTrue(r.show)
        assertEquals(7, r.weeks)
        assertEquals(today.plusDays(44), r.estimatedDate)
        assertTrue(r.message.contains("周后到目标体重"))
    }

    @Test
    fun risingTrendNeverPredicts() {
        val r = forecast(series(7, 69.0, 0.1), 69.6)
        assertFalse(r.show)
        assertTrue(r.message.contains("上升"))
    }

    @Test
    fun tooFewSamplesNeverPredicts() {
        val r = forecast(series(3, 70.0, -0.1), 69.8)
        assertFalse(r.show)
        assertTrue(r.message.contains("再记 2 天"))
    }

    @Test
    fun staleDataNeverPredicts() {
        val points = series(7, 70.0, -0.1, endAt = today.minusDays(8))
        val r = forecast(points, 69.4)
        assertFalse(r.show)
        assertTrue(r.message.contains("最近一周没记体重"))
    }

    @Test
    fun aSingleOutlierDoesNotChangeMedianTrend() {
        val points = series(7, 70.0, -0.1).toMutableList()
        // 把其中一天改成离谱的 95kg（比如站错秤），相邻日变化率的中位数仍是 -0.1
        points[3] = points[3].copy(weightKg = 95.0)
        val r = forecast(points, 69.4)
        assertTrue("单个极值不该推翻整体趋势", r.show)
        assertEquals(7, r.weeks)
    }

    @Test
    fun missingTargetOrAlreadyReachedNeverPredicts() {
        assertFalse(forecast(series(7, 70.0, -0.1), 69.4, target = 0.0).show)
        assertTrue(
            forecast(series(7, 70.0, -0.1), 69.4, target = 0.0)
                .message.contains("目标体重")
        )
        assertFalse(forecast(series(7, 70.0, -0.1), 69.4, target = 75.0).show)
    }

    @Test
    fun verySlowDeclineBeyondTwoYearsIsNotShown() {
        // 每天 -0.001kg，5kg 需要 5000 天，超过 2 年上限
        val r = forecast(series(7, 70.0, -0.001), 69.994, target = 65.0)
        assertFalse(r.show)
        assertTrue(r.message.contains("暂难估算"))
    }

    @Test
    fun duplicateDatesAreDeduplicated() {
        val points = series(7, 70.0, -0.1) + series(7, 70.0, -0.1)
        val r = forecast(points, 69.4)
        assertTrue(r.show)
        assertEquals(7, r.weeks)
    }

    @Test
    fun noMedicalWordingInOutput() {
        val r = forecast(series(7, 70.0, -0.1), 69.4)
        listOf("诊断", "治疗", "就医", "疾病", "药").forEach {
            assertFalse(r.message.contains(it))
        }
    }
}
