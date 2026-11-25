package com.qingji.app.domain

import java.time.LocalDate

/** 一次已保存训练的只读快照。只含分析需要的字段，不把数据库实体泄漏到规则层。 */
data class TrainingSessionSnapshot(
    val date: LocalDate,
    val durationMinutes: Int,
    val items: List<WorkoutItem>
)

data class TypeSummary(
    val type: ExerciseRecordType,
    val sessions: Int,
    val totalSets: Int,
    val volumeKg: Double,
    val durationSec: Long,
    val averageRpe: Double?,
    val sessionsPerWeek: Double,
    val minGapDays: Int?,
    val trust: TrustLevel
)

enum class TrustLevel { FACT, CALCULATED, UNKNOWN }

data class TrainingAnalysis(
    val windowDays: Int,
    val totals: Map<ExerciseRecordType, TypeSummary>,
    val insufficientData: Boolean,
    val warnings: List<String>
) {
    fun summary(type: ExerciseRecordType): TypeSummary? = totals[type]
}

/** G 阶段：三类运动分开算；缺数据只标 UNKNOWN，不编恢复指标。 */
object TrainingAnalysisEngine {

    fun analyze(
        snapshots: List<TrainingSessionSnapshot>,
        today: LocalDate,
        windowDays: Int = 28
    ): TrainingAnalysis {
        val from = today.minusDays(windowDays - 1L)
        val inWindow = snapshots.filter { it.date in from..today }
        val warnings = mutableListOf<String>()

        val summaries = listOf(
            ExerciseRecordType.STRENGTH,
            ExerciseRecordType.CARDIO,
            ExerciseRecordType.HIIT
        ).associateWith { type -> summarize(inWindow, type, windowDays) }

        if (inWindow.size < 3) {
            warnings.add("近 ${windowDays} 天训练记录不足 3 次，趋势与恢复结论不可靠")
        }
        if (summaries.values.all { it.sessions == 0 }) {
            warnings.add("近 ${windowDays} 天没有力量/有氧/HIIT 正式记录")
        }

        return TrainingAnalysis(
            windowDays = windowDays,
            totals = summaries,
            insufficientData = inWindow.size < 3,
            warnings = warnings
        )
    }

    private fun summarize(
        sessions: List<TrainingSessionSnapshot>,
        type: ExerciseRecordType,
        windowDays: Int
    ): TypeSummary {
        val matched = sessions.filter { snapshot ->
            snapshot.items.any { it.recordType == type }
        }
        if (matched.isEmpty()) {
            return TypeSummary(type, 0, 0, 0.0, 0L, null, 0.0, null, TrustLevel.UNKNOWN)
        }

        val items = matched.flatMap { it.items }.filter { it.recordType == type }
        val sets = items.filterIsInstance<WorkoutItem.Strength>().flatMap { it.sets }
        val rpeValues = sets.mapNotNull { it.rpe.takeIf { v -> v > 0 } }
        val durationSec = items.sumOf { item ->
            when (item) {
                is WorkoutItem.Cardio -> item.durationSec
                is WorkoutItem.Hiit -> item.durationSec
                else -> 0L
            }
        }
        val dates = matched.map { it.date }.sorted()
        val gaps = dates.zipWithNext { a, b -> java.time.temporal.ChronoUnit.DAYS.between(a, b).toInt() }

        return TypeSummary(
            type = type,
            sessions = matched.size,
            totalSets = sets.size,
            volumeKg = TrainingMath.sessionVolume(sets),
            durationSec = durationSec,
            averageRpe = if (rpeValues.isEmpty()) null
            else rpeValues.sum().toDouble() / rpeValues.size,
            sessionsPerWeek = matched.size * 7.0 / windowDays.coerceAtLeast(1),
            minGapDays = gaps.minOrNull(),
            trust = TrustLevel.CALCULATED
        )
    }
}
