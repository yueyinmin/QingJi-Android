package com.qingji.app.data.backup

import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * ============================================================
 * 备份文件 v3 新增的 training 段（训练中心 6 张表）
 * ------------------------------------------------------------
 * 为什么是 v3 不是 v2：BackupFormat.VERSION 在 v1.0.6 就已经是 2 了
 * （当时补了围度/照片/运动/断食/自建运动五段）。
 * ParsedBackup.version 的默认值 1 只是「读很老的备份时」的兜底，别拿它当当前版本。
 *
 * 兼容规则（与 restoreInto 的其它记录类一致，优先不丢数据）：
 * - version >= 3：按备份里的 training 段整包覆盖（先清 6 表再灌）；
 * - version < 3（v1.0.9 及以前导出的老备份）：根本没有训练数据，
 *   **训练表一行都不动**，免得用户导个旧备份把新记的训练弄没了。
 * ============================================================
 */
data class TrainingBackup(
    val exercises: List<ExerciseEntity> = emptyList(),
    val plans: List<TrainingPlan> = emptyList(),
    val planDays: List<TrainingPlanDay> = emptyList(),
    val planExercises: List<PlanExercise> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val workoutExercises: List<WorkoutExercise> = emptyList()
) {
    fun isEmpty(): Boolean = exercises.isEmpty() && plans.isEmpty() && planDays.isEmpty() &&
        planExercises.isEmpty() && sessions.isEmpty() && workoutExercises.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("exercises", JSONArray().apply {
            exercises.forEach { e -> put(JSONObject().apply {
                put("uuid", e.uuid); put("name", e.name); put("category", e.category)
                put("primaryMuscles", e.primaryMuscles); put("secondaryMuscles", e.secondaryMuscles)
                put("equipment", e.equipment); put("steps", e.steps)
                put("commonMistakes", e.commonMistakes)
                put("isBuiltIn", e.isBuiltIn); put("isFavorite", e.isFavorite)
                put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
            }) }
        })
        put("plans", JSONArray().apply {
            plans.forEach { p -> put(JSONObject().apply {
                put("uuid", p.uuid); put("name", p.name); put("source", p.source)
                put("isActive", p.isActive); put("createdAt", p.createdAt)
            }) }
        })
        put("planDays", JSONArray().apply {
            planDays.forEach { d -> put(JSONObject().apply {
                put("id", d.id); put("planUuid", d.planUuid); put("weekday", d.weekday)
                put("title", d.title); put("isRest", d.isRest)
            }) }
        })
        put("planExercises", JSONArray().apply {
            planExercises.forEach { x -> put(JSONObject().apply {
                put("id", x.id); put("dayId", x.dayId); put("exerciseUuid", x.exerciseUuid)
                put("exerciseNameSnapshot", x.exerciseNameSnapshot)
                put("targetSets", x.targetSets); put("targetReps", x.targetReps)
                put("targetWeightKg", x.targetWeightKg); put("orderInDay", x.orderInDay)
            }) }
        })
        put("sessions", JSONArray().apply {
            sessions.forEach { s -> put(JSONObject().apply {
                put("uuid", s.uuid); put("date", s.date.toString())
                put("startedAt", s.startedAt); put("endedAt", s.endedAt)
                put("durationMinutes", s.durationMinutes); put("note", s.note)
                put("source", s.source); put("createdAt", s.createdAt)
            }) }
        })
        put("workoutExercises", JSONArray().apply {
            workoutExercises.forEach { w -> put(JSONObject().apply {
                put("id", w.id); put("sessionUuid", w.sessionUuid)
                put("exerciseUuid", w.exerciseUuid)
                put("exerciseNameSnapshot", w.exerciseNameSnapshot)
                put("orderInSession", w.orderInSession)
                put("setsJson", w.setsJson); put("note", w.note)
            }) }
        })
    }
}

/** 单条坏数据只跳过那一条；整段读不了就返回 null（当作没有训练数据）。 */
fun parseTraining(o: JSONObject?): TrainingBackup? {
    if (o == null) return null
    return try {
        TrainingBackup(
            exercises = o.items("exercises") { e ->
                val name = e.optString("name", "").trim()
                if (name.isEmpty() || e.optString("uuid", "").isEmpty()) null else ExerciseEntity(
                    uuid = e.optString("uuid"), name = name,
                    category = e.optString("category", ""),
                    primaryMuscles = e.optString("primaryMuscles", ""),
                    secondaryMuscles = e.optString("secondaryMuscles", ""),
                    equipment = e.optString("equipment", ""),
                    steps = e.optString("steps", ""),
                    commonMistakes = e.optString("commonMistakes", ""),
                    isBuiltIn = e.optBoolean("isBuiltIn", false),
                    isFavorite = e.optBoolean("isFavorite", false),
                    createdAt = e.optLong("createdAt", 0L),
                    updatedAt = e.optLong("updatedAt", 0L)
                )
            },
            plans = o.items("plans") { p ->
                val uuid = p.optString("uuid", "")
                if (uuid.isEmpty()) null else TrainingPlan(
                    uuid = uuid, name = p.optString("name", ""),
                    source = p.optString("source", "custom"),
                    isActive = p.optBoolean("isActive", false),
                    createdAt = p.optLong("createdAt", 0L)
                )
            },
            planDays = o.items("planDays") { d -> TrainingPlanDay(
                id = d.optLong("id", 0L), planUuid = d.optString("planUuid", ""),
                weekday = d.optInt("weekday", 1), title = d.optString("title", ""),
                isRest = d.optBoolean("isRest", false)
            ) },
            planExercises = o.items("planExercises") { x -> PlanExercise(
                id = x.optLong("id", 0L), dayId = x.optLong("dayId", 0L),
                exerciseUuid = x.optString("exerciseUuid", ""),
                exerciseNameSnapshot = x.optString("exerciseNameSnapshot", ""),
                targetSets = x.optInt("targetSets", 0), targetReps = x.optInt("targetReps", 0),
                targetWeightKg = x.optDouble("targetWeightKg", 0.0),
                orderInDay = x.optInt("orderInDay", 0)
            ) },
            sessions = o.items("sessions") { s ->
                val uuid = s.optString("uuid", "")
                val date = s.dateOrNull("date") ?: return@items null
                if (uuid.isEmpty()) null else WorkoutSession(
                    uuid = uuid, date = date,
                    startedAt = s.optLong("startedAt", 0L), endedAt = s.optLong("endedAt", 0L),
                    durationMinutes = s.optInt("durationMinutes", 0),
                    note = s.optString("note", ""), source = s.optString("source", "manual"),
                    createdAt = s.optLong("createdAt", 0L)
                )
            },
            workoutExercises = o.items("workoutExercises") { w -> WorkoutExercise(
                id = w.optLong("id", 0L), sessionUuid = w.optString("sessionUuid", ""),
                exerciseUuid = w.optString("exerciseUuid", ""),
                exerciseNameSnapshot = w.optString("exerciseNameSnapshot", ""),
                orderInSession = w.optInt("orderInSession", 0),
                setsJson = w.optString("setsJson", ""), note = w.optString("note", "")
            ) }
        )
    } catch (e: Exception) {
        null
    }
}

private inline fun <T> JSONObject.items(key: String, mapper: (JSONObject) -> T?): List<T> {
    val arr = optJSONArray(key) ?: return emptyList()
    val out = ArrayList<T>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val item = try { mapper(obj) } catch (e: Exception) { null }
        if (item != null) out.add(item)
    }
    return out
}

/** 与 ParsedBackup 的日期规则一致：空串 / "null" / 格式不对都当没有。 */
private fun JSONObject.dateOrNull(key: String): LocalDate? {
    val raw = optString(key, "").trim()
    if (raw.isEmpty() || raw == "null") return null
    return try { LocalDate.parse(raw) } catch (e: Exception) { null }
}
