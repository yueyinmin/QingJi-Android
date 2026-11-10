package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 热量公式、安全下限、中国标准 BMI 分级。 */
class NutritionCalculatorTest {

    @Test
    fun `Mifflin-StJeor 男性 BMR`() {
        // 10*92 + 6.25*175 - 5*25 + 5 = 1893.75
        val goal = NutritionCalculator.compute("male", 25, 175.0, 92.0, 2, NutritionCalculator.MODE_LOSE)
        assertEquals(1893, goal.bmr)
        assertEquals(2603, goal.tdee)      // 1893.75 * 1.375
    }

    @Test
    fun `女性 BMR 比男性少166`() {
        val male = NutritionCalculator.compute("male", 25, 175.0, 92.0, 2, NutritionCalculator.MODE_KEEP)
        val female = NutritionCalculator.compute("female", 25, 175.0, 92.0, 2, NutritionCalculator.MODE_KEEP)
        assertEquals(166, male.bmr - female.bmr)
    }

    @Test
    fun `减重比维持少约500千卡`() {
        val keep = NutritionCalculator.compute("male", 25, 175.0, 92.0, 2, NutritionCalculator.MODE_KEEP)
        val lose = NutritionCalculator.compute("male", 25, 175.0, 92.0, 2, NutritionCalculator.MODE_LOSE)
        assertEquals(500, keep.kcal - lose.kcal)
    }

    @Test
    fun `再低也不会跌破安全下限`() {
        val goal = NutritionCalculator.compute("male", 60, 150.0, 40.0, 1, NutritionCalculator.MODE_LOSE)
        assertTrue("男性不应低于 1500 千卡", goal.kcal >= 1500)
        val female = NutritionCalculator.compute("female", 60, 150.0, 40.0, 1, NutritionCalculator.MODE_LOSE)
        assertTrue("女性不应低于 1200 千卡", female.kcal >= 1200)
    }

    @Test
    fun `宏量目标都是非负数`() {
        val goal = NutritionCalculator.compute("female", 60, 150.0, 40.0, 1, NutritionCalculator.MODE_LOSE)
        assertTrue(goal.proteinG >= 0)
        assertTrue(goal.carbG >= 0)
        assertTrue(goal.fatG >= 0)
    }

    @Test
    fun `BMI 用中国标准分级`() {
        assertEquals("偏瘦", NutritionCalculator.bmiCategory(17.0))
        assertEquals("正常", NutritionCalculator.bmiCategory(18.5))
        assertEquals("正常", NutritionCalculator.bmiCategory(23.9))
        assertEquals("超重", NutritionCalculator.bmiCategory(24.0))
        assertEquals("超重", NutritionCalculator.bmiCategory(27.9))
        assertEquals("肥胖", NutritionCalculator.bmiCategory(28.0))
    }

    @Test
    fun `BMI 数值按身高体重算`() {
        // 70 / (1.75^2) = 22.857
        assertEquals(22.86, NutritionCalculator.bmi(70.0, 175.0), 0.01)
        assertEquals(0.0, NutritionCalculator.bmi(70.0, 0.0), 0.001)
    }

    @Test
    fun `活动量文案覆盖四档`() {
        assertTrue(NutritionCalculator.activityLabel(1).contains("久坐"))
        assertTrue(NutritionCalculator.activityLabel(4).contains("高度"))
        assertTrue(NutritionCalculator.activityLabel(99).contains("轻度"))
    }

    // ---------- 优化分支 v1.0.4：超重蛋白估算 + 输入边界 ----------

    @Test
    fun `超重人群蛋白目标不再按实际体重线性放大`() {
        // 120kg / 175cm → BMI ≈ 39.2（肥胖）
        // 参考体重 = max(24 × 1.75² = 73.5, 120 × 0.75 = 90) = 90 → 1.6 × 90 = 144
        val obese = NutritionCalculator.compute(
            "male", 30, 175.0, 120.0, 2, NutritionCalculator.MODE_LOSE
        )
        assertEquals(144, obese.proteinG)
        assertTrue("不该按 120kg 算出 192g", obese.proteinG < (1.6 * 120.0).toInt())
    }

    @Test
    fun `正常体重蛋白目标保持原样`() {
        val normal = NutritionCalculator.compute(
            "male", 30, 175.0, 70.0, 2, NutritionCalculator.MODE_KEEP
        )
        assertEquals((1.6 * 70.0).toInt(), normal.proteinG)
    }

    @Test
    fun `非有限与离谱输入不会算出NaN或负数`() {
        val cases = listOf(
            NutritionCalculator.compute(
                "male", 25, Double.NaN, Double.POSITIVE_INFINITY, 2,
                NutritionCalculator.MODE_LOSE
            ),
            NutritionCalculator.compute("female", -5, -170.0, -1.0, 99, "unknown"),
            NutritionCalculator.compute(
                "male", 200, 9999.0, 99999.0, 0, NutritionCalculator.MODE_GAIN
            )
        )
        cases.forEach { g ->
            assertTrue("BMR 必须为正", g.bmr > 0)
            assertTrue("TDEE 必须为正", g.tdee > 0)
            assertTrue("热量目标必须为正", g.kcal > 0)
            assertTrue(g.proteinG >= 0)
            assertTrue(g.carbG >= 0)
            assertTrue(g.fatG >= 0)
        }
    }

    @Test
    fun `热量下限公式保持不变`() {
        // 这次优化只补输入边界，不换下限公式
        val male = NutritionCalculator.compute(
            "male", 60, 150.0, 40.0, 1, NutritionCalculator.MODE_LOSE
        )
        val female = NutritionCalculator.compute(
            "female", 60, 150.0, 40.0, 1, NutritionCalculator.MODE_LOSE
        )
        assertEquals(1500, male.kcal)
        assertEquals(1200, female.kcal)
    }

    @Test
    fun `BMI 输入非法时返回0`() {
        assertEquals(0.0, NutritionCalculator.bmi(Double.NaN, 175.0), 0.001)
        assertEquals(0.0, NutritionCalculator.bmi(70.0, Double.POSITIVE_INFINITY), 0.001)
        assertEquals(0.0, NutritionCalculator.bmi(-1.0, 175.0), 0.001)
    }
}
