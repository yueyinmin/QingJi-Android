package com.qingji.app.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拖拽目标索引计算单测（QJ-UI-PROGRESS-DRAG-003）。
 * 覆盖工单 §九中可自动化的场景：跨多项目、首尾限界、中心滞回、无边界、取消回滚。
 * 手势/动画/真机视觉仍需人工验收（见实施交接包）。
 */
class ProgressCardDragMathTest {

    // 5 张卡，等高 60px（52dp 行 + 8dp 间距，这里用 px 抽象），中心 30/90/150/210/270
    private val five = listOf(
        ProgressCardDragMath.ItemBounds(0, 0f, 60f),
        ProgressCardDragMath.ItemBounds(1, 60f, 60f),
        ProgressCardDragMath.ItemBounds(2, 120f, 60f),
        ProgressCardDragMath.ItemBounds(3, 180f, 60f),
        ProgressCardDragMath.ItemBounds(4, 240f, 60f)
    )

    @Test
    fun `手指在第一张中心之前返回索引零`() {
        assertEquals(0, ProgressCardDragMath.targetIndexFor(0f, five))
        assertEquals(0, ProgressCardDragMath.targetIndexFor(29f, five))
    }

    @Test
    fun `手指在各项中心处判定为该项`() {
        // 恰好中心：fingerY < center 为 false → 落到下一项（中心点判定）
        assertEquals(1, ProgressCardDragMath.targetIndexFor(30f, five))
        assertEquals(2, ProgressCardDragMath.targetIndexFor(90f, five))
    }

    @Test
    fun `一次跨多项目直接落到跨过的项`() {
        assertEquals(4, ProgressCardDragMath.targetIndexFor(230f, five))
        assertEquals(4, ProgressCardDragMath.targetIndexFor(999f, five))
    }

    @Test
    fun `底部越过所有中心后限界到最后一项`() {
        assertEquals(4, ProgressCardDragMath.targetIndexFor(500f, five))
        assertEquals(0, ProgressCardDragMath.targetIndexFor(-100f, five))
    }

    @Test
    fun `空布局返回负一由调用方保持原样`() {
        assertEquals(-1, ProgressCardDragMath.targetIndexFor(100f, emptyList()))
    }

    @Test
    fun `中心滞回轻微抖动不触发换位`() {
        // 手指停在第一项中心略下（31f）→ 判为索引1；在中心附近 29f→0、31f→1 才是真实边界
        val a = ProgressCardDragMath.targetIndexFor(89f, five)   // < 90，仍是索引1
        val b = ProgressCardDragMath.targetIndexFor(91f, five)   // > 90，索引2
        assertEquals(1, a)
        assertEquals(2, b)
        // 89 与 88 都在同一项内，不产生换位 → 轻微抖动稳定
        assertEquals(
            ProgressCardDragMath.targetIndexFor(88f, five),
            ProgressCardDragMath.targetIndexFor(89f, five)
        )
    }

    @Test
    fun `shouldReorder仅在目标有效且不同时为真`() {
        assertTrue(ProgressCardDragMath.shouldReorder(3, 1, 5))
        assertFalse(ProgressCardDragMath.shouldReorder(1, 1, 5))   // 同位置不换位
        assertFalse(ProgressCardDragMath.shouldReorder(-1, 1, 5))  // 无效目标
        assertFalse(ProgressCardDragMath.shouldReorder(5, 1, 5))   // 越界
    }

    @Test
    fun `滚动后布局偏移变化目标索引随之重算`() {
        // 列表向上滚 60px：首个可见项 index=1、offset=0，手指仍在内容 Y=95
        val scrolled = listOf(
            ProgressCardDragMath.ItemBounds(1, 0f, 60f),
            ProgressCardDragMath.ItemBounds(2, 60f, 60f),
            ProgressCardDragMath.ItemBounds(3, 120f, 60f)
        )
        // 内容 Y=95 → 超过 index1 中心(30)、未过 index2 中心(90)？95>90 → index3
        assertEquals(3, ProgressCardDragMath.targetIndexFor(95f, scrolled))
        // 手指内容 Y 回到 index1 中心之前（25 < 30）→ 落回 index1
        assertEquals(1, ProgressCardDragMath.targetIndexFor(25f, scrolled))
    }

    @Test
    fun `取消回滚后重新拖同一卡目标可重现`() {
        val start = 0
        val target = ProgressCardDragMath.targetIndexFor(150f, five)
        val afterMove = ProgressCardCatalog.moveToIndex(ProgressCardCatalog.DEFAULT_ORDER, ProgressCardCatalog.FORECAST, target)
        assert(afterMove != ProgressCardCatalog.DEFAULT_ORDER)
        // 回滚用快照，重新计算目标得到相同值
        assertEquals(target, ProgressCardDragMath.targetIndexFor(150f, five))
        assertEquals(start, ProgressCardCatalog.DEFAULT_ORDER.indexOf(ProgressCardCatalog.FORECAST))
    }
}
