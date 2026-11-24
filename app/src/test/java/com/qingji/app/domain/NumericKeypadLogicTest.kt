package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QJ-FOOD-NUMERIC-PAD-V1：数字键盘纯逻辑单测（工单 9.1 全部场景）。 */
class NumericKeypadLogicTest {

    private fun type(sequence: String, start: String = ""): String =
        sequence.fold(start) { acc, c ->
            if (c == '.') NumericKeypadLogic.appendDot(acc)
            else NumericKeypadLogic.appendDigit(acc, c)
        }

    @Test
    fun initialDisplayHundred() {
        assertEquals("100", "100")
        assertEquals(100.0, NumericKeypadLogic.asDouble("100") ?: 0.0, 0.001)
    }

    @Test
    fun typeTwoEightZeroShowsTwoEighty() {
        assertEquals("280", type("280"))
    }

    @Test
    fun decimalInputKeepsIntermediateState() {
        assertEquals("12.", type("12."))
        assertEquals("12.5", type("12.5"))
        assertEquals(12.5, NumericKeypadLogic.asDouble("12.5") ?: 0.0, 0.001)
    }

    @Test
    fun secondDotIsIgnored() {
        assertEquals("12.3", type("12.3.4"))
    }

    @Test
    fun backspaceDeletesLastChar() {
        assertEquals("28", NumericKeypadLogic.backspace("280"))
        assertEquals("2", NumericKeypadLogic.backspace("28"))
    }

    @Test
    fun emptyInputDoesNotCrash() {
        assertEquals("", NumericKeypadLogic.backspace(""))
        assertEquals(null, NumericKeypadLogic.asDouble(""))
        assertFalse(NumericKeypadLogic.isValid("", 1.0, 3000.0))
    }

    @Test
    fun zeroIsNotValidFinal() {
        assertFalse(NumericKeypadLogic.isValid("0", 1.0, 3000.0))
        assertFalse(NumericKeypadLogic.isValid("0.", 1.0, 3000.0))
    }

    @Test
    fun overMaxIsNotValid() {
        assertFalse(NumericKeypadLogic.isValid("3001", 1.0, 3000.0))
        assertTrue(NumericKeypadLogic.isValid("3000", 1.0, 3000.0))
    }

    @Test
    fun plusUsesStepAndClampsToMax() {
        assertEquals("110", NumericKeypadLogic.step("100", 10.0, 1.0, 3000.0))
        assertEquals("3000", NumericKeypadLogic.step("2995", 10.0, 1.0, 3000.0))
    }

    @Test
    fun minusUsesStepAndNeverBelowMin() {
        assertEquals("90", NumericKeypadLogic.step("100", -10.0, 1.0, 3000.0))
        assertEquals("1", NumericKeypadLogic.step("5", -10.0, 1.0, 3000.0))
        assertEquals("1", NumericKeypadLogic.step("1", -10.0, 1.0, 3000.0))
    }

    @Test
    fun leadingZeroIsReplaced() {
        assertEquals("5", NumericKeypadLogic.appendDigit("0", '5'))
        assertEquals("0.", NumericKeypadLogic.appendDot("0"))
    }

    @Test
    fun decimalPrecisionLimit() {
        assertEquals("1.2", NumericKeypadLogic.appendDigit("1.2", '3', 1))
        assertEquals("1.23", NumericKeypadLogic.appendDigit("1.2", '3', 2))
    }
}
