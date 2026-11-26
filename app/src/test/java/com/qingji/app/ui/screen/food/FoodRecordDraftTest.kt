package com.qingji.app.ui.screen.food

import com.qingji.app.domain.FoodNutritionCalculator
import com.qingji.app.domain.MealRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * QJ-RECORD-INPUT-UNIFIED-V3 / B4：食物记录草稿单测。
 * 草稿是「返回不改日期时间、不丢输入中文本」的载体，
 * 这里只测纯数据部分（不启动 Compose）。
 */
class FoodRecordDraftTest {

    private fun draft(gramsText: String = "150") = FoodRecordDraft.create(
        foodName = "米饭",
        basis = FoodNutritionCalculator.PER_100G,
        defaultGrams = 150.0,
        servingGrams = 100.0,
        kcalPerBasis = 200.0,
        proteinPerBasis = 4.0,
        carbPerBasis = 40.0,
        fatPerBasis = 1.0,
        meal = MealRules.LUNCH
    ).copy(gramsText = gramsText)

    @Test
    fun createNormalizesDefaultGramsAndBasis() {
        val d = draft()
        assertEquals("150", d.gramsText)
        assertEquals(150.0, d.originalGrams, 0.001)
        assertEquals(FoodNutritionCalculator.PER_100G, d.basis)
        assertEquals(MealRules.LUNCH, d.meal)
    }

    @Test
    fun previewUsesSameNutritionFunctionAsSave() {
        assertEquals(300.0, draft().previewKcal(), 0.001)
        assertEquals(200.0, draft("100").previewKcal(), 0.001)
    }

    @Test
    fun blankOrOutOfRangeGramsCannotSubmit() {
        assertFalse(draft("").gramsValid)
        assertFalse(draft("0").gramsValid)
        assertFalse(draft("3001").gramsValid)
        assertTrue(draft("1").gramsValid)
        assertTrue(draft("3000").gramsValid)
    }

    @Test
    fun dateAndTimeComeFromPrimitiveFields() {
        val day = LocalDate.of(2026, 11, 20)
        val d = draft().copy(epochDay = day.toEpochDay(), hour = 12, minute = 35)
        assertEquals(day, d.date)
        assertEquals(LocalTime.of(12, 35), d.time)
    }

    @Test
    fun submittedDraftCannotBeSavedTwice() {
        assertTrue(draft().canSubmit)
        assertFalse(draft().copy(submitted = true).canSubmit)
    }
}
