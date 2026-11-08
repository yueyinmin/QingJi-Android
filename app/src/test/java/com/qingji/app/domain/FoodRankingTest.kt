package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 常吃食物置顶：计数正确、排序正确、没吃过的保持原顺序。 */
class FoodRankingTest {

    private data class F(val name: String)

    @Test
    fun `统计最近出现次数`() {
        val counts = FoodRanking.countRecent(listOf("米饭", "鸡蛋", "米饭", " 米饭 ", "", "鸡蛋"))
        assertEquals(3, counts["米饭"])
        assertEquals(2, counts["鸡蛋"])
        assertEquals(false, counts.containsKey(""))
    }

    @Test
    fun `常吃的排前面`() {
        val items = listOf(F("苹果"), F("米饭"), F("鸡蛋"))
        val counts = mapOf("米饭" to 10, "鸡蛋" to 3)
        val ranked = FoodRanking.rank(items, { it.name }, counts)
        assertEquals("米饭", ranked[0].name)
        assertEquals("鸡蛋", ranked[1].name)
        assertEquals("苹果", ranked[2].name)
    }

    @Test
    fun `次数相同保持原顺序`() {
        val items = listOf(F("A"), F("B"), F("C"))
        val ranked = FoodRanking.rank(items, { it.name }, emptyMap())
        assertEquals(listOf("A", "B", "C"), ranked.map { it.name })
    }
}
