package com.qingji.app.domain

import org.json.JSONObject

/**
 * 统一训练项目（统一运动体系 V2.1 / B1）。
 *
 * 五类全部具备：稳定项目 ID、动作身份(exerciseUuid/name)、recordType、顺序、
 * workoutMode、备注、历史关联(sessionUuid)。同一动作在一场中重复出现时以
 * itemId 区分，按 itemId 编辑/删除，绝不按 exerciseUuid 覆盖。
 *
 * 力量/有氧明细分开：Strength 用 setsJson(WorkoutSet)，其余用 cardioDetail/rawInputJson。
 * schemaVersion 用于 JSON 升级与降级；未知枚举值降级 NORMAL/OTHER 并保留诊断。
 */
object WorkoutItemJson {
    const val SCHEMA_VERSION = 1
    const val K_VERSION = "v"
    const val K_ID = "id"
    const val K_UUID = "eu"
    const val K_NAME = "n"
    const val K_TYPE = "t"
    const val K_ORDER = "o"
    const val K_MODE = "m"
    const val K_NOTE = "nt"
    const val K_SESSION = "s"
    const val K_DURATION = "d"
    const val K_DISTANCE = "di"
    const val K_INTENSITY = "in"
    const val K_KCAL = "k"
    const val K_METHOD = "km"
    const val K_CALC_VERSION = "kv"
    const val K_WEIGHT_SNAP = "w"
    const val K_WORK_SEC = "ws"
    const val K_REST_SEC = "rs"
    const val K_ROUNDS = "rn"
    const val K_MET = "met"
    const val K_CONTENT = "c"
    const val K_REPS = "rp"
}

sealed class WorkoutItem {
    abstract val itemId: String
    abstract val exerciseUuid: String
    abstract val name: String
    abstract val recordType: ExerciseRecordType
    abstract val order: Int
    abstract val mode: WorkoutMode
    abstract val note: String
    abstract val sessionUuid: String

    data class Strength(
        override val itemId: String,
        override val exerciseUuid: String,
        override val name: String,
        override val order: Int,
        override val mode: WorkoutMode = WorkoutMode.NORMAL,
        override val note: String = "",
        override val sessionUuid: String = "",
        val sets: List<WorkoutSet> = emptyList()
    ) : WorkoutItem() {
        override val recordType get() = ExerciseRecordType.STRENGTH
    }

    data class Cardio(
        override val itemId: String,
        override val exerciseUuid: String,
        override val name: String,
        override val order: Int,
        override val mode: WorkoutMode = WorkoutMode.NORMAL,
        override val note: String = "",
        override val sessionUuid: String = "",
        val durationSec: Long = 0L,
        val distanceMeters: Double? = null,
        val intensity: String? = null,
        val calculatedKcal: Double = 0.0,
        val calculationMethod: String = "",
        val calculationVersion: String = "",
        val weightSnapshot: Double? = null
    ) : WorkoutItem() {
        override val recordType get() = ExerciseRecordType.CARDIO
    }

    /** HIIT：本轮简单间歇字段，不做多阶段编排引擎。 */
    data class Hiit(
        override val itemId: String,
        override val exerciseUuid: String,
        override val name: String,
        override val order: Int,
        override val mode: WorkoutMode = WorkoutMode.INTERVAL,
        override val note: String = "",
        override val sessionUuid: String = "",
        val durationSec: Long = 0L,
        val intensity: String? = null,
        val met: Double? = null,
        val workSec: Int = 0,
        val restSec: Int = 0,
        val rounds: Int = 0,
        val calculatedKcal: Double = 0.0,
        val calculationVersion: String = "",
        val weightSnapshot: Double? = null
    ) : WorkoutItem() {
        override val recordType get() = ExerciseRecordType.HIIT
    }

    data class Flexibility(
        override val itemId: String,
        override val exerciseUuid: String,
        override val name: String,
        override val order: Int,
        override val mode: WorkoutMode = WorkoutMode.NORMAL,
        override val note: String = "",
        override val sessionUuid: String = "",
        val durationSec: Long = 0L,
        val content: String = ""
    ) : WorkoutItem() {
        override val recordType get() = ExerciseRecordType.FLEXIBILITY
    }

    data class Other(
        override val itemId: String,
        override val exerciseUuid: String,
        override val name: String,
        override val order: Int,
        override val mode: WorkoutMode = WorkoutMode.NORMAL,
        override val note: String = "",
        override val sessionUuid: String = "",
        val durationSec: Long = 0L,
        val reps: Int? = null
    ) : WorkoutItem() {
        override val recordType get() = ExerciseRecordType.OTHER
    }

    companion object {
        /** 从 JSON 解析；schemaVersion 缺失/损坏按 v0 兼容，未知枚举降级。 */
        fun fromJson(json: String?): WorkoutItem? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                val version = o.optInt(WorkoutItemJson.K_VERSION, 0) // 0=旧版无版本号
                val type = ExerciseRecordType.fromKey(o.optString(WorkoutItemJson.K_TYPE))
                    ?: ExerciseRecordType.OTHER // 未知类型降级 OTHER，保留原始串于 note 诊断
                val common = Common(
                    itemId = o.optString(WorkoutItemJson.K_ID, ""),
                    exerciseUuid = o.optString(WorkoutItemJson.K_UUID, ""),
                    name = o.optString(WorkoutItemJson.K_NAME, ""),
                    order = o.optInt(WorkoutItemJson.K_ORDER, 0),
                    mode = WorkoutMode.fromKey(o.optString(WorkoutItemJson.K_MODE, null)),
                    note = o.optString(WorkoutItemJson.K_NOTE, ""),
                    sessionUuid = o.optString(WorkoutItemJson.K_SESSION, "")
                )
                if (common.name.isBlank() && common.exerciseUuid.isBlank()) return@runCatching null
                when (type) {
                    ExerciseRecordType.STRENGTH -> Strength(
                        common.itemId, common.exerciseUuid, common.name, common.order, common.mode,
                        common.note, common.sessionUuid,
                        sets = TrainingMath.decodeSets(o.optString("sets"))
                    )
                    ExerciseRecordType.CARDIO -> Cardio(
                        common.itemId, common.exerciseUuid, common.name, common.order, common.mode,
                        common.note, common.sessionUuid,
                        durationSec = o.optLong(WorkoutItemJson.K_DURATION, 0L),
                        distanceMeters = o.optDouble(WorkoutItemJson.K_DISTANCE, Double.NaN).takeIf { it.isFinite() },
                        intensity = o.optString(WorkoutItemJson.K_INTENSITY, "").ifBlank { null },
                        calculatedKcal = o.optDouble(WorkoutItemJson.K_KCAL, 0.0).takeIf { it.isFinite() } ?: 0.0,
                        calculationMethod = o.optString(WorkoutItemJson.K_METHOD, ""),
                        calculationVersion = o.optString(WorkoutItemJson.K_CALC_VERSION, ""),
                        weightSnapshot = o.optDouble(WorkoutItemJson.K_WEIGHT_SNAP, Double.NaN).takeIf { it.isFinite() }
                    )
                    ExerciseRecordType.HIIT -> Hiit(
                        common.itemId, common.exerciseUuid, common.name, common.order, common.mode,
                        common.note, common.sessionUuid,
                        durationSec = o.optLong(WorkoutItemJson.K_DURATION, 0L),
                        intensity = o.optString(WorkoutItemJson.K_INTENSITY, "").ifBlank { null },
                        met = o.optDouble(WorkoutItemJson.K_MET, Double.NaN).takeIf { it.isFinite() },
                        workSec = o.optInt(WorkoutItemJson.K_WORK_SEC, 0),
                        restSec = o.optInt(WorkoutItemJson.K_REST_SEC, 0),
                        rounds = o.optInt(WorkoutItemJson.K_ROUNDS, 0),
                        calculatedKcal = o.optDouble(WorkoutItemJson.K_KCAL, 0.0).takeIf { it.isFinite() } ?: 0.0,
                        calculationVersion = o.optString(WorkoutItemJson.K_CALC_VERSION, ""),
                        weightSnapshot = o.optDouble(WorkoutItemJson.K_WEIGHT_SNAP, Double.NaN).takeIf { it.isFinite() }
                    )
                    ExerciseRecordType.FLEXIBILITY -> Flexibility(
                        common.itemId, common.exerciseUuid, common.name, common.order, common.mode,
                        common.note, common.sessionUuid,
                        durationSec = o.optLong(WorkoutItemJson.K_DURATION, 0L),
                        content = o.optString(WorkoutItemJson.K_CONTENT, "")
                    )
                    ExerciseRecordType.OTHER -> Other(
                        common.itemId, common.exerciseUuid, common.name, common.order, common.mode,
                        common.note, common.sessionUuid,
                        durationSec = o.optLong(WorkoutItemJson.K_DURATION, 0L),
                        reps = o.optInt(WorkoutItemJson.K_REPS, -1).takeIf { it >= 0 }
                    )
                }
            }.getOrNull()
        }

        /** 序列化为 JSON（schemaVersion=1）。与 TrainingMath.encodeSets 的旧组数据兼容。 */
        fun toJson(item: WorkoutItem): String = JSONObject().apply {
            put(WorkoutItemJson.K_VERSION, WorkoutItemJson.SCHEMA_VERSION)
            put(WorkoutItemJson.K_ID, item.itemId)
            put(WorkoutItemJson.K_UUID, item.exerciseUuid)
            put(WorkoutItemJson.K_NAME, item.name)
            put(WorkoutItemJson.K_TYPE, item.recordType.key)
            put(WorkoutItemJson.K_ORDER, item.order)
            put(WorkoutItemJson.K_MODE, item.mode.key)
            put(WorkoutItemJson.K_NOTE, item.note)
            put(WorkoutItemJson.K_SESSION, item.sessionUuid)
            when (item) {
                is Strength -> put("sets", TrainingMath.encodeSets(item.sets))
                is Cardio -> {
                    put(WorkoutItemJson.K_DURATION, item.durationSec)
                    item.distanceMeters?.let { put(WorkoutItemJson.K_DISTANCE, it) }
                    item.intensity?.let { put(WorkoutItemJson.K_INTENSITY, it) }
                    put(WorkoutItemJson.K_KCAL, item.calculatedKcal)
                    put(WorkoutItemJson.K_METHOD, item.calculationMethod)
                    put(WorkoutItemJson.K_CALC_VERSION, item.calculationVersion)
                    item.weightSnapshot?.let { put(WorkoutItemJson.K_WEIGHT_SNAP, it) }
                }
                is Hiit -> {
                    put(WorkoutItemJson.K_DURATION, item.durationSec)
                    item.intensity?.let { put(WorkoutItemJson.K_INTENSITY, it) }
                    item.met?.let { put(WorkoutItemJson.K_MET, it) }
                    put(WorkoutItemJson.K_WORK_SEC, item.workSec)
                    put(WorkoutItemJson.K_REST_SEC, item.restSec)
                    put(WorkoutItemJson.K_ROUNDS, item.rounds)
                    put(WorkoutItemJson.K_KCAL, item.calculatedKcal)
                    put(WorkoutItemJson.K_CALC_VERSION, item.calculationVersion)
                    item.weightSnapshot?.let { put(WorkoutItemJson.K_WEIGHT_SNAP, it) }
                }
                is Flexibility -> {
                    put(WorkoutItemJson.K_DURATION, item.durationSec)
                    put(WorkoutItemJson.K_CONTENT, item.content)
                }
                is Other -> {
                    put(WorkoutItemJson.K_DURATION, item.durationSec)
                    item.reps?.let { put(WorkoutItemJson.K_REPS, it) }
                }
            }
        }.toString()

        private data class Common(
            val itemId: String, val exerciseUuid: String, val name: String, val order: Int,
            val mode: WorkoutMode, val note: String, val sessionUuid: String
        )
    }
}
