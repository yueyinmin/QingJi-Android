package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 快捷份量倍数：以初始份量为基准、四位标签、数值精度。 */
class PortionRulesTest {

    @Test
    fun `四个档位与文案`() {
        assertEquals(listOf(0.5, 1.0, 1.5, 2.0), PortionRules.MULTIPLIERS)
        assertEquals("0.5x", PortionRules.label(0.5))
        assertEquals("1x", PortionRules.label(1.0))
        assertEquals("1.5x", PortionRules.label(1.5))
        assertEquals("2x", PortionRules.label(2.0))
    }

    @Test
    fun `按基准缩放`() {
        assertEquals(50.0, PortionRules.scaled(100.0, 0.5), 0.001)
        assertEquals(100.0, PortionRules.scaled(100.0, 1.0), 0.001)
        assertEquals(150.0, PortionRules.scaled(100.0, 1.5), 0.001)
        assertEquals(200.0, PortionRules.scaled(100.0, 2.0), 0.001)
    }

    @Test
    fun `连点两次2x仍是2x而不是4x_因为基准不变`() {
        val base = 100.0
        assertEquals(200.0, PortionRules.scaled(base, 2.0), 0.001)
        assertEquals(200.0, PortionRules.scaled(base, 2.0), 0.001)
    }

    @Test
    fun `保留一位小数`() {
        assertEquals(21.6, PortionRules.scaled(14.4, 1.5), 0.001)
    }

    @Test
    fun `异常输入不产生负数`() {
        assertEquals(0.0, PortionRules.scaled(-1.0, 2.0), 0.001)
        assertEquals(0.0, PortionRules.scaled(100.0, -2.0), 0.001)
    }
}
