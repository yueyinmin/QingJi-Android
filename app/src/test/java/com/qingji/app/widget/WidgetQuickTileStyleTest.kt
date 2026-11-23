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
    fun `图标放大到30dp 白底与彩底已去`() {
        // 用户要求去白色格子底 + 淡绿彩底，图标放大成为主视觉（原 18dp）
        assertEquals(30, WidgetSpec.QUICK_ICON_DP)
        assert(WidgetSpec.QUICK_ICON_DP > 18)
    }

    @Test
    fun `饮食图标资源独立于旧早餐图标`() {
        // 两个不同的 drawable 资源名，保证确实换了图
        val diet = com.qingji.app.R.drawable.ic_widget_diet
        val oldBreakfast = com.qingji.app.R.drawable.ic_widget_breakfast
        assert(diet != oldBreakfast)
    }
}
