package com.qingji.app.ui.viewmodel

import com.qingji.app.data.local.entity.SleepRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 健康体系 V1.1：睡眠趋势统计纯逻辑测试（工单 9.5：周/月统计正确）。
 */
class SleepTrendStatsTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 18)

    private fun rec(date: LocalDate, minutes: Int): SleepRecordEntity = SleepRecordEntity(
        date = date, startTime = 0L, endTime = 0L, durationMinutes = minutes
    )

    @Test
    fun `空记录 全部天为null 平均null`() {
        val stats = SleepTrendStats.of(emptyList(), today, 7)
        assertEquals(7, stats.days.size)
        assertEquals(today, stats.days.last().first)  // 最后一天 = 今天
        assertEquals(today.minusDays(6), stats.days.first().first)
        stats.days.forEach { assertNull(it.second) }
        assertNull(stats.avgHours)
        assertEquals(0, stats.recordedCount)
    }

    @Test
    fun `单天多段睡眠按时长求和`() {
        // 同一天两段小睡 8h + 1h（补录场景）
        val stats = SleepTrendStats.of(
            listOf(rec(today, 480), rec(today, 60)), today, 7
        )
        assertEquals(9.0, stats.days.last().second!!, 0.001)
        assertEquals(9.0, stats.avgHours!!, 0.001)
        assertEquals(1, stats.recordedCount)
    }

    @Test
    fun `跨多天平均只算有记录的天`() {
        val stats = SleepTrendStats.of(
            listOf(
                rec(today, 420),                 // 今天 7h
                rec(today.minusDays(2), 480)     // 前天 8h；昨天没记录
            ),
            today, 7
        )
        // days 布局：[0]=6天前 … [5]=昨天 … [6]=今天
        assertNull(stats.days[5].second)          // 昨天没记录 → null（不显示 0）
        assertEquals(8.0, stats.days[4].second!!, 0.001)
        assertEquals(7.0, stats.days[6].second!!, 0.001)
        // 平均 = (7+8)/2 = 7.5，不是除以 7 天（工单：无记录不显示为 0 小时）
        assertEquals(7.5, stats.avgHours!!, 0.001)
        assertEquals(2, stats.recordedCount)
    }

    @Test
    fun `窗口外的记录不计入`() {
        val stats = SleepTrendStats.of(
            listOf(rec(today.minusDays(10), 600)),  // 10 天前，窗口外
            today, 7
        )
        assertEquals(0, stats.recordedCount)
        assertNull(stats.avgHours)
    }

    // ========== 2026-11-19 睡眠 UI 重构回归补充（只锁数据口径，页面只画不改数） ==========

    @Test
    fun `达标天数口径 满7小时才算达标 不按平均值`() {
        // 页面 WeeklyTrendCard 用 hours*60 >= 7*60 统计「已达标 N/7」。
        // 这里锁住底层 7 天数据：5 条 >=7h、2 条 <7h，达标数应为 5。
        val stats = SleepTrendStats.of(
            listOf(
                rec(today.minusDays(6), 420),  // 7h  达标
                rec(today.minusDays(5), 450),  // 7.5h 达标
                rec(today.minusDays(4), 480),  // 8h  达标
                rec(today.minusDays(3), 430),  // 7.17h 达标
                rec(today.minusDays(2), 440),  // 7.33h 达标
                rec(today.minusDays(1), 350),  // 5.83h 未达标
                rec(today, 390)               // 6.5h 未达标
            ),
            today, 7
        )
        val achieved = stats.days.mapNotNull { it.second }.count { it * 60 >= 7 * 60 }
        assertEquals(5, achieved)
        assertEquals(7, stats.recordedCount)
    }

    @Test
    fun `选中日期为右端点时历史某晚的趋势跟着那晚走`() {
        // 页面点击趋势某天后，SleepTrendStats.of(recent30, selectedDate, 7) 重算。
        // selectedDate = 3 天前时，窗口应为 [9天前 … 3天前]，右端点 = 3天前。
        val selected = today.minusDays(3)
        val stats = SleepTrendStats.of(emptyList(), selected, 7)
        assertEquals(selected, stats.days.last().first)
        assertEquals(selected.minusDays(6), stats.days.first().first)
    }

    @Test
    fun `跨天睡眠按起床日归属且时长不因跨午夜变负`() {
        // Repository 口径：归属 = 起床日；时长 = (end-start)/60000；end 必须 > start。
        // 23:30 入睡 → 次日 07:30 起床 = 480 分钟，归属次日。
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val start = java.time.LocalDateTime.of(2026, 11, 17, 23, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val end = java.time.LocalDateTime.of(2026, 11, 18, 7, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val minutes = ((end - start) / 60_000L).toInt()
        val attributedDate = java.time.Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        assertEquals(480, minutes)
        assertEquals(java.time.LocalDate.of(2026, 11, 18), attributedDate)
    }
}
