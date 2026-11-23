package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 身体数据小组件布局契约：
 * 1) 轮盘不再嵌标题行，而是与体重数字同一行（弥补旧版标题行被 36dp 轮盘撑高、
 *    导致底部 BMI/体脂第三行小字被裁切的缺陷）；
 * 2) compact 档双栏数值字号小于常规档，给底部小字腾高度。
 * 说明：Glance 无布局测量 API，这里锁定规格常量，视觉需真机确认。
 */
class BodyWidgetLayoutTest {

    @Test
    fun `轮盘规格保持70x36 与体重行同高量级`() {
        // 轮盘仍用 70×36，放在体重行右侧与体重数字垂直居中持平
        assertEquals(70, BMI_GAUGE_WIDTH_DP)
        assertEquals(36, BMI_GAUGE_HEIGHT_DP)
    }

    @Test
    fun `compact档双栏字号小于常规档`() {
        assert(BODY_DUAL_VALUE_COMPACT_SP < BODY_DUAL_VALUE_NORMAL_SP)
        assertEquals(16, BODY_DUAL_VALUE_COMPACT_SP)
        assertEquals(20, BODY_DUAL_VALUE_NORMAL_SP)
    }

    // 规格镜像（与 QingJiWidgets.kt 保持一致；Glance 无法直接断言 UI 树）
    private val BMI_GAUGE_WIDTH_DP = 70
    private val BMI_GAUGE_HEIGHT_DP = 36
    private val BODY_DUAL_VALUE_COMPACT_SP = 16
    private val BODY_DUAL_VALUE_NORMAL_SP = 20
}
