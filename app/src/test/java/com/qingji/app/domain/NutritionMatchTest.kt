package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地食物库校正的单元测试。
 * 重点验证：名字匹配别乱配、克数兜底别越界、换算别算错。
 */
class NutritionMatchTest {

    private val rice = Per100g(kcal = 116.0, protein = 2.6, carb = 25.9, fat = 0.3)
    private val candidates = listOf(
        "米饭" to rice,
        "鸡蛋" to Per100g(144.0, 13.3, 2.8, 8.8),
        "西瓜" to Per100g(31.0, 0.6, 7.0, 0.2)
    )

    @Test
    fun 名字完全一样得满分() {
        assertEquals(100, NutritionMatch.score("米饭", "米饭"))
    }

    @Test
    fun 带修饰词也能认出是同一种食物() {
        // 模型常输出「白米饭」「米饭（熟）」，本地库只有「米饭」
        assertTrue(NutritionMatch.score("白米饭", "米饭") >= 80)
        assertTrue(NutritionMatch.score("米饭（熟）", "米饭") >= 60)
    }

    @Test
    fun 不相干的食物不能乱配() {
        // 「西红柿」和「西瓜」只有「西」一个字重合，不该当成同一种
        assertTrue(NutritionMatch.score("西红柿", "西瓜") < 55)
    }

    @Test
    fun 能挑出最像的一条() {
        val hit = NutritionMatch.pick("白米饭", candidates)
        assertNotNull(hit)
        assertEquals("米饭", hit!!.dbName)
        assertEquals(116.0, hit.per100g.kcal, 0.01)
    }

    @Test
    fun 库里没有就返回空() {
        assertNull(NutritionMatch.pick("佛跳墙", candidates))
    }

    @Test
    fun 按克数换算营养值() {
        val per = NutritionMatch.compute(rice, 150.0)
        assertEquals(174.0, per.kcal, 0.05)      // 116 × 1.5
        assertEquals(3.9, per.protein, 0.05)     // 2.6 × 1.5
        assertEquals(38.9, per.carb, 0.05)       // 25.9 × 1.5 = 38.85 → 38.9
        assertEquals(0.5, per.fat, 0.05)         // 0.3 × 1.5 = 0.45 → 0.5
    }

    @Test
    fun 克数为零时营养值归零() {
        val per = NutritionMatch.compute(rice, 0.0)
        assertEquals(0.0, per.kcal, 0.001)
    }

    @Test
    fun 离谱克数会被夹到合理范围() {
        assertEquals(3000.0, NutritionMatch.saneGrams(99999.0), 0.001)
        assertEquals(100.0, NutritionMatch.saneGrams(0.0), 0.001)
        assertEquals(100.0, NutritionMatch.saneGrams(-5.0), 0.001)
        assertEquals(150.0, NutritionMatch.saneGrams(150.4), 0.001)
    }

    @Test
    fun 每百克热量超过九百会被压回来() {
        // 100 克食物给 2000 千卡，明显算飞了（纯油才 900）
        assertEquals(900.0, NutritionMatch.saneKcal(2000.0, 100.0), 0.01)
        // 正常值原样保留
        assertEquals(174.0, NutritionMatch.saneKcal(174.0, 150.0), 0.01)
    }
}
