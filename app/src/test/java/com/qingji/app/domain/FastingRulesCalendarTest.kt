package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 断食日历的网格与状态判定（2026-11-11 专项）。
 *
 * 闰月、大小月、月初是周几，这几样最容易在界面里手工算出错，
 * 所以全部收进纯函数，这里逐月钉一遍。
 */
class FastingRulesCalendarTest {

    @Test
    fun `2026年11月1日是周日 前面补六格`() {
        val cells = FastingRules.monthCells(YearMonth.of(2026, 11))
        assertEquals("必须是整周倍数", 0, cells.size % 7)
        assertTrue("前六格应该是补位", cells.take(6).all { it == null })
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertEquals(LocalDate.of(2026, 11, 30), cells.last { it != null })
    }

    @Test
    fun `闰年二月有29天`() {
        val days = FastingRules.monthCells(YearMonth.of(2024, 2)).filterNotNull()
        assertEquals(29, days.size)
        assertEquals(LocalDate.of(2024, 2, 29), days.last())
    }

    @Test
    fun `平年二月只有28天`() {
        val days = FastingRules.monthCells(YearMonth.of(2026, 2)).filterNotNull()
        assertEquals(28, days.size)
    }

    @Test
    fun `一整年每个月都是整周网格且日期连续`() {
        for (m in 1..12) {
            val month = YearMonth.of(2026, m)
            val cells = FastingRules.monthCells(month)
            assertEquals("$m 月不是整周", 0, cells.size % 7)
            val dates = cells.filterNotNull()
            assertEquals("$m 月天数不对", month.lengthOfMonth(), dates.size)
            // 日期必须从 1 号连续排到最后一天，中间不能缺
            dates.forEachIndexed { i, d -> assertEquals(i + 1, d.dayOfMonth) }
        }
    }

    @Test
    fun `dayMark 区分未来 有记录 和空白`() {
        val today = LocalDate.of(2026, 11, 11)
        val recorded = setOf(today.minusDays(1))
        assertEquals(
            FastingRules.DayMark.FUTURE,
            FastingRules.dayMark(today.plusDays(1), today, recorded)
        )
        assertEquals(
            FastingRules.DayMark.RECORDED,
            FastingRules.dayMark(today.minusDays(1), today, recorded)
        )
        assertEquals(
            FastingRules.DayMark.EMPTY,
            FastingRules.dayMark(today.minusDays(2), today, recorded)
        )
    }

    @Test
    fun `今天有记录算有记录 没记录算空白`() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(
            FastingRules.DayMark.RECORDED,
            FastingRules.dayMark(today, today, setOf(today))
        )
        assertEquals(
            FastingRules.DayMark.EMPTY,
            FastingRules.dayMark(today, today, emptySet())
        )
    }

    @Test
    fun `补位格子里没有日期`() {
        val cells = FastingRules.monthCells(YearMonth.of(2026, 1))
        assertTrue(cells.any { it == null })
        // null 只出现在首尾，不会插在日期中间
        val firstDate = cells.indexOfFirst { it != null }
        val lastDate = cells.indexOfLast { it != null }
        assertTrue(cells.subList(firstDate, lastDate + 1).none { it == null })
    }
}
