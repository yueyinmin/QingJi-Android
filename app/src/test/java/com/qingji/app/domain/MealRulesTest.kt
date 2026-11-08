package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 餐次常量与显示名，四种记录方式共用同一套值。 */
class MealRulesTest {

    @Test
    fun `四餐显示名`() {
        assertEquals("早餐", MealRules.label(MealRules.BREAKFAST))
        assertEquals("午餐", MealRules.label(MealRules.LUNCH))
        assertEquals("晚餐", MealRules.label(MealRules.DINNER))
        assertEquals("加餐", MealRules.label(MealRules.SNACK))
    }

    @Test
    fun `只认四个合法值`() {
        MealRules.ALL.forEach { assertTrue(MealRules.isValid(it)) }
        assertFalse(MealRules.isValid("lunch "))
        assertFalse(MealRules.isValid(""))
        assertFalse(MealRules.isValid("BRUNCH"))
    }

    @Test
    fun `猜餐次一定落在四个合法值里`() {
        assertTrue(MealRules.isValid(MealRules.guess()))
    }

    @Test
    fun `未知餐次兜底成加餐`() {
        assertEquals("加餐", MealRules.label("unknown"))
    }
}
