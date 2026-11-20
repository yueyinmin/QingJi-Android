package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 断食时间计算：最容易错的是跨零点。 */
class FastingRulesTest {

    private fun status(now: String, plan: Int = 16, start: Int = 460, end: Int = 940) =
        FastingRules.status(LocalTime.parse(now), plan, start, end, enabled = true)

    @Test
    fun 晚上在断食区间() {
        // 15:40 结束进食，20:00 时已断 4 小时 20 分
        val s = status("20:00")
        assertTrue(s.active)
        assertEquals(260, s.elapsedMinutes)
    }

    @Test
    fun 进食窗口内不算断食() {
        // 10:00 在 07:40~15:40 之内
        val s = status("10:00")
        assertFalse(s.active)
    }

    @Test
    fun 凌晨跨零点要算对() {
        // 03:00 时，断食从昨天 15:40 开始：8h20m + 3h = 11h20m
        val s = status("03:00")
        assertTrue(s.active)
        assertEquals(11 * 60 + 20, s.elapsedMinutes)
    }

    @Test
    fun 剩余时间不会算成负数() {
        val s = status("15:39")
        assertTrue(s.remainMinutes >= 0)
    }

    @Test
    fun 关闭时不返回断食中() {
        val s = FastingRules.status(LocalTime.parse("20:00"), 16, 460, 940, enabled = false)
        assertFalse(s.active)
    }

    @Test
    fun 时间标签格式正确() {
        assertEquals("07:40", FastingRules.minuteLabel(460))
        assertEquals("15:40", FastingRules.minuteLabel(940))
        assertEquals("09:32", FastingRules.durationLabel(572))
    }

    @Test
    fun 进度在零到一之间() {
        val s = status("20:00")
        val p = FastingRules.progress(s)
        assertTrue(p >= 0f && p <= 1f)
    }

    @Test
    fun 阶段文案随小时数变化() {
        assertEquals("消化吸收", FastingRules.phase(2.0).first)
        assertEquals("脂肪动员", FastingRules.phase(10.0).first)
        assertEquals("深度断食", FastingRules.phase(17.0).first)
    }
}

/** 过敏原判定：宁可多标，不能漏标。 */
class AllergenRulesTest {

    @Test
    fun 花生命中() {
        assertTrue(AllergenRules.isRisky("花生米", listOf(AllergenRules.PEANUT)))
    }

    @Test
    fun 海鲜不误伤蔬菜() {
        assertFalse(AllergenRules.isRisky("西红柿", listOf(AllergenRules.SEAFOOD)))
        assertTrue(AllergenRules.isRisky("清蒸鲈鱼", listOf(AllergenRules.SEAFOOD)))
    }

    @Test
    fun 麸质命中面食() {
        assertTrue(AllergenRules.isRisky("白面馒头", listOf(AllergenRules.GLUTEN)))
    }

    @Test
    fun 没选过敏原就什么都不拦() {
        assertFalse(AllergenRules.isRisky("花生米", emptyList()))
    }

    @Test
    fun 拼给模型的规则带过敏原与忌口() {
        val text = AllergenRules.toPrompt(listOf("花生", "海鲜"), listOf("香菜"))
        assertTrue(text.contains("花生"))
        assertTrue(text.contains("海鲜"))
        assertTrue(text.contains("香菜"))
    }

    @Test
    fun 什么都没选时返回空串() {
        assertEquals("", AllergenRules.toPrompt(emptyList(), emptyList()))
    }
}

/** 食物自动归类：顺序敏感的地方最容易错。 */
class FoodCategoryTest {

    @Test
    fun 面条类归到面食() {
        assertEquals(FoodCategory.NOODLE, FoodCategory.guess("牛肉面"))
        assertEquals(FoodCategory.NOODLE, FoodCategory.guess("小笼包"))
    }

    @Test
    fun 米饭归到主食() {
        assertEquals(FoodCategory.STAPLE, FoodCategory.guess("蛋炒饭"))
    }

    @Test
    fun 有品牌就归快餐() {
        assertEquals(FoodCategory.FAST_FOOD, FoodCategory.guess("香辣鸡腿堡", "肯德基"))
    }

    @Test
    fun 水果蔬菜分开() {
        assertEquals(FoodCategory.FRUIT, FoodCategory.guess("苹果"))
        assertEquals(FoodCategory.VEGETABLE, FoodCategory.guess("西兰花"))
    }

    @Test
    fun 认不出来给其他() {
        assertEquals(FoodCategory.OTHER, FoodCategory.guess(""))
    }
}
