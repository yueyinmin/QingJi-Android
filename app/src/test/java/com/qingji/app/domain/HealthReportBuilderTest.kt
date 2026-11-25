package com.qingji.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H 阶段综合报告测试：身体数据 + 训练 + 饮食三块拼在一起时的口径。
 * 重点验证：平均值只按有记录的天算、缺数据要标出来、蛋白达标天数算得对。
 */
class HealthReportBuilderTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val profile = BodyProfile(
        heightCm = 175.0,
        startWeightKg = 90.0,
        targetWeightKg = 75.0,
        proteinGoalG = 100
    )

    private fun bodyAnalysis(weights: List<WeightPoint> = defaultWeights()): BodyAnalysis =
        BodyAnalysisEngine.analyze(weights, emptyList(), profile, today, 28)

    private fun defaultWeights(): List<WeightPoint> = listOf(
        WeightPoint(LocalDate.of(2026, 11, 5), 86.0),
        WeightPoint(LocalDate.of(2026, 11, 12), 85.0),
        WeightPoint(LocalDate.of(2026, 11, 19), 84.0),
        WeightPoint(today, 83.0)
    )

    private fun typeSummary(type: ExerciseRecordType, sessions: Int): TypeSummary = TypeSummary(
        type = type,
        sessions = sessions,
        totalSets = sessions * 12,
        volumeKg = sessions * 1200.0,
        durationSec = sessions * 3000L,
        averageRpe = if (sessions > 0) 7.5 else null,
        sessionsPerWeek = sessions / 4.0,
        minGapDays = if (sessions > 1) 2 else null,
        trust = if (sessions > 0) TrustLevel.FACT else TrustLevel.UNKNOWN
    )

    private fun training(strength: Int, cardio: Int, hiit: Int): TrainingAnalysis =
        TrainingAnalysis(
            windowDays = 28,
            totals = mapOf(
                ExerciseRecordType.STRENGTH to typeSummary(ExerciseRecordType.STRENGTH, strength),
                ExerciseRecordType.CARDIO to typeSummary(ExerciseRecordType.CARDIO, cardio),
                ExerciseRecordType.HIIT to typeSummary(ExerciseRecordType.HIIT, hiit)
            ),
            insufficientData = false,
            warnings = emptyList()
        )

    private fun logs(vararg items: Triple<Int, Double, Double>): List<FoodLogPoint> = items.map {
        FoodLogPoint(LocalDate.of(2026, 11, it.first), it.second, it.third)
    }

    @Test
    fun `三块数据齐全时汇总出体重训练和饮食`() {
        val report = HealthReportBuilder.build(
            body = bodyAnalysis(),
            training = training(strength = 4, cardio = 2, hiit = 0),
            foodLogs = logs(
                Triple(20, 1800.0, 60.0),
                Triple(21, 2000.0, 100.0),
                Triple(22, 1600.0, 80.0)
            ),
            proteinGoalG = 100,
            today = today,
            windowDays = 28
        )

        assertEquals(3, report.nutrition.loggedDays)
        assertEquals(1800, report.nutrition.averageKcal)
        assertEquals(80, report.nutrition.averageProteinG)
        assertEquals(1, report.nutrition.daysMeetingProtein)
        assertTrue(report.summary.any { it.contains("同期训练 6 次") })
        assertTrue(report.summary.any { it.contains("蛋白达标 1 天") })
        assertTrue(report.summary.any { it.contains("BMI 27.1") })
        assertFalse(report.insufficientData)
    }

    @Test
    fun `平均热量只按有记录的天算`() {
        val report = HealthReportBuilder.build(
            body = bodyAnalysis(),
            training = training(1, 0, 0),
            foodLogs = logs(Triple(20, 2000.0, 90.0), Triple(24, 1600.0, 70.0)),
            proteinGoalG = 100,
            today = today,
            windowDays = 28
        )

        assertEquals(2, report.nutrition.loggedDays)
        // 不能用 28 天做分母，否则会算成 128 kcal
        assertEquals(1800, report.nutrition.averageKcal)
        assertTrue(report.insufficientData)
        assertTrue(report.warnings.any { it.contains("饮食记录") })
    }

    @Test
    fun `没有饮食记录时营养分区为 MISSING`() {
        val report = HealthReportBuilder.build(
            body = bodyAnalysis(),
            training = training(2, 0, 0),
            foodLogs = emptyList(),
            proteinGoalG = 100,
            today = today,
            windowDays = 28
        )

        assertEquals(BodyTrust.MISSING, report.nutrition.trust)
        assertEquals(0, report.nutrition.loggedDays)
        assertEquals(null, report.nutrition.averageKcal)
        assertTrue(report.warnings.any { it.contains("饮食记录") })
    }

    @Test
    fun `没设蛋白目标时不判断达标天数`() {
        val report = HealthReportBuilder.build(
            body = bodyAnalysis(),
            training = training(2, 0, 0),
            foodLogs = logs(Triple(20, 1800.0, 120.0), Triple(21, 1900.0, 130.0), Triple(22, 1700.0, 110.0)),
            proteinGoalG = 0,
            today = today,
            windowDays = 28
        )

        assertEquals(0, report.nutrition.daysMeetingProtein)
        assertTrue(report.warnings.any { it.contains("没有设置每日蛋白目标") })
        assertTrue(report.summary.any { it.contains("未设蛋白目标") })
    }

    @Test
    fun `窗口外的饮食记录不进统计`() {
        val report = HealthReportBuilder.build(
            body = bodyAnalysis(),
            training = training(2, 0, 0),
            foodLogs = listOf(
                FoodLogPoint(LocalDate.of(2026, 9, 1), 3000.0, 50.0),
                FoodLogPoint(LocalDate.of(2026, 11, 24), 1800.0, 90.0)
            ),
            proteinGoalG = 100,
            today = today,
            windowDays = 28
        )

        assertEquals(1, report.nutrition.loggedDays)
        assertEquals(1800, report.nutrition.averageKcal)
    }
}
