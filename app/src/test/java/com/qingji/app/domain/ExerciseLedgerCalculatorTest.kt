package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 统一运动消耗账本聚合单测（2026-11-18）。
 * 重点锁两件事：力量训练要算进消耗、日/周/月边界与有氧口径一致。
 */
class ExerciseLedgerCalculatorTest {

    private val today = LocalDate.of(2026, 11, 18)

    private fun row(
        d: LocalDate,
        type: String,
        kcal: Double,
        seconds: Int = 1800
    ) = ExerciseSessionCalorieEntity(
        sourceType = type,
        sourceId = "test-$d-$type-${kcal.toInt()}",
        date = d,
        durationSeconds = seconds,
        caloriesGross = kcal
    )

    private val rows = listOf(
        row(today, "STRENGTH_TRAINING", 200.0, 3600),        // 今天力量
        row(today, "CARDIO", 100.0, 1800),                    // 今天有氧
        row(today.minusDays(6), "WALKING", 80.0),             // 边界内步行
        row(today.minusDays(7), "STRENGTH_TRAINING", 500.0),  // 边界外力量
        row(today.minusDays(29), "CARDIO", 90.0),             // 30 天边界内
        row(today.minusDays(30), "CARDIO", 70.0)              // 30 天边界外
    )

    @Test
    fun `力量训练算进今日消耗 不再纹丝不动`() {
        val s = ExerciseLedgerCalculator.summarize(rows, ExerciseRangeCalculator.RANGE_DAY, today)
        assertEquals(2, s.count)          // 力量 + 有氧各一行
        assertEquals(300.0, s.kcal, 0.0)  // 200 + 100
        assertEquals(90, s.minutes)       // 3600 + 1800 秒
        assertEquals("今日", s.label)
    }

    @Test
    fun `近7天含边界不含第8天 力量和有氧都算`() {
        val s = ExerciseLedgerCalculator.summarize(rows, ExerciseRangeCalculator.RANGE_WEEK, today)
        assertEquals(3, s.count)
        assertEquals(380.0, s.kcal, 0.0) // 200 + 100 + 80
    }

    @Test
    fun `近30天含第29天不含第30天`() {
        val s = ExerciseLedgerCalculator.summarize(rows, ExerciseRangeCalculator.RANGE_MONTH, today)
        // 近 30 天窗口是 [today-29, today]：0/1/6/7/29 天这 5 行都算，第 30 天不算
        assertEquals(5, s.count)
        assertEquals(970.0, s.kcal, 0.0) // 200+100+80+500+90
    }

    @Test
    fun `分类占比按来源拆开 力量有氧步行各归各位`() {
        val s = ExerciseLedgerCalculator.summarize(rows, ExerciseRangeCalculator.RANGE_WEEK, today)
        val byKind = s.byKind.associate { it.kind to it.kcal }
        assertEquals(200.0, byKind[ExerciseLedgerCalculator.KIND_STRENGTH] ?: 0.0, 0.0)
        assertEquals(100.0, byKind[ExerciseLedgerCalculator.KIND_CARDIO] ?: 0.0, 0.0)
        assertEquals(80.0, byKind[ExerciseLedgerCalculator.KIND_WALK] ?: 0.0, 0.0)
    }

    @Test
    fun `来源类型大小写不敏感 设备来源归其他`() {
        assertEquals(ExerciseLedgerCalculator.KIND_STRENGTH, ExerciseLedgerCalculator.kindOf("strength_training"))
        assertEquals(ExerciseLedgerCalculator.KIND_OTHER, ExerciseLedgerCalculator.kindOf("DEVICE_IMPORTED"))
    }

    @Test
    fun `空账本给零 不报错`() {
        val s = ExerciseLedgerCalculator.summarize(emptyList(), ExerciseRangeCalculator.RANGE_WEEK, today)
        assertEquals(0, s.count)
        assertEquals(0.0, s.kcal, 0.0)
        assertEquals(0, s.minutes)
        assertEquals(emptyList<ExerciseLedgerCalculator.KindTotal>(), s.byKind)
    }
}
