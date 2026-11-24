package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 卡路里换算单测。 */
class CalorieConverterTest {

    @Test
    fun convertsGramsAndKjToKcal() {
        // 100g × 2000kJ/100g ÷ 4.184 ≈ 478.0 kcal
        assertEquals(478.0, CalorieConverter.kcal(100.0, 2000.0) ?: 0.0, 0.1)
    }

    @Test
    fun formulaMatchesScreenshotExample() {
        // 截图：重量 100g、千焦待填；这里验证 100g × 1000kJ/100g = 239.0 kcal
        assertEquals(239.0, CalorieConverter.kcal(100.0, 1000.0) ?: 0.0, 0.1)
    }

    @Test
    fun invalidInputsReturnNull() {
        assertNull(CalorieConverter.kcal(0.0, 1000.0))
        assertNull(CalorieConverter.kcal(100.0, 0.0))
        assertNull(CalorieConverter.kcal(-5.0, 1000.0))
    }

    @Test
    fun formatDropsTrailingZero() {
        assertEquals("478", CalorieConverter.format(478.0))
        assertEquals("478.5", CalorieConverter.format(478.46))
    }
}
