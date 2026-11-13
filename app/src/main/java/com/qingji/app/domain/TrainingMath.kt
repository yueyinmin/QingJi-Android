package com.qingji.app.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一组训练记录。重量一律 kg（基准单位），lb 只在界面上换算。
 * rpe = 0 表示「这次没填自觉费力程度」；restSec = 0 表示没记组间休息。
 */
data class WorkoutSet(
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Int = 0,
    val restSec: Int = 0
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
                    restSec = o.optInt("rest", 0).coerceAtLeast(0)
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 时长文案：把毫秒换成「01:23:45」或「23:45」，计时条和历史都用它。 */
    fun formatDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, sec)
        else "%02d:%02d".format(m, sec)
    }
}
