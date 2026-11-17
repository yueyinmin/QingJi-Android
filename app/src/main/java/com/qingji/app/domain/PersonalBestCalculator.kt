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

    /**
     * 302 动作库把 8 条中文名按上游英文名修订了。
     * 历史记录里的名称快照不能改，但 PB 归组时把旧名归一到新名，
     * 否则同一个动作会拆成「旧名 / 新名」两份个人最佳。
     */
    private val NAME_ALIASES = mapOf(
        "坐姿划船" to "坐姿绳索划船",
        "哑铃肩推" to "坐姿哑铃肩推",
        "杠铃弯举" to "EZ 杠弯举",
        "哑铃交替弯举" to "哑铃弯举",
        "窄距俯卧撑" to "钻石俯卧撑",
        "哑铃深蹲" to "高脚杯深蹲",
        "罗马尼亚硬拉" to "哑铃罗马尼亚硬拉",
        "背阔肌拉伸" to "婴儿式"
    )

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
        // 按动作名快照分组；旧名先归一到新名，历史快照本身一行不动
        val grouped = allExercises.groupBy { row ->
            val name = row.exerciseNameSnapshot.trim()
            NAME_ALIASES[name] ?: name
        }.filterKeys { it.isNotEmpty() }

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
