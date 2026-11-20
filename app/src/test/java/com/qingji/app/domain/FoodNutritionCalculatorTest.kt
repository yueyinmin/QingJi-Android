package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5 阶段二：统一营养折算工具测试。
 * 重点覆盖工单验收项：蛋白粉40g、三种计量基准、4-9-4 只提示不覆盖、null 语义。
 */
class FoodNutritionCalculatorTest {

    /** 工单 3.3：蛋白粉每100g=379kcal，实际吃40g → 151.6kcal，四宏量同一倍率 0.4 */
    @Test
    fun `蛋白粉每100g吃40g按同一基准折算`() {
        val r = FoodNutritionCalculator.compute(
            kcalPerBasis = 379.0,
            proteinPerBasis = 80.0,
            carbPerBasis = 5.0,
            fatPerBasis = 5.0,
            basis = FoodNutritionCalculator.PER_100G,
            servingGrams = 100.0,
            grams = 40.0
        )
        assertEquals(151.6, r.kcal, 1e-9)
        assertEquals(32.0, r.proteinG, 1e-9)
        assertEquals(2.0, r.carbG, 1e-9)
        assertEquals(2.0, r.fatG, 1e-9)
    }

    /** 每份基准：每份30g，吃了60g → 倍率 2.0 */
    @Test
    fun `每份基准按每份克数折算`() {
        val r = FoodNutritionCalculator.compute(
            kcalPerBasis = 200.0,
            proteinPerBasis = 10.0,
            carbPerBasis = 20.0,
            fatPerBasis = 5.0,
            basis = FoodNutritionCalculator.PER_SERVING,
            servingGrams = 30.0,
            grams = 60.0
        )
        assertEquals(400.0, r.kcal, 1e-9)
        assertEquals(20.0, r.proteinG, 1e-9)
    }

    /** 每100ml：喝200ml → 倍率 2.0 */
    @Test
    fun `每100ml基准按毫升折算`() {
        val r = FoodNutritionCalculator.compute(
            kcalPerBasis = 50.0,
            proteinPerBasis = 3.0,
            carbPerBasis = 6.0,
            fatPerBasis = 1.0,
            basis = FoodNutritionCalculator.PER_100ML,
            servingGrams = 100.0,
            grams = 200.0
        )
        assertEquals(100.0, r.kcal, 1e-9)
        assertEquals(6.0, r.proteinG, 1e-9)
    }

    /** 非法克数/每份克数 → 倍率 0，不产生 NaN/负数 */
    @Test
    fun `非法份量倍率为零`() {
        assertEquals(0.0, FoodNutritionCalculator.ratio(FoodNutritionCalculator.PER_100G, 100.0, -5.0), 1e-9)
        assertEquals(0.0, FoodNutritionCalculator.ratio(FoodNutritionCalculator.PER_SERVING, 0.0, 60.0), 1e-9)
        assertEquals(0.0, FoodNutritionCalculator.ratio(FoodNutritionCalculator.PER_100G, 100.0, Double.NaN), 1e-9)
    }

    /** 微量营养素 null 折后仍是 null（未收录不等于 0） */
    @Test
    fun `未收录微量营养素保持null`() {
        val r = FoodNutritionCalculator.compute(
            kcalPerBasis = 100.0, proteinPerBasis = 5.0, carbPerBasis = 10.0, fatPerBasis = 2.0,
            basis = FoodNutritionCalculator.PER_100G, servingGrams = 100.0, grams = 50.0,
            fiberPerBasis = null, sodiumPerBasis = 400.0
        )
        assertNull(r.fiberG)
        assertEquals(200.0, r.sodiumMg!!, 1e-9)
    }

    /** 4-9-4：明显不匹配时给提示；一致时静默；只提示不覆盖（工具本身不返回改写值） */
    @Test
    fun `热量与宏量不匹配给提示但不改写`() {
        // 蛋白40/碳水5/脂肪5 → 估算 40*4+5*4+5*9 = 225kcal；填 379 明显超
        val note = FoodNutritionCalculator.consistencyNote(
            kcal = 379.0, protein = 40.0, carb = 5.0, fat = 5.0
        )
        assertTrue(note != null && note!!.contains("匹配"))

        // 一致：p10 c20 f5 → 40+80+45=165，与填的 165 一致
        assertNull(FoodNutritionCalculator.consistencyNote(165.0, 10.0, 20.0, 5.0))

        // 三宏量全 0（没填）→ 无从校验，不提示
        assertNull(FoodNutritionCalculator.consistencyNote(379.0, 0.0, 0.0, 0.0))
    }

    /** 基准字符串归一化：非法值回落 PER_100G */
    @Test
    fun `基准非法回落到每100g`() {
        assertEquals(FoodNutritionCalculator.PER_100G, FoodNutritionCalculator.normalizeBasis("garbage"))
        assertEquals(FoodNutritionCalculator.PER_100G, FoodNutritionCalculator.normalizeBasis(null))
        assertEquals(FoodNutritionCalculator.PER_100ML, FoodNutritionCalculator.normalizeBasis("PER_100ML"))
    }

    /** v1.5 阶段三：反向折算——一餐吃到151.6kcal/32g蛋白来自40g蛋白粉，还原每100g=379/80 */
    @Test
    fun `反向折算还原每100g营养`() {
        val per = FoodNutritionCalculator.toPerBasis(
            consumedKcal = 151.6, consumedProtein = 32.0,
            consumedCarb = 2.0, consumedFat = 2.0, grams = 40.0
        )
        assertNotNull(per)
        assertEquals(379.0, per!!.kcal, 0.2)
        assertEquals(80.0, per.protein, 0.2)
    }

    /** 反向折算：非法克数返回 null（调用方跳过） */
    @Test
    fun `反向折算非法克数返回空`() {
        assertNull(FoodNutritionCalculator.toPerBasis(100.0, 10.0, 10.0, 5.0, 0.0))
        assertNull(FoodNutritionCalculator.toPerBasis(100.0, 10.0, 10.0, 5.0, -5.0))
    }
}
