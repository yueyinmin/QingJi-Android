package com.qingji.app.domain

import org.json.JSONObject

/**
 * 训练计划目标配置（统一运动体系 V2.1 / B1）。
 * 版本化 JSON：schemaVersion + type + 可选目标字段。
 *
 * 旧 plan_exercise 只有 targetSets/targetReps/targetWeightKg 三列：
 * targetConfigJson 为空串时，调用方用 legacy 三列兜底（不伪造、不覆盖）。
 * 未知字段忽略；负数值降级 null；计划目标与实际完成数据严格分离。
 */
data class TargetConfig(
    val schemaVersion: Int = TargetConfigJson.SCHEMA_VERSION,
    val type: ExerciseRecordType = ExerciseRecordType.STRENGTH,
    val sets: Int? = null,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Long? = null,
    val distanceMeters: Double? = null,
    val intensity: String? = null,
    val workSec: Int? = null,
    val restSec: Int? = null,
    val rounds: Int? = null
)

object TargetConfigJson {
    const val SCHEMA_VERSION = 1
    private const val K_V = "v"
    private const val K_T = "t"
    private const val K_S = "s"
    private const val K_R = "r"
    private const val K_W = "w"
    private const val K_D = "d"
    private const val K_DI = "di"
    private const val K_IN = "in"
    private const val K_WS = "ws"
    private const val K_RS = "rs"
    private const val K_RN = "rn"

    fun encode(cfg: TargetConfig): String = JSONObject().apply {
        put(K_V, cfg.schemaVersion)
        put(K_T, cfg.type.key)
        cfg.sets?.let { put(K_S, it) }
        cfg.reps?.let { put(K_R, it) }
        cfg.weightKg?.let { put(K_W, it) }
        cfg.durationSec?.let { put(K_D, it) }
        cfg.distanceMeters?.let { put(K_DI, it) }
        cfg.intensity?.let { put(K_IN, it) }
        cfg.workSec?.let { put(K_WS, it) }
        cfg.restSec?.let { put(K_RS, it) }
        cfg.rounds?.let { put(K_RN, it) }
    }.toString()

    fun decode(json: String?): TargetConfig? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            val type = ExerciseRecordType.fromKey(o.optString(K_T)) ?: ExerciseRecordType.OTHER
            TargetConfig(
                schemaVersion = o.optInt(K_V, 0),
                type = type,
                sets = o.optInt(K_S, -1).takeIf { it > 0 },
                reps = o.optInt(K_R, -1).takeIf { it > 0 },
                weightKg = o.optDouble(K_W, Double.NaN).takeIf { it.isFinite() && it >= 0.0 },
                durationSec = o.optLong(K_D, -1L).takeIf { it > 0 },
                distanceMeters = o.optDouble(K_DI, Double.NaN).takeIf { it.isFinite() && it > 0 },
                intensity = o.optString(K_IN, "").ifBlank { null },
                workSec = o.optInt(K_WS, -1).takeIf { it > 0 },
                restSec = o.optInt(K_RS, -1).takeIf { it >= 0 },
                rounds = o.optInt(K_RN, -1).takeIf { it > 0 }
            )
        }.getOrNull()
    }
}
