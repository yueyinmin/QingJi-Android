package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QJ-WATER-SHEET-V2.1：快捷累加与数字编辑逻辑单测（工单 9.1 / 9.2 可测部分）。 */
class WaterQuickAddTest {

    @Test
    fun quickAddAccumulatesNotOverwrites() {
        assertEquals(300, WaterQuickAdd.add("200", 100))
        assertEquals(400, WaterQuickAdd.add("200", 200))
        assertEquals(500, WaterQuickAdd.add("200", 300))
        assertEquals(400, WaterQuickAdd.add("300", 100))
        assertEquals(800, WaterQuickAdd.add("500", 300))
    }

    @Test
    fun quickAddClampsToMax() {
        assertEquals(3000, WaterQuickAdd.add("2950", 100))
        assertEquals(3000, WaterQuickAdd.add("3000", 300))
    }

    @Test
    fun stepButtonClamps() {
        assertEquals(150, WaterQuickAdd.step(200, -50))
        assertEquals(250, WaterQuickAdd.step(200, 50))
        assertEquals(1, WaterQuickAdd.step(1, -50))
        assertEquals(3000, WaterQuickAdd.step(3000, 50))
    }

    @Test
    fun digitEditingIsIntegerOnly() {
        // decimals=0：数字正常追加，小数点被忽略（无小数）
        assertEquals("500", "500".fold("") { acc, c -> NumericKeypadLogic.appendDigit(acc, c, 0) })
        assertEquals("1000", "1000".fold("") { acc, c -> NumericKeypadLogic.appendDigit(acc, c, 0) })
        assertEquals("12", NumericKeypadLogic.appendDot("12", decimals = 0))
    }

    @Test
    fun backspaceAndEmptyAreSafe() {
        assertEquals("50", NumericKeypadLogic.backspace("500"))
        assertEquals("", NumericKeypadLogic.backspace(""))
        assertFalse(WaterQuickAdd.isValid(""))
    }

    @Test
    fun invalidNumbersCannotSubmit() {
        // 负数/小数/超范围/空值均不可提交
        assertFalse(WaterQuickAdd.isValid("-100"))
        assertFalse(WaterQuickAdd.isValid("12.5"))
        assertFalse(WaterQuickAdd.isValid("3001"))
        assertFalse(WaterQuickAdd.isValid("0"))
        assertTrue(WaterQuickAdd.isValid("1"))
        assertTrue(WaterQuickAdd.isValid("3000"))
    }

    @Test
    fun repeatedQuickAddKeepsAccumulating() {
        var pending = "200"
        repeat(3) { pending = WaterQuickAdd.add(pending, 100).toString() }
        assertEquals("500", pending)
    }
}
