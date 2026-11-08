package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 食物图标分类：别把肉认成菜、别把面认成米。 */
class FoodIconTest {

    @Test
    fun 主食认成饭() {
        assertEquals("🍚", FoodIcon.emoji("米饭"))
        assertEquals("🍚", FoodIcon.emoji("蛋炒饭"))
    }

    @Test
    fun 面优先于肉() {
        // 「牛肉面」既有「牛」又有「面」，按规则应该算面
        assertEquals("🍜", FoodIcon.emoji("牛肉面"))
        assertEquals("🍜", FoodIcon.emoji("米线"))
    }

    @Test
    fun 鸡蛋认成蛋() {
        assertEquals("🥚", FoodIcon.emoji("水煮鸡蛋"))
    }

    @Test
    fun 水果各归各的() {
        assertEquals("🍉", FoodIcon.emoji("西瓜"))
        assertEquals("🍎", FoodIcon.emoji("苹果"))
        assertEquals("🍌", FoodIcon.emoji("香蕉"))
    }

    @Test
    fun 认不出来给通用餐盘() {
        assertEquals("🍽", FoodIcon.emoji("佛跳墙"))
        assertEquals("🍽", FoodIcon.emoji(""))
    }
}

/** 重新识别的口味规则，拼出来的文字要能让模型看懂。 */
class MealRefineTest {

    @Test
    fun 少油会给出具体克数() {
        val text = RefineOptions(oil = RefineOptions.OIL_LESS).toPrompt()
        assertTrue(text.contains("食用油按 3g 计"))
    }

    @Test
    fun 重油按饭店做法() {
        val text = RefineOptions(oil = RefineOptions.OIL_HEAVY).toPrompt()
        assertTrue(text.contains("25g"))
        assertTrue(text.contains("饭店"))
    }

    @Test
    fun 多糖是常规两倍() {
        val text = RefineOptions(sugar = RefineOptions.SUGAR_MORE).toPrompt()
        assertTrue(text.contains("两倍"))
    }

    @Test
    fun 加酱料要计入热量() {
        val text = RefineOptions(sauce = true).toPrompt()
        assertTrue(text.contains("酱料"))
        assertTrue(text.contains("15g"))
    }

    @Test
    fun 生食要单独说明() {
        val text = RefineOptions(raw = true).toPrompt()
        assertTrue(text.contains("生食"))
    }

    @Test
    fun 用户补充说明会带上() {
        val text = RefineOptions(note = "米饭只吃了半碗").toPrompt()
        assertTrue(text.contains("米饭只吃了半碗"))
    }

    @Test
    fun 不写说明就不出现空行() {
        val text = RefineOptions().toPrompt()
        assertTrue(!text.contains("用户补充说明"))
    }
}
