package com.qingji.app.domain

import com.qingji.app.data.local.entity.WeightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 身体数据仪表盘 P0：数据口径与减重进度边界单测。 */
class BodyMetricsCalculatorTest {

    private fun log(kg: Double, date: String, fat: Double? = null) = WeightLog(
        date = LocalDate.parse(date), weightKg = kg, bodyFat = fat
    )

    @Test
    fun `最新体重取完整集合第一条不受曲线范围影响`() {
        // 模拟 date DESC：最新在前，最旧在后
        val all = listOf(log(70.0, "2026-11-20"), log(72.0, "2026-11-10"), log(75.0, "2026-11-01"))
        assertEquals(70.0, BodyMetricsCalculator.latestWeightKg(all), 0.001)
    }

    @Test
    fun `无体重记录回退起始体重`() {
        assertEquals(68.0, BodyMetricsCalculator.latestWeightKg(emptyList(), 68.0), 0.001)
        assertEquals(0.0, BodyMetricsCalculator.latestWeightKg(emptyList()), 0.001)
    }

    @Test
    fun `无效体重被跳过`() {
        val all = listOf(log(-1.0, "2026-11-21"), log(70.0, "2026-11-20"), log(Double.NaN, "2026-11-19"))
        assertEquals(70.0, BodyMetricsCalculator.latestWeightKg(all), 0.001)
    }

    @Test
    fun `最近体脂率与最新体重可不同日`() {
        val all = listOf(
            log(70.0, "2026-11-20", fat = null),
            log(71.0, "2026-11-15", fat = 22.5),
            log(72.0, "2026-11-10", fat = 23.0)
        )
        val r = BodyMetricsCalculator.latestBodyFat(all)
        assertEquals(22.5, r!!.value, 0.001)
        assertEquals(LocalDate.parse("2026-11-15"), r.date)
    }

    @Test
    fun `体脂率非法值与越界被跳过`() {
        val all = listOf(
            log(70.0, "2026-11-20", fat = 99.0),
            log(71.0, "2026-11-19", fat = 0.0),
            log(72.0, "2026-11-18", fat = Double.NaN),
            log(73.0, "2026-11-17", fat = 20.0)
        )
        assertEquals(20.0, BodyMetricsCalculator.latestBodyFat(all)!!.value, 0.001)
    }

    @Test
    fun `无体脂率返回null`() {
        assertNull(BodyMetricsCalculator.latestBodyFat(listOf(log(70.0, "2026-11-20"))))
    }

    @Test
    fun `减重进度边界`() {
        // 起始80 目标60：当前80→0%，70→50%，60→100%，55→不超过100%
        assertEquals(0.0, BodyMetricsCalculator.lossProgressPercent(80.0, 80.0, 60.0), 0.001)
        assertEquals(50.0, BodyMetricsCalculator.lossProgressPercent(80.0, 70.0, 60.0), 0.001)
        assertEquals(100.0, BodyMetricsCalculator.lossProgressPercent(80.0, 60.0, 60.0), 0.001)
        assertEquals(100.0, BodyMetricsCalculator.lossProgressPercent(80.0, 55.0, 60.0), 0.001)
    }

    @Test
    fun `当前高于起始进度为负`() {
        assertTrue(BodyMetricsCalculator.lossProgressPercent(80.0, 85.0, 60.0) < 0.0)
    }

    @Test
    fun `起始等于目标不除零`() {
        assertEquals(0.0, BodyMetricsCalculator.lossProgressPercent(70.0, 70.0, 70.0), 0.001)
        assertEquals(0.0, BodyMetricsCalculator.lossProgressPercent(70.0, 65.0, 70.0), 0.001)
    }

    @Test
    fun `剩余重量与达成判定`() {
        assertEquals(10.0, BodyMetricsCalculator.remainingToTarget(70.0, 60.0), 0.001)
        assertEquals(0.0, BodyMetricsCalculator.remainingToTarget(55.0, 60.0), 0.001)
        assertTrue(BodyMetricsCalculator.isGoalReached(60.0, 60.0))
        assertTrue(BodyMetricsCalculator.isGoalReached(55.0, 60.0))
        assertFalse(BodyMetricsCalculator.isGoalReached(70.0, 60.0))
    }
}
