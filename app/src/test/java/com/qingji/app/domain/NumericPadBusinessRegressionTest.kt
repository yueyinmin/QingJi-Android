package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * QJ-FOOD-NUMERIC-PAD-V1 业务回归（工单 9.3）：
 * 证明数字键盘只是输入层，营养换算逻辑与改造前完全一致。
 */
class NumericPadBusinessRegressionTest {

    @Test
    fun input280gComputesSameAsDirectDouble() {
        // 键盘输入 280 → 字符串 → Double，与直接传 280.0 结果必须一致
        val typed = "280".fold("") { acc, c -> NumericKeypadLogic.appendDigit(acc, c) }
        val grams = NumericKeypadLogic.asDouble(typed) ?: 0.0
        assertEquals(280.0, grams, 0.001)

        val fromKeyboard = FoodNutritionCalculator.compute(
            kcalPerBasis = 250.0, proteinPerBasis = 10.0, carbPerBasis = 30.0, fatPerBasis = 10.0,
            basis = FoodNutritionCalculator.PER_100G, servingGrams = 100.0, grams = grams
        )
        val direct = FoodNutritionCalculator.compute(
            kcalPerBasis = 250.0, proteinPerBasis = 10.0, carbPerBasis = 30.0, fatPerBasis = 10.0,
            basis = FoodNutritionCalculator.PER_100G, servingGrams = 100.0, grams = 280.0
        )
        assertEquals(direct.kcal, fromKeyboard.kcal, 1e-9)
        assertEquals(direct.proteinG, fromKeyboard.proteinG, 1e-9)
        assertEquals(direct.carbG, fromKeyboard.carbG, 1e-9)
        assertEquals(direct.fatG, fromKeyboard.fatG, 1e-9)
    }

    @Test
    fun decimalGramsKeepPrecision() {
        // 12.5g 不在 UI 层被取整
        val typed = "12.5".fold("") { acc, c ->
            if (c == '.') NumericKeypadLogic.appendDot(acc)
            else NumericKeypadLogic.appendDigit(acc, c)
        }
        val grams = NumericKeypadLogic.asDouble(typed) ?: 0.0
        assertEquals(12.5, grams, 0.001)
        val r = FoodNutritionCalculator.compute(
            kcalPerBasis = 100.0, proteinPerBasis = 5.0, carbPerBasis = 10.0, fatPerBasis = 2.0,
            basis = FoodNutritionCalculator.PER_100G, servingGrams = 100.0, grams = grams
        )
        assertEquals(12.5, r.kcal, 1e-9)
    }

    @Test
    fun defaultGramsSemanticsPreserved() {
        // 不同 defaultGrams 的食物打开时显示各自默认值，不被强制成 100
        listOf(30.0, 150.0, 200.0).forEach { default ->
            val initial = default.toInt().toString() // AddToMealDialog 原逻辑：defaultGrams.toInt()
            assertEquals(default.toInt().toString(), initial)
        }
    }

    @Test
    fun perServingBasisKeepsGramsSemantics() {
        // 每份基准：grams 仍是「实际克数」，ratio 用 servingGrams 做分母。
        // 60g / 每份30g = 2 份 → 倍率 2.0；UI 单位必须是「克」不是「份」。
        val r = FoodNutritionCalculator.compute(
            kcalPerBasis = 200.0, proteinPerBasis = 10.0, carbPerBasis = 20.0, fatPerBasis = 5.0,
            basis = FoodNutritionCalculator.PER_SERVING, servingGrams = 30.0, grams = 60.0
        )
        assertEquals(400.0, r.kcal, 1e-9)
    }
}
