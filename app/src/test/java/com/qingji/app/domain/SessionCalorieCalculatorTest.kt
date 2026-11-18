package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 训练场次热量结算单测（健康体系 P0）。
 * 口径沿用现有公式：千卡 = MET × 体重kg × 时长(小时)，即 MET×体重÷60×分钟。
 */
class SessionCalorieCalculatorTest {

    private fun entry(
        sets: List<WorkoutSet>,
        metLow: Double = 4.25,
        metModerate: Double = 5.0,
        metHigh: Double = 5.75
    ) = SessionCalorieCalculator.StrengthEntry("ex-1", "深蹲", metLow, metModerate, metHigh, sets)

    @Test
    fun `口径核对 MET×体重÷60×分钟 与现有运动模块完全一致`() {
        // MET 5.0 × 70kg × 1 分钟 = 5.0×70÷60 ≈ 5.833 千卡
        assertEquals(5.0 * 70.0 / 60.0, SessionCalorieCalculator.kcalPerMinute(5.0, 70.0), 0.0001)
    }

    @Test
    fun `一组热量 = 做功 按次数折算时间 中档MET`() {
        val r = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(entry(sets = listOf(WorkoutSet(weightKg = 80.0, reps = 10, restSec = 0)))),
            weightKg = 70.0,
            durationMinutes = 0   // 没有时长就不封顶
        )
        // 做功时间 = 10次 × 4秒 = 40 秒（在 8~120 秒之间）
        val workKcal = SessionCalorieCalculator.kcalPerMinute(5.0, 70.0) * 40.0 / 60.0
        // 单组没有「组间休息」（最后一组），只有 30 秒切换准备
        val setupKcal = SessionCalorieCalculator.kcalPerMinute(1.3, 70.0) * 30.0 / 60.0
        assertEquals(workKcal + setupKcal, r.totalKcal, 0.001)
        // 每组分摊展示 = 做功 + 0 休息
        assertEquals(workKcal, r.sets[0].totalKcal, 0.001)
    }

    @Test
    fun `组间休息按静息MET计算 不吃高强度档`() {
        val r = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(entry(sets = listOf(
                WorkoutSet(weightKg = 80.0, reps = 10, restSec = 90),
                WorkoutSet(weightKg = 80.0, reps = 10, restSec = 90)
            ))),
            weightKg = 70.0,
            durationMinutes = 0
        )
        val workKcal = SessionCalorieCalculator.kcalPerMinute(5.0, 70.0) * 40.0 / 60.0 * 2
        // 只算组间休息一次（90 秒，在两组之间），最后一组后面不计
        val restKcal = SessionCalorieCalculator.kcalPerMinute(1.3, 70.0) * 90.0 / 60.0
        val setupKcal = SessionCalorieCalculator.kcalPerMinute(1.3, 70.0) * 30.0 / 60.0
        assertEquals(workKcal + restKcal + setupKcal, r.totalKcal, 0.001)
        // 第 1 组的展示热量含休息段，第 2 组（最后一组）不含
        assertEquals(workKcal / 2 + restKcal, r.sets[0].totalKcal, 0.001)
        assertEquals(workKcal / 2, r.sets[1].totalKcal, 0.001)
    }

    @Test
    fun `RPE 决定强度档 每组热量随强度变化`() {
        val r = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(entry(sets = listOf(
                WorkoutSet(weightKg = 50.0, reps = 10, rpe = 9, restSec = 0),   // 高强度档 5.75
                WorkoutSet(weightKg = 50.0, reps = 10, rpe = 3, restSec = 0),   // 轻松档 4.25
                WorkoutSet(weightKg = 50.0, reps = 10, rpe = 0, restSec = 0)    // 没填 → 中档 5.0
            ))),
            weightKg = 70.0,
            durationMinutes = 0
        )
        assertTrue("RPE9 的组热量应该明显高于 RPE3", r.sets[0].workKcal > r.sets[1].workKcal)
        assertEquals(SessionCalorieCalculator.kcalPerMinute(5.75, 70.0) * 40.0 / 60.0, r.sets[0].workKcal, 0.001)
        assertEquals(SessionCalorieCalculator.kcalPerMinute(4.25, 70.0) * 40.0 / 60.0, r.sets[1].workKcal, 0.001)
        assertEquals(SessionCalorieCalculator.kcalPerMinute(5.0, 70.0) * 40.0 / 60.0, r.sets[2].workKcal, 0.001)
    }

    @Test
    fun `未完成的组不计入消耗`() {
        val r = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(entry(sets = listOf(
                WorkoutSet(weightKg = 80.0, reps = 10),
                WorkoutSet(weightKg = 0.0, reps = 0)   // 没填次数也没填重量 = 没练
            ))),
            weightKg = 70.0,
            durationMinutes = 0
        )
        assertEquals(1, r.sets.size)
    }

    @Test
    fun `估算时长超过实际场次时按实际时长等比封顶`() {
        // 两组各 40 秒做功 + 90 秒休息 + 30 秒准备 = 200 秒估算；实际场次只有 100 秒
        val r = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(entry(sets = listOf(
                WorkoutSet(weightKg = 80.0, reps = 10, restSec = 90),
                WorkoutSet(weightKg = 80.0, reps = 10, restSec = 0)
            ))),
            weightKg = 70.0,
            durationMinutes = 2   // 120 秒
        )
        assertEquals(120.0 / 200.0, r.scale, 0.001)
        val uncapped = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(entry(sets = listOf(
                WorkoutSet(weightKg = 80.0, reps = 10, restSec = 90),
                WorkoutSet(weightKg = 80.0, reps = 10, restSec = 0)
            ))),
            weightKg = 70.0,
            durationMinutes = 0
        )
        assertEquals(uncapped.totalKcal * r.scale, r.totalKcal, 0.001)
    }

    @Test
    fun `只有时长没有组数据时保守估算 中等MET打七折`() {
        // MET 5.0 × 70kg ÷ 60 × 30 分钟 × 0.7
        val expected = SessionCalorieCalculator.kcalPerMinute(5.0, 70.0) * 30 * 0.7
        assertEquals(expected, SessionCalorieCalculator.estimateFromDurationOnly(30, 70.0, 5.0), 0.001)
        assertEquals(0.0, SessionCalorieCalculator.estimateFromDurationOnly(0, 70.0, 5.0), 0.001)
    }

    @Test
    fun `多动作多组整场汇总 含每个动作的切换准备时间`() {
        val second = SessionCalorieCalculator.StrengthEntry(
            "ex-2", "拉伸放松", 2.0, 2.3, 2.65,
            listOf(WorkoutSet(weightKg = 0.0, reps = 0))  // 全空组 → 该动作被过滤
        )
        val r = SessionCalorieCalculator.settleStrengthSession(
            entries = listOf(
                entry(sets = listOf(WorkoutSet(weightKg = 80.0, reps = 10))),
                second
            ),
            weightKg = 70.0,
            durationMinutes = 0
        )
        // 第二个动作没有有效组 → 只有第一个动作的做功 + 一次准备时间
        val workKcal = SessionCalorieCalculator.kcalPerMinute(5.0, 70.0) * 40.0 / 60.0
        val setupKcal = SessionCalorieCalculator.kcalPerMinute(1.3, 70.0) * 30.0 / 60.0
        assertEquals(workKcal + setupKcal, r.totalKcal, 0.001)
        assertEquals(1, r.sets.size)
    }
}
