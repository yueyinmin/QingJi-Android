package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 体重单位换算（M7）。
 * 重点不是「乘 2」这么简单，而是三条边界：
 * 1. 库里永远存公斤，显示才换；
 * 2. 输入非法时返回 null，不能悄悄存 0 或 NaN；
 * 3. 斤和公斤换算来回要不丢精度。
 */
class WeightUnitTest {

    @Test
    fun `公斤显示公斤不变`() {
        assertEquals(68.5, WeightUnit.toDisplay(68.5, WeightUnit.KG), 0.0001)
        assertEquals("68.5", WeightUnit.format(68.5, WeightUnit.KG))
    }

    @Test
    fun `公斤转斤要乘二`() {
        assertEquals(137.0, WeightUnit.toDisplay(68.5, WeightUnit.JIN), 0.0001)
        assertEquals("137.0", WeightUnit.format(68.5, WeightUnit.JIN))
        assertEquals("137.0 斤", WeightUnit.formatWithSymbol(68.5, WeightUnit.JIN))
    }

    @Test
    fun `斤输入要除二存公斤`() {
        assertEquals(65.0, WeightUnit.toKg(130.0, WeightUnit.JIN), 0.0001)
        assertEquals(65.0, WeightUnit.parseToKg("130", WeightUnit.JIN) ?: 0.0, 0.0001)
    }

    @Test
    fun `单位符号对得上`() {
        assertEquals("kg", WeightUnit.symbol(WeightUnit.KG))
        assertEquals("斤", WeightUnit.symbol(WeightUnit.JIN))
        // 传了不认识的单位当公斤处理，不能崩
        assertEquals("kg", WeightUnit.symbol("lb"))
    }

    @Test
    fun `非法输入返回空`() {
        assertNull(WeightUnit.parseToKg("", WeightUnit.KG))
        assertNull(WeightUnit.parseToKg("abc", WeightUnit.KG))
        assertNull(WeightUnit.parseToKg("0", WeightUnit.KG))
        assertNull(WeightUnit.parseToKg("-5", WeightUnit.KG))
    }

    @Test
    fun `超出合理范围也返回空`() {
        // 10kg 太轻、600kg 太重，都是误输
        assertNull(WeightUnit.parseToKg("10", WeightUnit.KG))
        assertNull(WeightUnit.parseToKg("600", WeightUnit.KG))
        // 250 斤 = 125kg，合理
        assertEquals(125.0, WeightUnit.parseToKg("250", WeightUnit.JIN) ?: 0.0, 0.0001)
    }

    @Test
    fun `变化量文案带符号和单位`() {
        assertEquals("-0.5 kg", WeightUnit.changeText(-0.5, WeightUnit.KG))
        assertEquals("+0.3 kg", WeightUnit.changeText(0.3, WeightUnit.KG))
        assertEquals("-1.0 斤", WeightUnit.changeText(-0.5, WeightUnit.JIN))
    }
}
