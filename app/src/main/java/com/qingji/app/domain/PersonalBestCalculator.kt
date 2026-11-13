package com.qingji.app.domain

import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import java.time.LocalDate

/**
 * ============================================================
 * 个人最佳（P1）：按动作汇总历史最好成绩
 * ------------------------------------------------------------
 * 铁律：只读 workout_exercise.exerciseNameSnapshot，
 * 不 JOIN 动作库现名 —— 动作改名 / 删掉之后，历史成绩不能跟着变。
 * 1RM 一律是 Epley 公式的**估算值**，界面必须带「估算」角标。
 * ============================================================
 */
object PersonalBestCalculator {

    data class Best(
        val name: String,
        /** 单次举起过的最大重量（kg） */
        val maxWeightKg: Double,
        /** 该最大重量那次做了几次 */
        val repsAtMax: Int,
        /** 所有组里估算 1RM 的最大值 */
        val best1RM: Double,
        /** 最好成绩出现在哪天 */
        val date: LocalDate?,
        /** 一共记过多少组 */
        val totalSets: Int,
        /** 一共练过多少次（不同会话） */
        val sessionCount: Int
    )

    fun compute(
        sessions: List<WorkoutSession>,
        allExercises: List<WorkoutExercise>
    ): List<Best> {
        val dateBySession = sessions.associate { it.uuid to it.date }
        // 按动作名快照分组；一个动作可能在一天里出现在多个会话
        val grouped = allExercises.groupBy { it.exerciseNameSnapshot.trim() }
            .filterKeys { it.isNotEmpty() }

        return grouped.map { (name, rows) ->
            val sets = rows.flatMap { row -> TrainingMath.decodeSets(row.setsJson) }
            val withDate = rows.flatMap { row ->
                TrainingMath.decodeSets(row.setsJson).map { set -> set to dateBySession[row.sessionUuid] }
            }
            val maxWeight = sets.maxOfOrNull { it.weightKg } ?: 0.0
            val repsAtMax = sets.filter { it.weightKg == maxWeight }.maxOfOrNull { it.reps } ?: 0
            val best1RM = sets.maxOfOrNull { TrainingMath.estimate1RM(it.weightKg, it.reps) } ?: 0.0
            val bestDate = withDate
                .filter { (set, _) -> set.weightKg == maxWeight && maxWeight > 0.0 }
                .mapNotNull { it.second }
                .maxOrNull()
                ?: rows.mapNotNull { dateBySession[it.sessionUuid] }.maxOrNull()

            Best(
                name = name,
                maxWeightKg = maxWeight,
                repsAtMax = repsAtMax,
                best1RM = best1RM,
                date = bestDate,
                totalSets = sets.size,
                sessionCount = rows.map { it.sessionUuid }.distinct().size
            )
        }.sortedByDescending { it.maxWeightKg }
    }
}
