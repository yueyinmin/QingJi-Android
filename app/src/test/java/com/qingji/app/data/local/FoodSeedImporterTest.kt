package com.qingji.app.data.local

import com.qingji.app.domain.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 食物库 654 条导入清单的数据验收（v3.4.0 · P0 R1）。
 * 这些断言等价于「导入前必须满足的数据契约」，CSV 一改就能立刻发现。
 */
class FoodSeedImporterTest {

    private val foods = FoodSeedImporter.parse()

    @Test
    fun parsesAllRowsAndNoDuplicateKeys() {
        assertEquals(654, foods.size)
        assertEquals(654, foods.distinctBy { it.name to it.brand }.size)
    }

    @Test
    fun everyPrimaryCategoryHasContent() {
        FoodCategory.ALL.forEach { cat ->
            assertTrue("分类「$cat」导入后没有内容", foods.any { it.category == cat })
        }
        foods.forEach { food ->
            assertTrue(
                "「${food.name}」的分类 ${food.category} 不在 13 个合法分类里",
                food.category in FoodCategory.ALL
            )
        }
    }

    @Test
    fun snacksAreAStandaloneCategoryOf120() {
        val snacks = foods.filter { it.category == FoodCategory.SNACK }
        assertEquals(120, snacks.size)
        assertTrue(snacks.any { it.name.contains("薯片") })
        assertTrue(snacks.any { it.name.contains("冰淇淋") })
    }

    @Test
    fun homeDishesCoverCommonChineseDishes() {
        val dishes = foods.filter { it.category == FoodCategory.HOME_DISH }
        assertEquals(190, dishes.size)
        assertTrue(dishes.any { it.name.contains("地锅") })
        assertTrue(dishes.any { it.name.contains("番茄炒蛋") })
    }

    @Test
    fun pinyinSearchWorksAndSourceBrandAreClean() {
        assertTrue(foods.any { it.pinyin.startsWith("sp") && it.name.contains("薯片") })
        assertTrue(foods.any { it.pinyin.startsWith("dg") && it.name.contains("地锅") })
        foods.forEach {
            assertEquals("preset", it.source)
            assertEquals("", it.brand)
        }
    }

    @Test
    fun nutritionValuesAreInReasonableRange() {
        foods.forEach { food ->
            assertTrue("${food.name} 热量异常", food.kcalPer100g >= 0 && food.kcalPer100g <= 950)
            assertTrue("${food.name} 默认份量异常", food.defaultGrams > 0)
            assertTrue("${food.name} 蛋白异常", food.proteinPer100g >= 0)
            assertTrue("${food.name} 碳水异常", food.carbPer100g >= 0)
            assertTrue("${food.name} 脂肪异常", food.fatPer100g >= 0)
        }
    }

    @Test
    fun fastFoodIsSplitByCategoryNotBrand() {
        val fast = foods.filter { it.category == FoodCategory.FAST_FOOD }
        assertEquals(33, fast.size)
        assertTrue(fast.all { it.brand.isBlank() })
    }
}
