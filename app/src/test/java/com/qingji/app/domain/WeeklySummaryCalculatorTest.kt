package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 本周小结纯逻辑单测（v3.4.0 · R2）。 */
class WeeklySummaryCalculatorTest {

    private val today = LocalDate.of(2026, 11, 8)

    private fun days(
        n: Int,
        kcal: Double = 1500.0,
        exercise: Double = 0.0,
        water: Int = 0,
        allLogged: Boolean = true
    ) = (0 until n).map { i ->
        WeeklySummaryCalculator.Day(
            date = today.minusDays((n - 1 - i).toLong()),
            kcal = kcal,
            exerciseKcal = exercise,
            waterMl = water,
            hasLog = allLogged
        )
    }

    private fun summarize(
        list: List<WeeklySummaryCalculator.Day>,
        goal: Int = 1800,
        waterGoal: Int = 2000,
        start: Double? = null,
        end: Double? = null
    ) = WeeklySummaryCalculator.summarize(list, goal, waterGoal, start, end)

    @Test
    fun lessThanThreeDaysGivesNoConclusion() {
        val r = summarize(days(2))
        assertFalse(r.ready)
        assertEquals(2, r.recordedDays)
        assertEquals(0, r.avgKcal)
        assertTrue(r.conclusion.contains("再记 1 天"))
        assertTrue(r.advice.isEmpty())
    }

    @Test
    fun emptyWeekNeverShowsFakeZeroConclusion() {
        val r = summarize(days(7, allLogged = false))
        assertFalse(r.ready)
        assertEquals(0, r.recordedDays)
        assertTrue(r.conclusion.contains("再记 3 天"))
    }

    @Test
    fun sevenDaysProducesConclusionWithAverageGap() {
        val r = summarize(days(7, kcal = 1500.0), goal = 1800)
        assertTrue(r.ready)
        assertEquals(7, r.recordedDays)
        assertEquals(1500, r.avgKcal)
        assertEquals(300, r.avgGap)
        assertTrue(r.conclusion.contains("平均每天少 300 千卡"))
        assertTrue(r.conclusion.contains("体重基本持平"))
        assertTrue(r.advice.isNotEmpty())
    }

    @Test
    fun exerciseKcalIsAddedToGap() {
        val r = summarize(days(3, kcal = 1500.0, exercise = 200.0), goal = 1800)
        assertEquals(500, r.avgGap)
    }

    @Test
    fun overBudgetGivesGentleAdvice() {
        val r = summarize(days(4, kcal = 2300.0), goal = 1800)
        assertEquals(-500, r.avgGap)
        assertTrue(r.conclusion.contains("平均每天多 500 千卡"))
        assertTrue(r.advice.contains("记全"))
    }

    @Test
    fun weightLossAndGainWording() {
        val down = summarize(days(3), start = 70.0, end = 69.4)
        assertTrue(down.conclusion.contains("体重降了 0.6kg"))
        assertEquals(-0.6, down.weightChangeKg, 0.0001)

        val up = summarize(days(3), start = 70.0, end = 70.8)
        assertTrue(up.conclusion.contains("体重涨了 0.8kg"))
    }

    @Test
    fun waterAndExerciseDaysAreCountedAcrossAllDays() {
        val mixed = listOf(
            WeeklySummaryCalculator.Day(today.minusDays(2), 1500.0, 0.0, 2200, true),
            WeeklySummaryCalculator.Day(today.minusDays(1), 1500.0, 300.0, 800, true),
            WeeklySummaryCalculator.Day(today, 1500.0, 100.0, 2100, true)
        )
        val r = summarize(mixed, waterGoal = 2000)
        assertEquals(2, r.waterDays)
        assertEquals(2, r.exerciseDays)
    }

    @Test
    fun noMedicalWordingInOutput() {
        val r = summarize(days(7), goal = 1800, start = 70.0, end = 69.0)
        val all = r.conclusion + r.advice
        listOf("诊断", "治疗", "就医", "疾病", "药").forEach {
            assertFalse("结论里不该出现「$it」", all.contains(it))
        }
    }
}
