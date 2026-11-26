package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** QJ-RECORD-INPUT-UNIFIED-V3 / B5：日级饮食分组与汇总纯逻辑单测。 */
class FoodDayGroupingTest {

    private val day = LocalDate.of(2026, 11, 26)

    private fun log(meal: String, kcal: Double, createdAt: Long) = com.qingji.app.data.local.entity.FoodLog(
        date = day,
        mealType = meal,
        foodName = "测试食物",
        grams = 100.0,
        kcal = kcal,
        proteinG = 10.0,
        carbG = 20.0,
        fatG = 5.0,
        createdAt = createdAt
    )

    @Test
    fun groupKeepsFourMealsInFixedOrder() {
        val grouped = FoodDayGrouping.group(listOf(log(MealRules.SNACK, 100.0, 3L)))
        assertEquals(MealRules.ALL, grouped.keys.toList())
        assertTrue(grouped[MealRules.BREAKFAST].isNullOrEmpty())
        assertEquals(1, grouped[MealRules.SNACK]?.size)
    }

    @Test
    fun groupSortsSameMealByCreatedAt() {
        val first = log(MealRules.LUNCH, 100.0, 10L)
        val second = log(MealRules.LUNCH, 200.0, 20L)
        val grouped = FoodDayGrouping.group(listOf(second, first))
        assertEquals(listOf(first, second), grouped[MealRules.LUNCH])
    }

    @Test
    fun totalsAddExistingFieldsWithoutRecomputing() {
        val totals = FoodDayGrouping.totals(listOf(log(MealRules.BREAKFAST, 300.0, 1L)))
        assertEquals(300.0, totals.kcal, 0.001)
        assertEquals(10.0, totals.protein, 0.001)
        assertEquals(20.0, totals.carb, 0.001)
        assertEquals(5.0, totals.fat, 0.001)
        assertEquals(1, totals.itemCount)
    }

    @Test
    fun emptyDayDoesNotProduceNaNRatio() {
        assertEquals(1.0, FoodDayNutrition().macroKcal, 0.001)
    }
}
