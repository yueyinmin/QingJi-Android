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

    // ---------- 一致性校验（v1.0.4 phase1 补：三大营养素与热量互相对不上） ----------

    @Test
    fun `正常食物不会被误报`() {
        // 米饭 100g：116 kcal，2.6 / 25.9 / 0.3
        assertTrue(
            AiResultRules.auditNutrition(100.0, 116.0, 2.6, 25.9, 0.3).ok
        )
        // 番茄牛肉盖浇饭 450g：620 kcal，24 / 78 / 20（4-9-4 反推 588，差 32，在容差内）
        assertTrue(
            AiResultRules.auditNutrition(450.0, 620.0, 24.0, 78.0, 20.0).ok
        )
        // 纯食用油 10g：90 kcal，全脂肪
        assertTrue(AiResultRules.auditNutrition(10.0, 90.0, 0.0, 0.0, 10.0).ok)
        // 纯碳水 100g 白糖：400 kcal
        assertTrue(AiResultRules.auditNutrition(100.0, 400.0, 0.0, 100.0, 0.0).ok)
    }

    @Test
    fun `小份量和粗纤维蔬菜不会因为误差被误报`() {
        // 5g 姜：20 kcal，0.1 / 4.5 / 0.1 —— 绝对差值只有 0.7 kcal
        assertTrue(AiResultRules.auditNutrition(5.0, 20.0, 0.1, 4.5, 0.1).ok)
        // 芹菜 100g：16 kcal，但 4-9-4 反推 19.3（膳食纤维没算进去），差 3.3 也不该报
        assertTrue(AiResultRules.auditNutrition(100.0, 16.0, 0.8, 3.9, 0.1).ok)
    }

    @Test
    fun `营养素加起来比食物本身还重必须标记`() {
        // 100 克食物里装着 500 克蛋白 —— 每个字段都「合法」，凑一起就是假的
        val audit = AiResultRules.auditNutrition(100.0, 500.0, 500.0, 0.0, 0.0)
        assertFalse(audit.ok)
        assertTrue("要给出人话原因", audit.note.contains("还多"))

        // 蛋白 + 碳水 + 脂肪一起超重也要抓到
        assertFalse(AiResultRules.auditNutrition(100.0, 800.0, 60.0, 90.0, 40.0).ok)
    }

    @Test
    fun `热量与三大营养素反推值差太远必须标记`() {
        // 100g 食物：三大营养素反推 340 kcal，却只写了 50 kcal
        val audit = AiResultRules.auditNutrition(100.0, 50.0, 20.0, 20.0, 20.0)
        assertFalse(audit.ok)
        assertTrue("原因里要带上两个数字", audit.note.contains("kcal"))

        // 有营养素却把热量写成 0，同样要标记（不能当成「0 卡食物」存下去）
        assertFalse(AiResultRules.auditNutrition(100.0, 0.0, 10.0, 20.0, 5.0).ok)
    }

    @Test
    fun `模型没给营养素时不做无根据的报警`() {
        // 只给了热量、三大营养素全是 0：很常见（模型偷懒），无从比对，不算异常
        assertTrue(AiResultRules.auditNutrition(100.0, 500.0, 0.0, 0.0, 0.0).ok)
        assertTrue(AiResultRules.auditNutrition(250.0, 180.0, 0.0, 0.0, 0.0).ok)
    }

    @Test
    fun `克数非法时直接放行由清洗层兜底`() {
        // 克数无效时比例无从谈起，交给 saneGrams 处理，这里不重复报警
        assertTrue(AiResultRules.auditNutrition(0.0, 500.0, 100.0, 100.0, 100.0).ok)
        assertTrue(AiResultRules.auditNutrition(Double.NaN, 500.0, 100.0, 100.0, 100.0).ok)
    }

    @Test
    fun `脏数据不会让校验本身崩掉`() {
        assertTrue(AiResultRules.auditNutrition(100.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN).ok)
        assertTrue(AiResultRules.auditNutrition(100.0, 116.0, -5.0, -5.0, -5.0).ok)
    }

    @Test
    fun `标记用的置信度要低于界面提示线`() {
        // RecordConfirm 在 confidence < 0.6 时亮红字「请重点核对」，
        // 这个常量必须真的低于 0.6，否则标记了用户也看不见。
        assertTrue(AiResultRules.SUSPECT_CONFIDENCE < 0.6)
    }

    @Test
    fun `4比9比4反推热量`() {
        assertEquals(588.0, AiResultRules.estimatedKcal(24.0, 78.0, 20.0), 0.001)
        assertEquals(0.0, AiResultRules.estimatedKcal(0.0, 0.0, 0.0), 0.001)
    }
}
