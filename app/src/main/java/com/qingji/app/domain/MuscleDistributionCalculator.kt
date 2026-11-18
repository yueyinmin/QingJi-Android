package com.qingji.app.domain

import com.qingji.app.data.local.entity.WorkoutExercise

/**
 * ============================================================
 * 肌群分布统计（工单 4.2 第 6 项，P2）—— 纯函数，不碰数据库
 * ------------------------------------------------------------
 * 数据来自两处：
 *   1. 动作库 ExerciseEntity.category（七类：胸/背/肩/手臂/腿/核心/拉伸）
 *   2. 训练明细 workout_exercise（每组重量×次数 = 训练量 kg）
 *
 * 口径说明（不编造）：
 * - 只统计「能按 UUID 在动作库里找到分类」的动作。
 *   自建动作、被删动作、UUID 为空的老记录 → 归到「未分类」，不硬塞进某一肌群。
 * - 一个动作只算它的主分类（category），不做多肌群拆分：
 *   primaryMuscles 是逗号串，拆开按多个肌群重复计数会虚高，宁可保守。
 * - 训练量用 TrainingMath.sessionVolume 的同一口径（重量×次数，徒手为 0）。
 * ============================================================
 */
object MuscleDistributionCalculator {

    data class Bucket(
        val key: String,
        val label: String,
        val sets: Int,
        val volumeKg: Double,
        val exerciseCount: Int
    )

    /** 七类的固定顺序 + 展示名（跟动作库筛选胶囊一致）。 */
    val CATEGORIES: List<Pair<String, String>> = listOf(
        "chest" to "胸部",
        "back" to "背部",
        "shoulders" to "肩部",
        "arms" to "手臂",
        "legs" to "腿部",
        "core" to "核心",
        "mobility" to "拉伸"
    )

    /**
     * @param exercises 动作库（uuid → 分类 key）
     * @param history 训练明细（近 N 天，调用方给）
     * @return 七类 + 未分类，按固定顺序；一类都没有也给 0 值行，方便界面画满骨架
     */
    fun compute(
        exercises: List<Pair<String, String>>,   // uuid to category
        history: List<WorkoutExercise>
    ): List<Bucket> {
        val categoryByUuid = exercises.toMap()
        val buckets = (CATEGORIES + ("other" to "未分类")).associate { (key, label) ->
            key to Bucket(key, label, 0, 0.0, 0)
        }.toMutableMap()

        history.forEach { row ->
            val cat = categoryByUuid[row.exerciseUuid].orEmpty()
                .takeIf { it.isNotBlank() && it != "other" } ?: "other"
            val sets = TrainingMath.decodeSets(row.setsJson)
            val old = buckets.getValue(cat)
            buckets[cat] = old.copy(
                sets = old.sets + sets.size,
                volumeKg = old.volumeKg + TrainingMath.sessionVolume(sets),
                // 同一个动作在不同场次会占多行，这里数的是「动作条目数」，
                // 界面文案写「条」而不是「个动作」，避免误解成去重数量
                exerciseCount = old.exerciseCount + 1
            )
        }
        return buckets.values.toList()
    }
}
