package com.qingji.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拆库后的「两路数据合并成同一个 Food」验收（v1.0.4）。
 *
 * 这些函数不碰 Android Context，所以能在电脑上直接跑，
 * 用来守住三条最容易出错的规矩：
 *  ① 标准库 id 从 100001 起，用户库 id 从 1 起，永不撞号；
 *  ② 标准库的字段（别名 / 全拼 / 来源 / 审核状态）要完整带到界面，
 *     并且「未收录」的 null 必须原样带到界面，不能被悄悄变成 0；
 *  ③ 界面上的 Food 写回用户库时，绝不能把标准库的 id 或 preset 来源带进去。
 */
class FoodMappingTest {

    private fun catalog() = CatalogFood(
        foodId = 100001L,
        name = "米饭（熟）",
        aliases = "米饭",
        pinyin = "mf",
        pinyinFull = "mifan",
        category = "主食",
        subcategory = "米饭",
        energyKcal = 116.0,
        protein = 2.6,
        fat = 0.3,
        carbs = 25.9,
        basisGrams = 100.0,
        defaultGrams = 150.0,
        portionNote = "1 碗",
        sourceId = "CNFCT-APPROX",
        sourceNote = "《中国食物成分表》近似值",
        auditStatus = 0,
        catalogVersion = "0.3"
    )

    @Test
    fun catalogCarriesAllNewFieldsIntoUnifiedFood() {
        val food = catalog().toFood(favorite = true)
        assertEquals(100001L, food.id)
        assertEquals("米饭（熟）", food.name)
        assertEquals("米饭", food.aliases)
        assertEquals("mifan", food.pinyinFull)
        assertEquals("米饭", food.subcategory)
        assertEquals("1 碗", food.portionNote)
        assertEquals("CNFCT-APPROX", food.sourceId)
        assertEquals(0, food.auditStatus)
        assertEquals("0.3", food.catalogVersion)
        assertEquals("preset", food.source)
        assertTrue(food.favorite)
    }

    @Test
    fun missingMicronutrientsStayNullInsteadOfBecomingZero() {
        // 清单里本来就没有纤维/钠/糖/钙这四列数据，落库是 NULL。
        // 如果这里变成 0.0，界面就会把「未收录」渲染成「0 克」，等于编数据。
        val food = catalog().toFood()
        assertEquals(null, food.fiberPer100g)
        assertEquals(null, food.sodiumMgPer100g)
        assertEquals(null, food.sugarPer100g)
        assertEquals(null, food.calciumMgPer100g)
    }

    @Test
    fun realZeroStaysZeroSoItStaysDistinguishableFromMissing() {
        // 以后真录进来一个「确实不含纤维」的食物时，0 必须还是 0，不能和 NULL 混为一谈
        val food = CatalogFood(
            foodId = 100999L, name = "测试用零值食物", category = "其他",
            energyKcal = 10.0, protein = 0.0, fat = 0.0, carbs = 0.0,
            fiber = 0.0, sodium = 0.0, sugar = 0.0, calcium = 0.0,
            sourceId = "CNFCT-APPROX"
        ).toFood()
        assertEquals(0.0, food.fiberPer100g!!, 0.0001)
        assertEquals(0.0, food.calciumMgPer100g!!, 0.0001)
    }

    @Test
    fun nutritionColumnsAreMappedInTheRightOrder() {
        // CSV 的列顺序是 protein / fat / carbs，别和旧表的 protein / carbs / fat 弄反
        val food = catalog().toFood()
        assertEquals(116.0, food.kcalPer100g, 0.0001)
        assertEquals(2.6, food.proteinPer100g, 0.0001)
        assertEquals(0.3, food.fatPer100g, 0.0001)
        assertEquals(25.9, food.carbPer100g, 0.0001)
        assertEquals(150.0, food.defaultGrams, 0.0001)
    }

    @Test
    fun catalogAndUserIdsNeverCollide() {
        val fromCatalog = catalog().toFood()
        val fromUser = UserFood(id = 1, name = "自定义", kcalPer100g = 100.0).toFood()
        assertTrue(fromCatalog.id >= 100001L)
        assertTrue(fromUser.id < 100001L)
        assertTrue(fromCatalog.id != fromUser.id)
    }

    @Test
    fun userFoodKeepsItsOwnSourceAndVersion() {
        val food = UserFood(
            id = 7, name = "妈妈牌红烧肉", kcalPer100g = 350.0,
            source = "user", category = "家常菜"
        ).toFood()
        assertEquals(7L, food.id)
        assertEquals("user", food.source)
        assertEquals("家常菜", food.category)
        assertEquals("", food.catalogVersion)
    }

    @Test
    fun writingBackNeverPutsCatalogIdOrPresetSourceIntoUserDb() {
        val back = catalog().toFood().toUserFood()
        assertEquals(0L, back.id)                    // 交给 Room 自增重新分配
        assertEquals("user", back.source)           // 不冒充标准库
        assertTrue(back.createdAt > 0L)             // 标准库那条是 0，写回要补时间戳
    }

    @Test
    fun writingBackAUserFoodKeepsItsId() {
        val original = UserFood(id = 7, name = "自定义", kcalPer100g = 100.0)
        val back = original.toFood().toUserFood()
        assertEquals(7L, back.id)
        assertEquals("自定义", back.name)
    }

    // ---------- 迁移 8：四列可空语义 ----------

    @Test
    fun newUserFoodUsesNullForNotRecorded() {
        val food = UserFood(id = 1, name = "自定义", kcalPer100g = 100.0).toFood()
        assertNull(food.fiberPer100g)
        assertNull(food.sodiumMgPer100g)
        assertNull(food.sugarPer100g)
        assertNull(food.calciumMgPer100g)
    }

    @Test
    fun zeroAndNullAreDifferentAfterRoundTrip() {
        // 0.0 = 确实测得为 0；null = 未收录。以前 toUserFood 会把 null 降级成 0.0，两者混为一谈。
        val back = Food(
            name = "自定义", kcalPer100g = 100.0,
            fiberPer100g = 0.0, sodiumMgPer100g = null,
            sugarPer100g = 1.5, calciumMgPer100g = null
        ).toUserFood()
        assertEquals(0.0, back.fiberPer100g!!, 0.0001)
        assertNull(back.sodiumMgPer100g)
        assertEquals(1.5, back.sugarPer100g!!, 0.0001)
        assertNull(back.calciumMgPer100g)
    }

    @Test
    fun sugarSurvivesUserFoodRoundTrip() {
        val food = UserFood(
            id = 2, name = "含糖食物", kcalPer100g = 200.0, sugarPer100g = 12.0
        ).toFood()
        assertEquals(12.0, food.sugarPer100g!!, 0.0001)
        assertEquals(12.0, food.toUserFood().sugarPer100g!!, 0.0001)
    }
}
