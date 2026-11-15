package com.qingji.app.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 进展页卡片顺序 / 显隐解析的保真测试（TASK-07）。
 *
 * 这些断言锁的是「改造前后行为一致」：
 * 空值、未知 ID、缺卡补位、隐藏过滤 —— 任何一条变了，用户的卡片布局就会乱。
 */
class ProgressCardCatalogTest {

    @Test
    fun `默认顺序包含七张可管理卡片且身体数据不在其中`() {
        assertEquals(7, ProgressCardCatalog.DEFAULT_ORDER.size)
        assertEquals(
            listOf("exercise", "forecast", "chart", "water", "nutrition", "measure", "photos"),
            ProgressCardCatalog.DEFAULT_ORDER
        )
        // 身体数据卡固定最上，不属于可管理卡片
        assertEquals(false, ProgressCardCatalog.isKnown("body"))
        assertEquals(true, ProgressCardCatalog.isKnown("photos"))
    }

    @Test
    fun `空串与null都回落到默认顺序`() {
        assertEquals(ProgressCardCatalog.DEFAULT_ORDER, ProgressCardCatalog.parseOrder(null))
        assertEquals(ProgressCardCatalog.DEFAULT_ORDER, ProgressCardCatalog.parseOrder(""))
    }

    @Test
    fun `未知ID被丢弃不报错`() {
        val raw = "chart,not_a_card,water"
        assertEquals(
            listOf("chart", "water", "exercise", "forecast", "nutrition", "measure", "photos"),
            ProgressCardCatalog.parseOrder(raw)
        )
    }

    @Test
    fun `旧设置里缺的新卡按默认顺序补到末尾`() {
        // 老版本只存过三张卡，后来新增了 measure / photos，应该自动出现
        val raw = "water,exercise,chart"
        val order = ProgressCardCatalog.parseOrder(raw)
        assertEquals(listOf("water", "exercise", "chart"), order.take(3))
        assertEquals(
            listOf("forecast", "nutrition", "measure", "photos"),
            order.drop(3)
        )
        assertEquals(7, order.size)
    }

    @Test
    fun `隐藏列表解析忽略空白项`() {
        assertEquals(setOf("water", "photos"), ProgressCardCatalog.parseHidden("water,,photos,"))
        assertEquals(emptySet<String>(), ProgressCardCatalog.parseHidden(null))
        assertEquals(emptySet<String>(), ProgressCardCatalog.parseHidden("  "))
    }

    @Test
    fun `可见卡片等于排序后去掉隐藏项`() {
        val visible = ProgressCardCatalog.visibleCards(
            rawOrder = "photos,chart",
            rawHidden = "water,nutrition"
        )
        assertEquals(
            listOf("photos", "chart", "exercise", "forecast", "measure"),
            visible
        )
    }

    @Test
    fun `全部隐藏时可见列表为空`() {
        val allHidden = ProgressCardCatalog.DEFAULT_ORDER.joinToString(",")
        assertEquals(
            emptyList<String>(),
            ProgressCardCatalog.visibleCards(rawOrder = "", rawHidden = allHidden)
        )
    }
}
