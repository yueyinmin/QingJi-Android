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
}
