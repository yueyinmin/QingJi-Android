package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2×2 快捷入口样式契约：
 * 1) 文案「饮食」（原「早餐」，入口是记饮食不限早/午/晚）；
 * 2) 图标彩底为圆角方形（对齐悬浮按钮 / 今日记录），不再是圆形 999dp；
 * 3) 饮食图标资源存在且与旧早餐图标不同（Restaurant 刀叉）。
 */
class WidgetQuickTileStyleTest {

    @Test
    fun `快捷入口文案为饮食`() {
        assertEquals("饮食", WidgetSpec.LABEL_DIET)
    }

    @Test
    fun `图标彩底为圆角方形十dp 不是圆形`() {
        assertEquals(10, WidgetSpec.TILE_ICON_CORNER_DP)
        // 圆形是 999dp；这里必须远小于它，确保是圆角方形
        assert(WidgetSpec.TILE_ICON_CORNER_DP < 100)
    }

    @Test
    fun `饮食图标资源独立于旧早餐图标`() {
        // 两个不同的 drawable 资源名，保证确实换了图
        val diet = com.qingji.app.R.drawable.ic_widget_diet
        val oldBreakfast = com.qingji.app.R.drawable.ic_widget_breakfast
        assert(diet != oldBreakfast)
    }
}
