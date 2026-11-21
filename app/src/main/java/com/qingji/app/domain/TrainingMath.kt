package com.qingji.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一组训练记录。重量一律 kg（基准单位），lb 只在界面上换算。
 * rpe = 0 表示「这次没填自觉费力程度」；restSec = 0 表示没记组间休息。
 * completed = 用户是否点了「完成此组」（v1.4 健康体系：完成后自动开始组间休息）。
 * 旧数据没有这个字段，读出来默认 false，不影响历史展示与结算。
 */
data class WorkoutSet(
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Int = 0,
    val restSec: Int = 0,
    val completed: Boolean = false,
    /**
     * QJ 训练联动：计划目标次数 / 目标重量（可空）。
     * null = 这组没有计划目标（手动空白训练 / 旧历史数据），不是 0。
     * 目标与实际（reps/weightKg）独立：训练中改实际值不覆盖目标。
     * 存进 setsJson 的 tr / tw 两个 key，旧 JSON 没有这两个 key 时读回 null。
     */
    val targetReps: Int? = null,
    val targetWeightKg: Double? = null,
    /**
     * 统一运动体系 B1：等长训练（平板支撑/悬垂等）按组记录持续时间。
     * 可空：null = 普通组次训练（旧数据/无等长需求），不是 0。
     * 存进 setsJson 的 ds key，旧 JSON 无此 key 时读回 null，不破坏历史。
     */
    val durationSec: Int? = null
)

/**
 * ============================================================
 * 训练中心的纯计算 + 组数据打包（不碰安卓、不碰数据库，方便在电脑上跑单测）
 * ------------------------------------------------------------
 * 所有「估算」类数字都只在这里算，界面负责加「估算」角标。
 * 这些是经验公式，不是医学结论，也不作为训练处方。
 * ============================================================
 */
object TrainingMath {

    /** 1RM 估算：Epley 公式 w × (1 + 次数 / 30)。只举得起 1 次时就是 w 本身。 */
    fun estimate1RM(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        // 次数超过 15 以后公式误差明显变大，界面上要提醒「次数越多越不准」
        return weightKg * (1.0 + reps / 30.0)
    }

    /** 一次训练的训练量 = 每组「重量 × 次数」加总。徒手动作重量记 0，只算次数不计重量。 */
    fun sessionVolume(sets: List<WorkoutSet>): Double =
        sets.sumOf { if (it.weightKg > 0.0) it.weightKg * it.reps else 0.0 }

    /**
     * 训练量显示单位（2026-11-18）：1000 千克（1 吨）往上直接用「吨」，
     * 不然一练就是「3 万千克」，数字长到没眼看。不到 1 吨照旧显示 kg。
     */
    fun formatVolume(volumeKg: Double): String =
        if (volumeKg >= 1000.0) "%.1f 吨".format(volumeKg / 1000.0)
        else "%.0f kg".format(volumeKg)

    fun kgToLb(kg: Double): Double = kg * 2.20462
    fun lbToKg(lb: Double): Double = lb / 2.20462

    /** 把一包组数据打包成 JSON 文本，存进 workout_exercise.setsJson。 */
    fun encodeSets(sets: List<WorkoutSet>): String = JSONArray().apply {
        sets.forEach { s ->
            put(JSONObject().apply {
                put("w", s.weightKg)
                put("r", s.reps)
                put("rpe", s.rpe)
                put("rest", s.restSec)
                put("done", s.completed)
                // QJ：计划目标（可空）；null 时不写 key，保持旧 JSON 结构不变
                s.targetReps?.let { put("tr", it) }
                s.targetWeightKg?.let { put("tw", it) }
                // 等长训练组持续时间；null 不写 key，保持旧 JSON 结构不变
                s.durationSec?.let { put("ds", it) }
            })
        }
    }.toString()

    /** 读回来。单个字段坏了就当 0，绝不让一条坏数据把整页历史卡住。 */
    fun decodeSets(json: String?): List<WorkoutSet> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                WorkoutSet(
                    weightKg = o.optDouble("w", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                    reps = o.optInt("r", 0).coerceAtLeast(0),
                    rpe = o.optInt("rpe", 0).coerceIn(0, 10),
                    restSec = o.optInt("rest", 0).coerceAtLeast(0),
                    completed = o.optBoolean("done", false),
                    // QJ：旧 JSON 无 tr/tw → null（无计划目标），不补造 0
                    targetReps = if (o.has("tr")) o.optInt("tr", 0).coerceAtLeast(0) else null,
                    targetWeightKg = if (o.has("tw")) {
                        o.optDouble("tw", 0.0).takeIf { it.isFinite() && it >= 0.0 }
                    } else null,
                    // 旧 JSON 无 ds → null（普通组次），不补造 0；坏值降级 null
                    durationSec = if (o.has("ds")) o.optInt("ds", 0).takeIf { it >= 0 } else null
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 时长文案：把毫秒换成「01:23:45」或「23:45」，计时条和历史都用它。 */
    fun formatDuration(seconds: Long): String = TimeFormat.stopwatch(seconds)
}
