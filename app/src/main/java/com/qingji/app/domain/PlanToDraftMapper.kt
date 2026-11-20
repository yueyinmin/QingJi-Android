package com.qingji.app.domain

import com.qingji.app.data.local.entity.PlanExercise

/**
 * ============================================================
 * 训练计划 → 训练草稿的纯映射（QJ 训练联动 P0）
 * ------------------------------------------------------------
 * 职责单一：把一条计划动作（目标组数/次数/重量）转成草稿里的动作数据。
 * 纯 Kotlin、可单测，不碰 Activity / Room / SharedPreferences。
 *
 * 目标与实际分离：
 * - 每组 targetReps / targetWeightKg = 计划目标（可空，独立字段）；
 * - reps / weightKg = 实际值，仅按现有录入习惯**预填**计划值，方便直接开练；
 * - completed 恒为 false —— 目标组数 ≠ 已完成组数；
 * - 用户之后改实际值只动 reps/weightKg，targetReps/targetWeightKg 不动。
 * ============================================================
 */
object PlanToDraftMapper {

    /** 映射结果：草稿动作需要的三件事（动作 UUID / 名称快照 / 训练组）。 */
    data class Mapped(
        val exerciseUuid: String,
        val name: String,
        val sets: List<WorkoutSet>
    )

    /**
     * 目标组数决定生成几组；每组写入 targetReps/targetWeightKg。
     * 目标组数 <= 0 时至少给 1 组（避免无效空动作）；
     * 目标次数/重量 <= 0 视为"未指定"，目标字段存 null。
     */
    fun map(exercise: PlanExercise): Mapped {
        val groupCount = exercise.targetSets.coerceAtLeast(1)
        val targetReps = exercise.targetReps.takeIf { it > 0 }
        val targetWeight = exercise.targetWeightKg.takeIf { it > 0.0 && it.isFinite() }
        val sets = (0 until groupCount).map {
            WorkoutSet(
                // 实际值预填计划值，用户可直接记录；之后改实际不影响目标
                reps = targetReps ?: 0,
                weightKg = targetWeight ?: 0.0,
                completed = false,
                targetReps = targetReps,
                targetWeightKg = targetWeight
            )
        }
        return Mapped(
            exerciseUuid = exercise.exerciseUuid,
            name = exercise.exerciseNameSnapshot,
            sets = sets
        )
    }
}
