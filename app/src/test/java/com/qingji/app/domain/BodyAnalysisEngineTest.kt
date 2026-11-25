package com.qingji.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H 阶段规则层测试：体重趋势 / BMI / 体脂 / 围度 / 目标进度。
 * 重点验证三件事：数字算得对、缺数据不瞎猜、未来记录不参与。
 */
class BodyAnalysisEngineTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val profile = BodyProfile(
        heightCm = 175.0,
        startWeightKg = 90.0,
        targetWeightKg = 75.0,
        proteinGoalG = 120
    )

    @Test
    fun `连续四周下降时给出方向和每周速度`() {
        val weights = listOf(
            WeightPoint(LocalDate.of(2026, 11, 5), 86.0),
            WeightPoint(LocalDate.of(2026, 11, 12), 85.0),
            WeightPoint(LocalDate.of(2026, 11, 19), 84.0),
            WeightPoint(LocalDate.of(2026, 11, 25), 83.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(LocalDate.of(2026, 11, 25), result.weight.latestDate)
        assertEquals(83.0, result.weight.latestKg!!, 0.001)
        // 7 天前最近一条是 11-12（11-19 比 11-18 晚，不算）
        assertEquals(-2.0, result.weight.change7dKg!!, 0.001)
        // 28 天前窗口里没有记录，宁可空着
        assertNull(result.weight.change28dKg)
        assertEquals(WeightDirection.LOSING, result.weight.direction)
        assertEquals(-1.05, result.weight.weeklyRateKg!!, 0.001)
        assertEquals(83.5, result.weight.average7dKg!!, 0.001)
        assertEquals(BodyTrust.CALCULATED, result.weight.trust)
        assertFalse(result.insufficientData)
    }

    @Test
    fun `只有一条记录时趋势不可用并给出提醒`() {
        val weights = listOf(WeightPoint(today, 80.0))

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(WeightDirection.UNKNOWN, result.weight.direction)
        assertNull(result.weight.weeklyRateKg)
        assertNull(result.weight.change7dKg)
        assertTrue(result.insufficientData)
        assertTrue(result.warnings.any { it.contains("体重记录不足") })
    }

    @Test
    fun `没有任何记录时所有分区都是 MISSING`() {
        val result = BodyAnalysisEngine.analyze(emptyList(), emptyList(), profile, today, 28)

        assertEquals(BodyTrust.MISSING, result.weight.trust)
        assertEquals(BodyTrust.MISSING, result.bmi.trust)
        assertEquals(BodyTrust.MISSING, result.bodyFat.trust)
        assertEquals(BodyTrust.MISSING, result.measures.trust)
        assertEquals(BodyTrust.MISSING, result.goal.trust)
        assertNull(result.bmi.value)
        assertTrue(result.insufficientData)
        assertTrue(result.warnings.any { it.contains("还没有任何体重记录") })
    }

    @Test
    fun `BMI 用本地公式算并给出分类`() {
        val weights = listOf(WeightPoint(today, 83.0))

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        // 83 / 1.75^2 = 27.1 -> 超重
        assertEquals(27.1, result.bmi.value!!, 0.01)
        assertEquals("超重", result.bmi.category)
        assertEquals(BodyTrust.CALCULATED, result.bmi.trust)
    }

    @Test
    fun `身高缺失时 BMI 不算并且不猜`() {
        val weights = listOf(WeightPoint(today, 83.0))
        val noHeight = profile.copy(heightCm = 0.0)

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), noHeight, today, 28)

        assertNull(result.bmi.value)
        assertEquals(BodyTrust.MISSING, result.bmi.trust)
        assertTrue(result.warnings.any { it.contains("BMI 算不出来") })
    }

    @Test
    fun `体脂缺失只标 MISSING 不拿体重去推`() {
        val weights = listOf(
            WeightPoint(LocalDate.of(2026, 11, 1), 85.0),
            WeightPoint(today, 83.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(BodyTrust.MISSING, result.bodyFat.trust)
        assertNull(result.bodyFat.latestPct)
        assertTrue(result.warnings.any { it.contains("体脂率") })
    }

    @Test
    fun `体脂变化按十四天前的记录比较`() {
        val weights = listOf(
            WeightPoint(LocalDate.of(2026, 11, 1), 85.0, 23.0),
            WeightPoint(LocalDate.of(2026, 11, 10), 84.0, 22.5),
            WeightPoint(today, 83.0, 21.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(21.0, result.bodyFat.latestPct!!, 0.001)
        // 14 天前是 11-11，最近一条不晚于它的是 11-10 的 22.5
        assertEquals(-1.5, result.bodyFat.change14dPct!!, 0.001)
        assertEquals(BodyTrust.RECORDED, result.bodyFat.trust)
    }

    @Test
    fun `围度间隔不足七天不做比较`() {
        val weights = listOf(WeightPoint(today, 83.0))
        val measures = listOf(
            MeasurePoint(LocalDate.of(2026, 11, 20), waistCm = 88.0),
            MeasurePoint(LocalDate.of(2026, 11, 24), waistCm = 87.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, measures, profile, today, 28)

        assertEquals(87.0, result.measures.latest!!.waistCm!!, 0.001)
        assertEquals(4, result.measures.waistSpanDays)
        assertNull(result.measures.waistChangeCm)
        assertTrue(result.warnings.any { it.contains("暂不比较腰围变化") })
    }

    @Test
    fun `围度间隔够长时给出腰围变化`() {
        val weights = listOf(WeightPoint(today, 83.0))
        val measures = listOf(
            MeasurePoint(LocalDate.of(2026, 11, 1), waistCm = 90.0),
            MeasurePoint(LocalDate.of(2026, 11, 22), waistCm = 87.5)
        )

        val result = BodyAnalysisEngine.analyze(weights, measures, profile, today, 28)

        assertEquals(21, result.measures.waistSpanDays)
        assertEquals(-2.5, result.measures.waistChangeCm!!, 0.001)
    }

    @Test
    fun `目标进度和预计周数按当前速度估算`() {
        val weights = listOf(
            WeightPoint(LocalDate.of(2026, 11, 5), 86.0),
            WeightPoint(LocalDate.of(2026, 11, 12), 85.0),
            WeightPoint(LocalDate.of(2026, 11, 19), 84.0),
            WeightPoint(today, 83.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(8.0, result.goal.remainingKg!!, 0.001)
        // 起始 90 → 现在 83，目标是 75，共 15kg，已完成 7/15
        assertEquals(46.7, result.goal.progressPct!!, 0.05)
        // (75-83) / -1.05 ≈ 7.6 周
        assertEquals(7.6, result.goal.estimatedWeeks!!, 0.05)
        assertEquals(BodyTrust.CALCULATED, result.goal.trust)
    }

    @Test
    fun `目标体重没填时不估算进度`() {
        val weights = listOf(WeightPoint(today, 83.0))
        val noTarget = profile.copy(targetWeightKg = 0.0)

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), noTarget, today, 28)

        assertNull(result.goal.remainingKg)
        assertNull(result.goal.progressPct)
        assertEquals(BodyTrust.MISSING, result.goal.trust)
        assertTrue(result.warnings.any { it.contains("没有目标体重") })
    }

    @Test
    fun `未来日期的体重不参与统计`() {
        val weights = listOf(
            WeightPoint(LocalDate.of(2026, 11, 5), 86.0),
            WeightPoint(LocalDate.of(2026, 11, 20), 84.0),
            WeightPoint(today, 83.0),
            WeightPoint(LocalDate.of(2026, 11, 28), 70.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(83.0, result.weight.latestKg!!, 0.001)
        assertEquals(WeightDirection.LOSING, result.weight.direction)
        assertEquals(3, result.completeness.weightDays)
    }

    @Test
    fun `异常体重被排除并提示`() {
        val weights = listOf(
            WeightPoint(LocalDate.of(2026, 11, 20), 84.0),
            WeightPoint(today, 999.0)
        )

        val result = BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

        assertEquals(84.0, result.weight.latestKg!!, 0.001)
        assertTrue(result.warnings.any { it.contains("数值异常") })
    }
}
