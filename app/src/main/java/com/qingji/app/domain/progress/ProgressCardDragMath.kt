package com.qingji.app.domain.progress

/**
 * 进展卡片拖拽 · 目标索引计算（QJ-UI-PROGRESS-DRAG-003）。
 *
 * 纯函数、不依赖 Compose/Android，可单测。职责单一：给定「手指在列表内容坐标系的 Y」
 * 与「可见项的布局边界」，算出被拖卡片应插入的目标列表索引。
 *
 * 为什么不用「累计位移 / 固定行高」：卡片高度、间距、滚动偏移、密度都可能变化，
 * 用真实布局边界才不会错位（工单 §3.4 硬要求）。
 */
object ProgressCardDragMath {

    /** 可见项布局信息抽象：索引 + 相对列表内容顶部的偏移 + 高度（均 px）。 */
    data class ItemBounds(val index: Int, val offset: Float, val size: Float)

    /**
     * 目标索引 = 第一个「手指 Y < 该项中心」的项索引；若手指越过所有可见项中心，取最后一项索引。
     *
     * 中心点判定天然带滞回：手指不跨过某项中心就不会换位，轻微抖动不反复 reorder。
     * 支持一次跨多项目（直接落到跨过的那项），首尾由可见项边界自然限界。
     *
     * @param fingerY 手指在列表内容坐标系的 Y（已扣除列表顶部偏移，px）
     * @param items 当前可见项布局边界，需按 offset 升序（LazyColumn layoutInfo 天然有序）
     * @return 目标索引（-1 = 无可判定项，调用方应保持原样）
     */
    fun targetIndexFor(fingerY: Float, items: List<ItemBounds>): Int {
        if (items.isEmpty()) return -1
        for (info in items) {
            val center = info.offset + info.size / 2f
            if (fingerY < center) return info.index
        }
        return items.last().index
    }

    /**
     * 是否需要发起换位：目标有效、在合法范围、且与当前不同才更新。
     * 供 UI 用单一口径判断「跨过有效边界」，避免无意义重排。
     */
    fun shouldReorder(target: Int, current: Int, size: Int): Boolean =
        target in 0 until size && target != current
}
