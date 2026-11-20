package com.qingji.app.ui.screen.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 识别结果确认页的纯计算规则（AI 专项 P0）。
 * 这些函数虽然和 Compose 同文件，但不碰 Android / Compose runtime，
 * JVM 单测可以直接跑，专门盯住「份量换算」和「落库前最后一道闸门」。
 */
class RecordConfirmRulesTest {

    private fun base() = EditableItem(
        name = "米饭",
        grams = "100.0",
        kcal = "200.0",
        proteinG = "10.0",
        carbG = "20.0",
        fatG = "5.0",
        confidence = 0.9
    )

    private val day = LocalDate.of(2026, 11, 11)

    @Test
    fun `点2倍时五个数值一起换算`() {
        val r = base().withMultiplier(2.0)
        assertEquals("200.0", r.grams)
        assertEquals("400.0", r.kcal)
        assertEquals("20.0", r.proteinG)
        assertEquals("40.0", r.carbG)
        assertEquals("10.0", r.fatG)
    }

    @Test
    fun `连点两次2倍不会累积成4倍`() {
        val r = base().withMultiplier(2.0).withMultiplier(2.0)
        assertEquals("200.0", r.grams)
        assertEquals("400.0", r.kcal)
    }

    @Test
    fun `改克数到150g和点1点5倍结果完全一致`() {
        val byGrams = base().withGrams("150.0")
        val byMultiplier = base().withMultiplier(1.5)
        assertEquals(byGrams.grams, byMultiplier.grams)
        assertEquals(byGrams.kcal, byMultiplier.kcal)
        assertEquals(byGrams.proteinG, byMultiplier.proteinG)
        assertEquals(byGrams.carbG, byMultiplier.carbG)
        assertEquals(byGrams.fatG, byMultiplier.fatG)
    }

    @Test
    fun `用户手动修正过的数值不会被快捷倍数覆盖`() {
        // 旧实现：withMultiplier 永远用 AI 初始值绝对赋值，会把手动改成 250 的热量打回 200
        val corrected = base().copy(kcal = "250.0").rebase()
        val r = corrected.withMultiplier(2.0)
        assertEquals("500.0", r.kcal)
    }

    @Test
    fun `改克数后四个营养值同步变化`() {
        val r = base().withGrams("50.0")
        assertEquals("50.0", r.grams)
        assertEquals("100.0", r.kcal)
        assertEquals("5.0", r.proteinG)
        assertEquals("10.0", r.carbG)
        assertEquals("2.5", r.fatG)
    }

    @Test
    fun `verdict按当前数值实时重算`() {
        val suspect = base().copy(
            grams = "20.0", kcal = "180.0",
            proteinG = "2.0", carbG = "3.0", fatG = "1.0"  // 4-9-4 反推 29 kcal
        )
        assertFalse(listOf(suspect).verdict().canSave)
        val fixed = suspect.copy(kcal = "29.0")
        assertTrue(listOf(fixed).verdict().canSave)
    }

    @Test
    fun `自相矛盾的条目即使绕过界面也不会落库`() {
        val suspect = base().copy(
            grams = "20.0", kcal = "180.0",
            proteinG = "2.0", carbG = "3.0", fatG = "1.0"
        )
        assertTrue(listOf(suspect).toFoodLogs("早餐", "text", day).isEmpty())
        assertEquals(1, listOf(base()).toFoodLogs("早餐", "text", day).size)
    }

    @Test
    fun `落库时三大营养素取界面当前值`() {
        val log = listOf(base().withGrams("50.0")).toFoodLogs("早餐", "text", day).single()
        assertEquals(5.0, log.proteinG, 0.001)
        assertEquals(10.0, log.carbG, 0.001)
        assertEquals(2.5, log.fatG, 0.001)
    }
}
