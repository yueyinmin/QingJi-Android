package com.qingji.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 周期计时引擎单测（SD-004）：天数计算、跨年/闰年、不为负、统计。
 * 全部走本地日期，不依赖设备时钟（用 today 参数注入）。
 */
class SelfDisciplineEngineTest {

    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun `当前周期天数=今天减开始日+1`() {
        // 开始日就是今天 → 1 天
        assertEquals(1, SelfDisciplineEngine.currentPeriodDays(LocalDate.of(2026, 11, 13), today))
        // 10 天前开始 → 11 天（含今天）
        assertEquals(11, SelfDisciplineEngine.currentPeriodDays(LocalDate.of(2026, 11, 3), today))
    }

    @Test
    fun `无进行中周期返回0`() {
        assertEquals(0, SelfDisciplineEngine.currentPeriodDays(null, today))
    }

    @Test
    fun `单个周期天数边界`() {
        // 开始=结束 同一天 → 1
        assertEquals(
            1,
            SelfDisciplineEngine.periodDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), today)
        )
        // 跨 9 天 → 10
        assertEquals(
            10,
            SelfDisciplineEngine.periodDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), today)
        )
    }

    @Test
    fun `结束日早于开始日不为负返回0`() {
        assertEquals(
            0,
            SelfDisciplineEngine.periodDays(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), today)
        )
    }

    @Test
    fun `历史最佳取所有周期最大天数`() {
        val periods = listOf(
            SelfDisciplineEngine.PeriodInput(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)), // 10
            SelfDisciplineEngine.PeriodInput(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), // 31
            SelfDisciplineEngine.PeriodInput(LocalDate.of(2026, 6, 1), null) // 进行中：到今天 11-13 = 166 天
        )
        // 进行中那个最大
        assertEquals(166, SelfDisciplineEngine.bestPeriodDays(periods, today))
    }

    @Test
    fun `历史最佳无周期返回0`() {
        assertEquals(0, SelfDisciplineEngine.bestPeriodDays(emptyList(), today))
    }

    @Test
    fun `闰年2月天数正确`() {
        // 2024 是闰年：2/28 到 3/1 含端点是 3 天（28、29、1）
        assertEquals(
            3,
            SelfDisciplineEngine.periodDays(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1), today)
        )
        // 平年 2025：2/28 到 3/1 是 2 天
        assertEquals(
            2,
            SelfDisciplineEngine.periodDays(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 1), today)
        )
    }

    @Test
    fun `跨年天数正确`() {
        // 2025-12-31 到 2026-01-02 = 3 天
        assertEquals(
            3,
            SelfDisciplineEngine.periodDays(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 2), today)
        )
    }

    @Test
    fun `符合目标天数与偏离次数统计`() {
        val statuses = listOf(
            SelfDisciplineContracts.STATUS_ON_TARGET,
            SelfDisciplineContracts.STATUS_ON_TARGET,
            SelfDisciplineContracts.STATUS_OFF_TARGET,
            SelfDisciplineContracts.STATUS_SKIP,
            SelfDisciplineContracts.STATUS_ON_TARGET
        )
        assertEquals(3, SelfDisciplineEngine.onTargetDays(statuses))
        assertEquals(1, SelfDisciplineEngine.deviationCount(statuses))
    }

    @Test
    fun `连续记录天数从今天往回数`() {
        val dates = setOf(
            LocalDate.of(2026, 11, 13), // 今天
            LocalDate.of(2026, 11, 12),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 11, 9) // 断了一天
        )
        assertEquals(3, SelfDisciplineEngine.currentStreak(dates, today))
    }

    @Test
    fun `今天没记录则从昨天起算连续`() {
        val dates = setOf(
            LocalDate.of(2026, 11, 12),
            LocalDate.of(2026, 11, 11)
        )
        assertEquals(2, SelfDisciplineEngine.currentStreak(dates, today))
    }
}
