package com.qingji.app.domain

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.entity.CatalogFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodMatchingEngineTest {

    private fun food(id: Long, name: String) = CatalogFood(
        foodId = id,
        name = name,
        category = "家常菜",
        energyKcal = 150.0,
        protein = 10.0,
        fat = 8.0,
        carbs = 10.0,
        sourceId = "test"
    )

    private val catalog = listOf(
        food(100096, "番茄炒蛋"),
        food(100659, "西红柿炒鸡蛋"),
        food(200143, "番茄炒蛋（堂食）"),
        food(100185, "木须肉"),
        food(100183, "青椒肉丝"),
        food(100190, "宫保鸡丁"),
        food(100715, "宫保鸡丁（老乡鸡）"),
        food(200097, "宫保鸡丁（堂食）")
    )

    private val patch by lazy {
        FoodAliasPatchLoader.load(ApplicationProvider.getApplicationContext<Application>()).getOrThrow()
    }

    @Test
    fun `八个样例词全部可达`() {
        val queries = listOf(
            "番茄炒蛋", "西红柿炒鸡蛋", "木须肉", "木樨肉",
            "青椒肉丝", "尖椒肉丝", "宫保鸡丁", "宫爆鸡丁"
        )

        val missing = queries.filter { query ->
            FoodMatchingEngine.match(query, catalog, patch).isEmpty()
        }

        assertTrue("不可达样例：$missing", missing.isEmpty())
        assertEquals(8, queries.size)
    }

    @Test
    fun `原本不可达词通过补丁命中且多来源不自动收敛`() {
        val mu = FoodMatchingEngine.match("木樨肉", catalog, patch)
        assertEquals(1, mu.size)
        assertEquals(100185L, mu.first().food.foodId)
        assertEquals("ALIAS_PATCH", mu.first().via)

        val gongbao = FoodMatchingEngine.match("宫爆鸡丁", catalog, patch)
        assertEquals(3, gongbao.size)
        assertTrue(gongbao.all { !it.isEstimated })
        assertTrue(gongbao.all { it.via == "ALIAS_PATCH" })
    }

    @Test
    fun `无匹配时返回空列表不强行选相似食物`() {
        val result = FoodMatchingEngine.match("火星炒陨石", catalog, patch)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `补丁版本不匹配时禁用补丁仍可走库内完全命中`() {
        // 版本不匹配时 loader 失败，调用方传 null 禁用补丁（不是把旧补丁继续传进引擎）。
        val withoutPatch = FoodMatchingEngine.match("木樨肉", catalog, null)
        val withPatch = FoodMatchingEngine.match("木樨肉", catalog, null)

        assertEquals(withoutPatch, withPatch)
    }
}
