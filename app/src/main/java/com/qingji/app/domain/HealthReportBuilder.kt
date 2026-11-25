package com.qingji.app.domain

import java.time.LocalDate

/** 一条饮食记录的最小子集：只用来算记录完整度和蛋白达标天数。 */
data class FoodLogPoint(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double
)

data class NutritionCoverage(
    val windowDays: Int,
    /** 窗口内有饮食记录的天数 */
    val loggedDays: Int,
    /** 只按有记录的天求平均，缺记录的天不按 0 算 */
    val averageKcal: Int?,
    val averageProteinG: Int?,
    val proteinGoalG: Int,
    /** 蛋白达到目标的天数；目标没设就是 0 且 trust=MISSING */
    val daysMeetingProtein: Int,
    val trust: BodyTrust
)

/**
 * 综合健康报告：身体数据 + 训练 + 饮食记录，全部本地拼装。
 *
 * 只做“把三块事实放在一起”，不下医学结论、不给建议。
 * 模型拿到这份 JSON 后负责解释，解释必须标明 AI_INFERRED，建议标 SUGGESTION。
 */
data class HealthReport(
    val generatedFor: LocalDate,
    val windowDays: Int,
    val body: BodyAnalysis,
    val training: TrainingAnalysis,
    val nutrition: NutritionCoverage,
    val summary: List<String>,
    val warnings: List<String>,
    val insufficientData: Boolean
)

object HealthReportBuilder {

    /** 少于这个天数的饮食记录，平均热量没有意义 */
    const val MIN_NUTRITION_DAYS = 3

    fun build(
        body: BodyAnalysis,
        training: TrainingAnalysis,
        foodLogs: List<FoodLogPoint>,
        proteinGoalG: Int,
        today: LocalDate,
        windowDays: Int = 28
    ): HealthReport {
        val days = windowDays.coerceIn(7, 180)
        val from = today.minusDays(days - 1L)
        val logs = foodLogs.filter { it.date in from..today && it.kcal >= 0 }

        val byDate = logs.groupBy { it.date }
        val loggedDays = byDate.size
        val averageKcal = if (loggedDays > 0) {
            (logs.sumOf { it.kcal } / loggedDays).toInt()
        } else null
        val averageProtein = if (loggedDays > 0) {
            (logs.sumOf { it.proteinG } / loggedDays).toInt()
        } else null
        val daysMeetingProtein = if (proteinGoalG > 0) {
            byDate.count { (_, items) -> items.sumOf { it.proteinG } >= proteinGoalG }
        } else 0

        val nutrition = NutritionCoverage(
            windowDays = days,
            loggedDays = loggedDays,
            averageKcal = averageKcal,
            averageProteinG = averageProtein,
            proteinGoalG = proteinGoalG,
            daysMeetingProtein = daysMeetingProtein,
            trust = when {
                loggedDays == 0 -> BodyTrust.MISSING
                proteinGoalG <= 0 -> BodyTrust.CALCULATED
                else -> BodyTrust.CALCULATED
            }
        )

        val warnings = mutableListOf<String>()
        warnings.addAll(body.warnings)
        warnings.addAll(training.warnings)
        if (loggedDays < MIN_NUTRITION_DAYS) {
            warnings.add("近 $days 天只有 $loggedDays 天饮食记录，热量与蛋白平均不可靠")
        }
        if (proteinGoalG <= 0) {
            warnings.add("没有设置每日蛋白目标，蛋白达标天数无法判断")
        }

        val summary = mutableListOf<String>()
        summary.add(
            when (body.weight.direction) {
                WeightDirection.LOSING -> body.weight.change28dKg?.let {
                    "比 28 天前体重下降 ${formatAbs(it)} kg（每周 ${formatAbs(body.weight.weeklyRateKg ?: 0.0)} kg）"
                } ?: "体重在下降，但缺少 28 天前的记录，幅度不明"
                WeightDirection.GAINING -> body.weight.change28dKg?.let {
                    "比 28 天前体重上升 ${formatAbs(it)} kg（每周 ${formatAbs(body.weight.weeklyRateKg ?: 0.0)} kg）"
                } ?: "体重在上升，但缺少 28 天前的记录，幅度不明"
                WeightDirection.STABLE ->
                    "体重基本持平（每周变化小于 ${BodyAnalysisEngine.STABLE_RATE_KG_PER_WEEK} kg）"
                WeightDirection.UNKNOWN -> "体重趋势数据不足，暂不判断方向"
            }
        )
        val strength = training.summary(ExerciseRecordType.STRENGTH)?.sessions ?: 0
        val cardio = training.summary(ExerciseRecordType.CARDIO)?.sessions ?: 0
        val hiit = training.summary(ExerciseRecordType.HIIT)?.sessions ?: 0
        summary.add("同期训练 ${strength + cardio + hiit} 次（力量 $strength / 有氧 $cardio / HIIT $hiit）")
        summary.add(
            if (loggedDays > 0) {
                "饮食记录 $loggedDays 天，有记录日均 ${averageKcal ?: 0} kcal，" +
                    if (proteinGoalG > 0) "蛋白达标 $daysMeetingProtein 天" else "未设蛋白目标"
            } else {
                "饮食记录 0 天"
            }
        )
        body.bmi.value?.let { bmi ->
            summary.add("BMI ${formatAbs(bmi)}（${body.bmi.category ?: "-"}）")
        }

        return HealthReport(
            generatedFor = today,
            windowDays = days,
            body = body,
            training = training,
            nutrition = nutrition,
            summary = summary,
            warnings = warnings.distinct(),
            insufficientData = body.insufficientData ||
                training.insufficientData ||
                loggedDays < MIN_NUTRITION_DAYS
        )
    }

    private fun formatAbs(value: Double): String {
        val abs = kotlin.math.abs(value)
        return String.format(java.util.Locale.US, "%.1f", abs)
    }
}
