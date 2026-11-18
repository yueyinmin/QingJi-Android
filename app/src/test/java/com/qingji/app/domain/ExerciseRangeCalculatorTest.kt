package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseLog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 日/周/月统计：边界按 today 算，不用系统时钟。 */
class ExerciseRangeCalculatorTest {

    private val today = LocalDate.of(2026, 11, 18)
    private fun log(d: LocalDate, min: Int, kcal: Double) = ExerciseLog(
        date = d, type = "跑步", minutes = min, kcal = kcal
    )
    private val logs = listOf(
        log(today, 30, 250.0),           // 今天
        log(today.minusDays(1), 20, 150.0),
        log(today.minusDays(6), 40, 300.0), // 近 7 天边界内
        log(today.minusDays(7), 60, 500.0), // 近 7 天边界外
        log(today.minusDays(29), 10, 80.0), // 近 30 天边界内
        log(today.minusDays(30), 10, 80.0)  // 近 30 天边界外
    )

    @Test
    fun `今日只算今天`() {
        val s = ExerciseRangeCalculator.summarize(logs, ExerciseRangeCalculator.RANGE_DAY, today)
        assertEquals(1, s.count)
        assertEquals(30, s.minutes)
        assertEquals(250.0, s.kcal, 0.0)
        assertEquals("今日", s.label)
    }

    @Test
    fun `近7天含边界不含第8天`() {
        val s = ExerciseRangeCalculator.summarize(logs, ExerciseRangeCalculator.RANGE_WEEK, today)
        assertEquals(3, s.count)
        assertEquals(90, s.minutes)
        assertEquals(700.0, s.kcal, 0.0)
    }

    @Test
    fun `近30天含第30天边界`() {
        val s = ExerciseRangeCalculator.summarize(logs, ExerciseRangeCalculator.RANGE_MONTH, today)
        assertEquals(5, s.count)
        assertEquals(160, s.minutes)
        assertEquals(1280.0, s.kcal, 0.0)
    }

    @Test
    fun `空数据不崩 全为0`() {
        val s = ExerciseRangeCalculator.summarize(emptyList(), ExerciseRangeCalculator.RANGE_WEEK, today)
        assertEquals(0, s.count)
        assertEquals(0, s.minutes)
        assertEquals(0.0, s.kcal, 0.0)
    }

    @Test
    fun `未知范围退回近7天`() {
        val s = ExerciseRangeCalculator.summarize(logs, "unknown", today)
        assertEquals(3, s.count)
    }
}
