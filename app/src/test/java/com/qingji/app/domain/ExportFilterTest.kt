package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** v3.4.0：CSV 导出「按月 / 按餐次」筛选规则的边界。 */
class ExportFilterTest {

    private val nov = YearMonth.of(2026, 11)
    private val d1 = LocalDate.of(2026, 11, 1)
    private val d2 = LocalDate.of(2026, 11, 30)
    private val dec = LocalDate.of(2026, 12, 1)

    @Test
    fun noFilterKeepsEverything() {
        assertTrue(ExportFilter.matches(dec, MealRules.SNACK, null, emptySet()))
        assertTrue(ExportFilter.matches(d1, MealRules.BREAKFAST, null, emptySet()))
    }

    @Test
    fun monthFilterKeepsOnlyThatMonthIncludingBoundaryDays() {
        assertTrue(ExportFilter.matches(d1, MealRules.LUNCH, nov, emptySet()))
        assertTrue(ExportFilter.matches(d2, MealRules.LUNCH, nov, emptySet()))
        assertFalse(ExportFilter.matches(dec, MealRules.LUNCH, nov, emptySet()))
        assertFalse(ExportFilter.matches(LocalDate.of(2026, 10, 31), MealRules.LUNCH, nov, emptySet()))
    }

    @Test
    fun mealFilterKeepsOnlySelectedMeals() {
        val meals = setOf(MealRules.BREAKFAST, MealRules.DINNER)
        assertTrue(ExportFilter.matches(d1, MealRules.BREAKFAST, null, meals))
        assertTrue(ExportFilter.matches(d1, MealRules.DINNER, null, meals))
        assertFalse(ExportFilter.matches(d1, MealRules.LUNCH, null, meals))
        assertFalse(ExportFilter.matches(d1, MealRules.SNACK, null, meals))
    }

    @Test
    fun twoFiltersAreCombinedWithAnd() {
        val meals = setOf(MealRules.LUNCH)
        assertTrue(ExportFilter.matches(d1, MealRules.LUNCH, nov, meals))
        assertFalse(ExportFilter.matches(dec, MealRules.LUNCH, nov, meals))
        assertFalse(ExportFilter.matches(d1, MealRules.BREAKFAST, nov, meals))
    }

    @Test
    fun describeExplainsCurrentScope() {
        assertEquals("全部时间 · 全部餐次", ExportFilter.describe(null, emptySet()))
        assertEquals("2026 年 11 月 · 全部餐次", ExportFilter.describe(nov, emptySet()))
        assertEquals(
            "全部时间 · 早餐、晚餐",
            ExportFilter.describe(null, setOf(MealRules.BREAKFAST, MealRules.DINNER))
        )
    }

    @Test
    fun describeOrdersMealsInFixedOrder() {
        // 集合本身无序，但描述里必须按早/午/晚/加餐的固定顺序输出
        assertEquals(
            "全部时间 · 早餐、加餐",
            ExportFilter.describe(null, setOf(MealRules.SNACK, MealRules.BREAKFAST))
        )
    }

    @Test
    fun filePrefixKeepsOldNameWhenUnfiltered() {
        assertEquals("food-logs", ExportFilter.filePrefix(null))
        assertEquals("food-logs-2026-11", ExportFilter.filePrefix(nov))
        assertEquals("food-logs-2026-01", ExportFilter.filePrefix(YearMonth.of(2026, 1)))
    }
}
