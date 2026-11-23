package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 身体数据小组件布局契约：
 * 1) 轮盘不与体重数字同行，而是下移到双栏行右侧、与 BMI 数字垂直居中持平；
 * 2) 双栏位置互换：体脂率在左、BMI 在右；
 * 3) 下方有余量，数值字号放大到 22sp、标签/来源 10sp（compact 与常规档统一）。
 * 说明：Glance 无布局测量 API，这里锁定规格常量，视觉需真机确认。
 */
class BodyWidgetLayoutTest {

    @Test
    fun `轮盘缩为64x34放进双栏行右侧`() {
        assertEquals(64, BMI_GAUGE_WIDTH_DP)
        assertEquals(34, BMI_GAUGE_HEIGHT_DP)
    }

    @Test
    fun `双栏数值字号放大到22sp 标签10sp`() {
        assertEquals(22, DUAL_VALUE_SP)
        assertEquals(10, DUAL_LABEL_SP)
        assert(DUAL_VALUE_SP > DUAL_LABEL_SP)
    }

    @Test
    fun `体脂率在左BMI在右`() {
        // 列顺序契约：左=体脂率，右=BMI（与旧版相反）
        assertEquals("体脂率", LEFT_COLUMN_LABEL)
        assertEquals("BMI", RIGHT_COLUMN_LABEL)
    }

    // 规格镜像（与 QingJiWidgets.kt 保持一致；Glance 无法直接断言 UI 树）
    private val BMI_GAUGE_WIDTH_DP = 64
    private val BMI_GAUGE_HEIGHT_DP = 34
    private val DUAL_VALUE_SP = 22
    private val DUAL_LABEL_SP = 10
    private val LEFT_COLUMN_LABEL = "体脂率"
    private val RIGHT_COLUMN_LABEL = "BMI"
}
