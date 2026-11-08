package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 日记的格子、评分档位、玻璃罐、三种排序。 */
class DiaryRulesTest {

    private val today = LocalDate.of(2026, 11, 7)

    private data class Item(val date: LocalDate, val updated: Long, val rating: Int)

    @Test
    fun `近30天格子从29天前到今天`() {
        val cells = DiaryRules.cells(today)
        assertEquals(30, cells.size)
        assertEquals(today.minusDays(29), cells.first())
        assertEquals(today, cells.last())
    }

    @Test
    fun `评分分四档`() {
        assertEquals(DiaryRules.LEVEL_NONE, DiaryRules.level(0))
        assertEquals(DiaryRules.LEVEL_LOW, DiaryRules.level(1))
        assertEquals(DiaryRules.LEVEL_LOW, DiaryRules.level(2))
        assertEquals(DiaryRules.LEVEL_MID, DiaryRules.level(3))
        assertEquals(DiaryRules.LEVEL_HIGH, DiaryRules.level(4))
        assertEquals(DiaryRules.LEVEL_HIGH, DiaryRules.level(5))
    }

    @Test
    fun `玻璃罐进度不越界`() {
        assertEquals(0f, DiaryRules.jarProgress(0), 0.001f)
        assertEquals(0.5f, DiaryRules.jarProgress(15), 0.001f)
        assertEquals(1f, DiaryRules.jarProgress(30), 0.001f)
        assertEquals(1f, DiaryRules.jarProgress(99), 0.001f)
    }

    @Test
    fun `按日期倒序`() {
        val items = listOf(
            Item(today.minusDays(2), 1L, 5),
            Item(today, 1L, 1),
            Item(today.minusDays(1), 1L, 3)
        )
        val result = DiaryRules.sort(items, DiaryRules.SortMode.DATE, { it.date }, { it.updated }, { it.rating })
        assertEquals(today, result[0].date)
        assertEquals(today.minusDays(2), result[2].date)
    }

    @Test
    fun `按评分倒序`() {
        val items = listOf(
            Item(today, 1L, 1),
            Item(today, 2L, 5),
            Item(today, 3L, 3)
        )
        val result = DiaryRules.sort(items, DiaryRules.SortMode.RATING, { it.date }, { it.updated }, { it.rating })
        assertEquals(5, result[0].rating)
        assertEquals(1, result[2].rating)
    }

    @Test
    fun `按更新时间倒序`() {
        val items = listOf(Item(today, 10L, 1), Item(today, 99L, 2), Item(today, 50L, 3))
        val result = DiaryRules.sort(items, DiaryRules.SortMode.UPDATED, { it.date }, { it.updated }, { it.rating })
        assertEquals(99L, result[0].updated)
        assertEquals(10L, result[2].updated)
    }

    @Test
    fun `空内容有兜底摘要且换行会被压平`() {
        assertTrue(DiaryRules.previewText("", today).contains("11月7日"))
        assertEquals("今天 不错", DiaryRules.previewText("今天\n不错", today))
    }
}
