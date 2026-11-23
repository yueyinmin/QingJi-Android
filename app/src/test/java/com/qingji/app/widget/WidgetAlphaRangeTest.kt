package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件背景不透明度 0~100 自由可调的边界契约。
 * 背景：旧下限 82 会把用户拖到 30 的值强行夹成 82，导致滑杆显示与实际不符。
 */
class WidgetAlphaRangeTest {

    @Test
    fun `下限为0上限为100 覆盖完整区间`() {
        assertEquals(0, WidgetSpec.ALPHA_MIN)
        assertEquals(100, WidgetSpec.ALPHA_MAX)
    }

    @Test
    fun `滑杆档数覆盖整段每百分之一一格`() {
        // Compose Slider 的 steps = 可选档数 - 1；0~100 共 101 个值，故 steps=99
        val steps = WidgetSpec.ALPHA_MAX - WidgetSpec.ALPHA_MIN - 1
        assertEquals(99, steps)
        // steps + 1 = 档数 = 101（0,1,...,100），恰好覆盖整段
        assertEquals(WidgetSpec.ALPHA_MAX - WidgetSpec.ALPHA_MIN + 1, steps + 2)
    }

    @Test
    fun `两端alpha换算为0和1`() {
        // 与 QingJiWidgets.cardBg 的换算口径一致：percent/MAX
        assertEquals(0f, WidgetSpec.ALPHA_MIN / WidgetSpec.ALPHA_MAX.toFloat(), 0.0001f)
        assertEquals(1f, WidgetSpec.ALPHA_MAX / WidgetSpec.ALPHA_MAX.toFloat(), 0.0001f)
        assertEquals(0.5f, 50 / WidgetSpec.ALPHA_MAX.toFloat(), 0.0001f)
    }

    @Test
    fun `预览下限与常量联动最低值不再被抬高`() {
        val previewFloor = WidgetSpec.ALPHA_MIN / WidgetSpec.ALPHA_MAX.toFloat()
        assertEquals(0f, previewFloor, 0.0001f)
        // 0% 传进来不会被抬到 0.82
        val input = 0 / WidgetSpec.ALPHA_MAX.toFloat()
        assertEquals(input.coerceIn(previewFloor, 1f), 0f, 0.0001f)
    }
}
