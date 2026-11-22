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

    /**
     * 映射结果：草稿动作需要的身份 + 明细。
     * 力量：sets 非空、cardioConfig=null；
     * 有氧/其他：sets 为空、cardioConfig 非空（由草稿层构造 WorkoutItem.Cardio）。
     */
    data class Mapped(
        val exerciseUuid: String,
        val name: String,
        val sets: List<WorkoutSet>,
        val recordType: ExerciseRecordType = ExerciseRecordType.STRENGTH,
        val cardioConfig: TargetConfig? = null
    )

    /**
     * 目标组数决定生成几组；每组写入 targetReps/targetWeightKg。
     * 目标组数 <= 0 时至少给 1 组（避免无效空动作）；
     * 目标次数/重量 <= 0 视为"未指定"，目标字段存 null。
     */
    fun map(exercise: PlanExercise): Mapped {
        // 统一体系 V2.1：计划目标配置优先（有氧/HIIT/柔韧等），空串时走 legacy 三列。
        val cfg = TargetConfigJson.decode(exercise.targetConfigJson)
        if (cfg != null && cfg.type != ExerciseRecordType.STRENGTH) {
            return Mapped(
                exerciseUuid = exercise.exerciseUuid,
                name = exercise.exerciseNameSnapshot,
                sets = emptyList(),
                recordType = cfg.type,
                cardioConfig = cfg
            )
        }
        val recordType = runCatching { ExerciseRecordType.fromKey(exercise.recordType) }.getOrNull()
        if (recordType != null && recordType != ExerciseRecordType.STRENGTH) {
            // 只写了 recordType 没有 targetConfigJson 的旧数据：不伪造目标，
            // 给一个空配置让用户在训练页填实际时长/距离。
            return Mapped(
                exerciseUuid = exercise.exerciseUuid,
                name = exercise.exerciseNameSnapshot,
                sets = emptyList(),
                recordType = recordType,
                cardioConfig = TargetConfig(type = recordType)
            )
        }
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
