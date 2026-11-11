package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 识别结果的保存闸门（AI 专项 P0）。
 * 重点证明：自相矛盾的数据不能保存，改到自洽后立刻能保存，
 * 判定用的是「界面当前数值」，不是识别那一刻的旧快照。
 */
class AiSaveGateTest {

    private fun okItem() = AiItemSnapshot(
        name = "米饭", grams = 100.0, kcal = 200.0,
        proteinG = 10.0, carbG = 20.0, fatG = 5.0
    )

    /** 只有热量对不上（营养素总重 6g 没有超 20g），用来单独测 4-9-4 分支。 */
    private fun kcalMismatch() = AiItemSnapshot(
        name = "饼干", grams = 20.0, kcal = 180.0,
        proteinG = 2.0, carbG = 3.0, fatG = 1.0   // 反推 29 kcal
    )

    @Test
    fun `数据自洽时允许保存`() {
        val verdict = AiSaveGate.check(listOf(okItem()))
        assertTrue(verdict.canSave)
        assertTrue(verdict.issues.isEmpty())
        assertEquals("", verdict.summary)
    }

    @Test
    fun `热量和三大营养素对不上时阻止保存`() {
        // 20g / 180kcal，但三大营养素反推只有 29kcal —— 典型的热量虚高
        val suspect = kcalMismatch()
        val verdict = AiSaveGate.check(listOf(suspect))
        assertFalse(verdict.canSave)
        assertEquals(0, verdict.issues[0].index)
        assertTrue(verdict.issues[0].reason.contains("热量"))
        assertTrue(verdict.summary.contains("第 1 条"))
    }

    @Test
    fun `三大营养素总重超过食物重量时阻止保存`() {
        val suspect = okItem().copy(grams = 20.0, proteinG = 30.0, carbG = 10.0, fatG = 5.0)
        val verdict = AiSaveGate.check(listOf(suspect))
        assertFalse(verdict.canSave)
        assertTrue(verdict.issues[0].reason.contains("三大营养素"))
    }

    @Test
    fun `用户把数值改到自洽后立刻放行`() {
        val suspect = kcalMismatch()
        assertFalse(AiSaveGate.check(listOf(suspect)).canSave)
        // 把热量改成和 4-9-4 反推值一致，相当于用户在确认页手动修正
        val fixed = suspect.copy(kcal = 29.0)
        assertTrue(AiSaveGate.check(listOf(fixed)).canSave)
    }

    @Test
    fun `输入框空着负数零克空名字都拦下来`() {
        val cases = listOf(
            okItem().copy(grams = null),
            okItem().copy(kcal = null),
            okItem().copy(proteinG = null),
            okItem().copy(grams = 0.0),
            okItem().copy(kcal = -1.0),
            okItem().copy(fatG = -1.0),
            okItem().copy(name = "  ")
        )
        cases.forEach { assertFalse("应拦截：$it", AiSaveGate.check(listOf(it)).canSave) }
    }

    @Test
    fun `AI没给营养素时只提示不误杀`() {
        // auditNutrition 里 estimated<=0 直接放行，闸门必须沿用同一口径
        val noMacro = okItem().copy(proteinG = 0.0, carbG = 0.0, fatG = 0.0)
        assertTrue(AiSaveGate.check(listOf(noMacro)).canSave)
    }

    @Test
    fun `多条里只有一条异常时定位到正确下标`() {
        val suspect = kcalMismatch()
        val verdict = AiSaveGate.check(listOf(okItem(), suspect, okItem()))
        assertFalse(verdict.canSave)
        assertEquals(listOf(1), verdict.issues.map { it.index })
    }

    @Test
    fun `多条异常时汇总文案不刷屏`() {
        val suspect = kcalMismatch()
        val verdict = AiSaveGate.check(listOf(suspect, suspect))
        assertEquals(2, verdict.issues.size)
        assertTrue(verdict.summary.contains("2 条"))
        assertFalse(verdict.summary.contains("⚠"))
    }
}
