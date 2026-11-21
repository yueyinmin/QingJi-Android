package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** 营养进度比例边界（工单 E1.6）：Glance 与设置页预览共用同一算法。 */
class WidgetProgressTest {

    @Test
    fun `goal_zero_returns_zero`() {
        assertEquals(0f, WidgetProgress.fraction(80, 0), 0f)
    }

    @Test
    fun `current_zero_returns_zero`() {
        assertEquals(0f, WidgetProgress.fraction(0, 120), 0f)
    }

    @Test
    fun `normal_ratio`() {
        assertEquals(80f / 120f, WidgetProgress.fraction(80, 120), 0.0001f)
    }

    @Test
    fun `over_goal_clamped_to_one`() {
        assertEquals(1f, WidgetProgress.fraction(300, 120), 0f)
    }

    @Test
    fun `negative_input_returns_zero`() {
        assertEquals(0f, WidgetProgress.fraction(-5, 120), 0f)
    }

    @Test
    fun `negative_goal_returns_zero`() {
        assertEquals(0f, WidgetProgress.fraction(80, -1), 0f)
    }
}
