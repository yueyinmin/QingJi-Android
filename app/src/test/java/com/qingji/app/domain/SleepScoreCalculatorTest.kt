package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ============================================================
 * 睡眠评分计算器单测（工单「评分环」验收清单）
 * ------------------------------------------------------------
 * 锁的是工单写死的评分口径，任何一处权重改动都会在这里红灯：
 *   基础60 + 时长25 + 质量档 + 精神档 + 夜醒(-3/次, 上限-9) + 规律性
 *   clamp 0~100；评级 <60 较差 / 60-74 一般 / 75-89 良好 / >=90 优秀。
 *
 * 说明：工单验收清单写「6h/较差/醒3次 → <60」是简写。按工单第 3 节
 * 明列的算法，6h 时长只能拿 19 分、较差 -5、醒 3 次 -9 合计 65 分；
 * 要落到 <60 还需要精神疲惫(-5) 或规律性差(-4)。这里按**算法正文**
 * 实现（正文是可回溯的权威口径），并用完整组合覆盖 <60 场景。
 * ============================================================
 */
class SleepScoreCalculatorTest {

    /** 规律性满分的样本：近 7 晚入睡都在 23:30 附近（睡眠轴 1410）。 */
    private val steadyBedtimes = listOf(1410, 1410, 1410, 1410, 1410, 1410)

    @Test
    fun `8小时良好没醒精神良好 评分优秀`() {
        val r = SleepScoreCalculator.evaluate(
            durationMinutes = 480, quality = 4, morningFeeling = 3,
            wakeCount = 0, bedMinute = 1410, recentBedMinutes = steadyBedtimes
        )
        // 60 + 25(时长达标) + 6(良好) + 3(精神良好) + 0(没醒) + 0(规律稳定) = 94
        assertEquals(94, r.score)
        assertEquals("优秀", r.rating)
    }

    @Test
    fun `很差组合评分低于60`() {
        val r = SleepScoreCalculator.evaluate(
            durationMinutes = 360, quality = 2, morningFeeling = 1,
            wakeCount = 3, bedMinute = 60, // 01:00，睡眠轴 1500
            recentBedMinutes = steadyBedtimes // 中位 1410 → 偏移 90 分钟 → -4
        )
        // 60 + 19(6h) - 5(较差) - 5(疲惫) - 9(醒3次) - 4(规律差) = 56
        assertEquals(56, r.score)
        assertEquals("较差", r.rating)
    }

    @Test
    fun `评级档位边界值`() {
        assertEquals("较差", SleepScoreCalculator.ratingOf(59))
        assertEquals("一般", SleepScoreCalculator.ratingOf(60))
        assertEquals("一般", SleepScoreCalculator.ratingOf(74))
        assertEquals("良好", SleepScoreCalculator.ratingOf(75))
        assertEquals("良好", SleepScoreCalculator.ratingOf(89))
        assertEquals("优秀", SleepScoreCalculator.ratingOf(90))
    }

    @Test
    fun `评分边界不会越出0到100`() {
        // 极差组合：0时长 + 很差 -10 + 疲惫 -5 + 夜醒封顶 -9 = 36。
        // 基础 60 分保底，最低只能到 36，永远掉不到 0 以下。
        val low = SleepScoreCalculator.evaluate(
            durationMinutes = 0, quality = 1, morningFeeling = 1,
            wakeCount = 30, bedMinute = 1410, recentBedMinutes = steadyBedtimes
        )
        assertEquals(36, low.score)
        // 满分组合：60 + 25(时长满) + 10(很好) + 5(精力充沛) = 100，刚好封顶。
        val high = SleepScoreCalculator.evaluate(
            durationMinutes = 600, quality = 5, morningFeeling = 4,
            wakeCount = 0, bedMinute = 1410, recentBedMinutes = steadyBedtimes
        )
        assertEquals(100, high.score)
    }

    @Test
    fun `规律性不足三晚不计分且明细有说明`() {
        val r = SleepScoreCalculator.evaluate(
            durationMinutes = 480, quality = 4, morningFeeling = 3,
            wakeCount = 0, bedMinute = 1410,
            recentBedMinutes = listOf(1410, 1410) // 只有 2 晚
        )
        assertNull(r.regularity)
        val note = r.parts.first { it.label == "规律性" }.note
        assertEquals("近 7 天不足 3 晚，不计分", note)
        // 60 + 25 + 6 + 3 + 0 + 0(规律不计) = 94
        assertEquals(94, r.score)
    }

    @Test
    fun `跨午夜入睡的规律性按环形距离算`() {
        // 23:30(1410) 与 00:27(26→1466) 只差 56 分钟，不能被当成差 23 小时
        val r = SleepScoreCalculator.evaluate(
            durationMinutes = 450, quality = 4, morningFeeling = 3,
            wakeCount = 0, bedMinute = 26,
            recentBedMinutes = listOf(1410, 1410, 1410, 26)
        )
        val reg = r.regularity
        assertNotNull(reg)
        // 偶数取上中位数：排序后 [1410,1410,1410,1466] 索引 2 = 1410；偏移 56 → -2
        assertEquals(56, reg!!.deviationMinutes)
        assertEquals(-2, reg.delta)
    }

    @Test
    fun `夜醒扣分上限9分`() {
        val r3 = SleepScoreCalculator.evaluate(
            480, 4, 3, 3, 1410, steadyBedtimes
        )
        val r30 = SleepScoreCalculator.evaluate(
            480, 4, 3, 30, 1410, steadyBedtimes
        )
        assertEquals(r3.score, r30.score) // 3 次和 30 次扣的一样（上限 -9）
    }

    @Test
    fun `时长超过目标不加分`() {
        val exact = SleepScoreCalculator.evaluate(480, 4, 3, 0, 1410, steadyBedtimes)
        val overshoot = SleepScoreCalculator.evaluate(720, 4, 3, 0, 1410, steadyBedtimes)
        assertEquals(exact.score, overshoot.score)
    }

    @Test
    fun `无入睡数据时规律性不计分`() {
        val r = SleepScoreCalculator.evaluate(
            durationMinutes = 480, quality = 4, morningFeeling = 3,
            wakeCount = 0, bedMinute = null,
            recentBedMinutes = listOf(1410, 1410, 1410)
        )
        assertNull(r.regularity)
    }
}
