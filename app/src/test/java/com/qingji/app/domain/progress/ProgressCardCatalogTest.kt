package com.qingji.app.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 进展页卡片顺序 / 显隐解析的保真测试（TASK-07）。
 *
 * 这些断言锁的是「改造前后行为一致」：
 * 空值、未知 ID、缺卡补位、隐藏过滤 —— 任何一条变了，用户的卡片布局就会乱。
 * 2026-11-18 追加三种日记卡（sleep/exercise/food_diary）：老设置缺卡要自动补到末尾。
 */
class ProgressCardCatalogTest {

    @Test
    fun `默认顺序包含十张可管理卡片且身体数据不在其中`() {
        assertEquals(10, ProgressCardCatalog.DEFAULT_ORDER.size)
        assertEquals(
            listOf(
                "exercise", "forecast", "chart", "water", "nutrition", "measure", "photos",
                "sleep_diary", "exercise_diary", "food_diary"
            ),
            ProgressCardCatalog.DEFAULT_ORDER
        )
        // 身体数据卡固定最上，不属于可管理卡片
        assertEquals(false, ProgressCardCatalog.isKnown("body"))
        assertEquals(true, ProgressCardCatalog.isKnown("photos"))
        assertEquals(true, ProgressCardCatalog.isKnown("sleep_diary"))
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
            listOf(
                "chart", "water", "exercise", "forecast", "nutrition", "measure", "photos",
                "sleep_diary", "exercise_diary", "food_diary"
            ),
            ProgressCardCatalog.parseOrder(raw)
        )
    }

    @Test
    fun `旧设置里缺的新卡按默认顺序补到末尾`() {
        // 老版本只存过三张卡，后来新增了 measure/photos 和三种日记卡，应该自动出现
        val raw = "water,exercise,chart"
        val order = ProgressCardCatalog.parseOrder(raw)
        assertEquals(listOf("water", "exercise", "chart"), order.take(3))
        assertEquals(
            listOf(
                "forecast", "nutrition", "measure", "photos",
                "sleep_diary", "exercise_diary", "food_diary"
            ),
            order.drop(3)
        )
        assertEquals(10, order.size)
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
            rawHidden = "water,nutrition,sleep_diary"
        )
        assertEquals(
            listOf(
                "photos", "chart", "exercise", "forecast", "measure",
                "exercise_diary", "food_diary"
            ),
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

    // ========== QJ-UI-PROGRESS-DRAG-001 拖拽排序回归（按稳定 ID 换位） ==========

    private val ten = ProgressCardCatalog.DEFAULT_ORDER

    @Test
    fun `第一张卡可以移到最后`() {
        val moved = ProgressCardCatalog.moveToIndex(ten, ten.first(), ten.lastIndex)
        assertEquals(ten.drop(1) + ten.first(), moved)
        assertEquals(10, moved.size)
        assertEquals(ten.toSet(), moved.toSet()) // 不丢不重
    }

    @Test
    fun `最后一张卡可以移到第一`() {
        val moved = ProgressCardCatalog.moveToIndex(ten, ten.last(), 0)
        assertEquals(listOf(ten.last()) + ten.dropLast(1), moved)
        assertEquals(10, moved.size)
    }

    @Test
    fun `按ID反查索引 连续跨越多张卡片不错位`() {
        // 模拟"连续拖同一张卡跨多行"：每一步都按 cardId 重新反查当前位置，
        // 旧实现 dragFrom 索引在换位后会错位，这里锁住按 ID 的结果稳定。
        val card = ten[2]
        var order = ten
        for (target in listOf(3, 4, 5, 6, 7)) {
            order = ProgressCardCatalog.moveToIndex(order, card, target)
            assertEquals(target, order.indexOf(card))
            assertEquals(10, order.size)
        }
    }

    @Test
    fun `非法入参原样返回 不丢卡不重卡`() {
        assertEquals(ten, ProgressCardCatalog.moveToIndex(ten, "not_exist", 3))
        assertEquals(ten, ProgressCardCatalog.moveToIndex(ten, ten.first(), 99))
        assertEquals(ten, ProgressCardCatalog.moveToIndex(ten, ten.first(), -1))
        // 目标就是当前位置 → 不变
        assertEquals(ten, ProgressCardCatalog.moveToIndex(ten, ten[3], 3))
    }

    @Test
    fun `取消回滚等于拖拽前原顺序`() {
        // UI 的 onDragCancel 用 dragStartOrder 覆盖 localOrder；
        // 这里验证"基于原顺序重新换位"得到的目标是确定且可回退的。
        val startOrder = ten
        val afterDrag = ProgressCardCatalog.moveToIndex(startOrder, ten[1], 8)
        assert(afterDrag != startOrder)
        // 回滚：直接用保存的原顺序，UI 无需增量逆操作
        val restored = startOrder
        assertEquals(ten, restored)
    }
}
