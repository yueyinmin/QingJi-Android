package com.qingji.app.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 进展页日记卡分组逻辑单测（三种日记共用，锁住排序 / 限量 / 空态行为）。 */
class DiaryGroupingTest {

    private data class Row(val date: LocalDate, val text: String)

    private val d1 = LocalDate.of(2026, 11, 18)
    private val d2 = LocalDate.of(2026, 11, 17)
    private val d3 = LocalDate.of(2026, 11, 16)

    @Test
    fun `空列表返回空分组`() {
        assertEquals(emptyList<DiaryGrouping.DayGroup<Row>>(),
            DiaryGrouping.groupByDate(emptyList<Row>(), { it.date }))
    }

    @Test
    fun `按日期分组且日期从新到旧`() {
        val groups = DiaryGrouping.groupByDate(
            listOf(Row(d1, "a"), Row(d2, "b"), Row(d1, "c")),
            { it.date }
        )
        assertEquals(listOf(d1, d2), groups.map { it.date })
        // 同一天保持传入顺序
        assertEquals(listOf("a", "c"), groups[0].items.map { it.text })
    }

    @Test
    fun `limit限制天数且不影响组内条目`() {
        val groups = DiaryGrouping.groupByDate(
            listOf(Row(d1, "a"), Row(d2, "b"), Row(d3, "c")),
            { it.date },
            limit = 2
        )
        assertEquals(listOf(d1, d2), groups.map { it.date })
        assertEquals(listOf("a"), groups[0].items.map { it.text })
    }

    @Test
    fun `limit为0返回空`() {
        val groups = DiaryGrouping.groupByDate(
            listOf(Row(d1, "a")), { it.date }, limit = 0
        )
        assertEquals(emptyList<DiaryGrouping.DayGroup<Row>>(), groups)
    }
}
