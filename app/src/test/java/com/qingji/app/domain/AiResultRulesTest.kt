package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 识别结果清洗规则（v1.0.4 优化分支）。
 * 为什么不测 AiClient.parseResult：它用 org.json，JVM 单测里 Android 的
 * org.json 方法会抛 "not mocked"，本项目 Robolectric 又被下载卡死禁用。
 * 所以把数值规则抽成纯 Kotlin 放 domain，这里能真跑。
 */
class AiResultRulesTest {

    @Test
    fun `0克负数和缺失值回落到常规份量`() {
        assertEquals(100.0, AiResultRules.saneGrams(0.0), 0.001)
        assertEquals(100.0, AiResultRules.saneGrams(-20.0), 0.001)
        assertEquals(100.0, AiResultRules.saneGrams(0.4), 0.001)
    }

    @Test
    fun `NaN和Infinity不会漏进记录`() {
        assertEquals(100.0, AiResultRules.saneGrams(Double.NaN), 0.001)
        assertEquals(100.0, AiResultRules.saneGrams(Double.POSITIVE_INFINITY), 0.001)
        assertEquals(0.0, AiResultRules.saneKcal(Double.NaN, 100.0), 0.001)
        assertEquals(0.0, AiResultRules.saneMacro(Double.NEGATIVE_INFINITY), 0.001)
        assertEquals(0.0, AiResultRules.saneConfidence(Double.NaN), 0.001)
    }

    @Test
    fun `离谱大数按上限压回`() {
        assertEquals(3000.0, AiResultRules.saneGrams(99999.0), 0.001)
        assertEquals(1000.0, AiResultRules.saneMacro(50000.0), 0.001)
        // 9000 kcal / 100g → 每 100g 9000 千卡，压回 900 千卡上限
        assertEquals(900.0, AiResultRules.saneKcal(9000.0, 100.0), 0.001)
    }

    @Test
    fun `正常数值原样保留`() {
        assertEquals(150.0, AiResultRules.saneGrams(150.0), 0.001)
        assertEquals(174.0, AiResultRules.saneKcal(174.0, 150.0), 0.001)
        assertEquals(2.6, AiResultRules.saneMacro(2.6), 0.001)
        assertEquals(0.7, AiResultRules.saneConfidence(0.7), 0.001)
    }

    @Test
    fun `置信度钳到0到1`() {
        assertEquals(1.0, AiResultRules.saneConfidence(8.0), 0.001)
        assertEquals(0.0, AiResultRules.saneConfidence(-1.0), 0.001)
    }

    @Test
    fun `营养负数归零`() {
        assertEquals(0.0, AiResultRules.saneMacro(-3.5), 0.001)
        assertEquals(0.0, AiResultRules.saneKcal(-100.0, 150.0), 0.001)
    }

    @Test
    fun `空结果只有明确追问才合法`() {
        assertTrue(AiResultRules.isLegalEmptyResult(0, true))
        assertFalse("没说要追问却空着，不能当成功", AiResultRules.isLegalEmptyResult(0, false))
        assertFalse("给了条目但清洗后全废，也不算合法空结果", AiResultRules.isLegalEmptyResult(2, true))
    }
}
