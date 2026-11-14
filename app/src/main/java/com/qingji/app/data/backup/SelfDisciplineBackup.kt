package com.qingji.app.data.backup

import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplineInterventionEntity
import com.qingji.app.data.local.entity.SelfDisciplineMilestoneEntity
import com.qingji.app.data.local.entity.SelfDisciplinePeriodEntity
import com.qingji.app.data.local.entity.SelfDisciplineSettingEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * ============================================================
 * 备份文件 v4 新增的 selfDiscipline 段（自律模块 7 张表）
 * ------------------------------------------------------------
 * 兼容规则（与 training 段一致，优先不丢数据）：
 * - version >= 4：按 selfDiscipline 段整包覆盖（先清 7 表再灌）；
 * - version < 4（老备份）：那时还没有这个模块，7 张表一行都不动，
 *   免得导旧备份把模块上线后新记的自律数据清掉。
 *
 * id / goalId 必须原样往返：7 张表都没有 @ForeignKey，
 * 6 张子表靠 goalId 关联 goals.id，主键一旦重生成关联就全断了。
 * ============================================================
 */
data class SelfDisciplineBackup(
    val goals: List<SelfDisciplineGoalEntity> = emptyList(),
    val periods: List<SelfDisciplinePeriodEntity> = emptyList(),
    val checkins: List<SelfDisciplineCheckinEntity> = emptyList(),
    val urgeLogs: List<SelfDisciplineUrgeEntity> = emptyList(),
    val settings: List<SelfDisciplineSettingEntity> = emptyList(),
    val milestones: List<SelfDisciplineMilestoneEntity> = emptyList(),
    val interventions: List<SelfDisciplineInterventionEntity> = emptyList()
) {
    fun isEmpty(): Boolean = goals.isEmpty() && periods.isEmpty() && checkins.isEmpty() &&
        urgeLogs.isEmpty() && settings.isEmpty() && milestones.isEmpty() && interventions.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("goals", JSONArray().apply {
            goals.forEach { g -> put(JSONObject().apply {
                put("id", g.id)
                put("title", g.title)
                put("goalType", g.goalType)
                put("isEnabled", g.isEnabled)
                put("startDate", g.startDate.toString())
                put("targetDays", g.targetDays)
                put("reminderEnabled", g.reminderEnabled)
                put("reminderMinute", g.reminderMinute)
                put("note", g.note)
                put("createdAt", g.createdAt)
            }) }
        })
        put("periods", JSONArray().apply {
            periods.forEach { p -> put(JSONObject().apply {
                put("id", p.id)
                put("goalId", p.goalId)
                put("startAt", p.startAt.toString())
                put("endAt", p.endAt?.toString() ?: JSONObject.NULL)
                put("endReason", p.endReason ?: JSONObject.NULL)
                put("status", p.status)
                put("createdAt", p.createdAt)
            }) }
        })
        put("checkins", JSONArray().apply {
            checkins.forEach { c -> put(JSONObject().apply {
                put("id", c.id)
                put("goalId", c.goalId)
                put("localDate", c.localDate.toString())
                put("status", c.status)
                put("mood", c.mood)
                put("urgeLevel", c.urgeLevel)
                put("triggers", c.triggers)
                put("copingMethods", c.copingMethods)
                put("note", c.note)
                put("createdAt", c.createdAt)
                put("updatedAt", c.updatedAt)
            }) }
        })
        put("urgeLogs", JSONArray().apply {
            urgeLogs.forEach { u -> put(JSONObject().apply {
                put("id", u.id)
                put("goalId", u.goalId)
                put("occurredAt", u.occurredAt)
                put("intensity", u.intensity)
                put("trigger", u.trigger)
                put("copingMethod", u.copingMethod)
                put("intensityAfter", u.intensityAfter)
                put("resolved", u.resolved)
                put("note", u.note)
                put("createdAt", u.createdAt)
            }) }
        })
        put("settings", JSONArray().apply {
            settings.forEach { s -> put(JSONObject().apply {
                put("key", s.key)
                put("value", s.value)
            }) }
        })
        put("milestones", JSONArray().apply {
            milestones.forEach { m -> put(JSONObject().apply {
                put("id", m.id)
                put("goalId", m.goalId)
                put("milestoneDays", m.milestoneDays)
                put("achievedAt", m.achievedAt?.toString() ?: JSONObject.NULL)
                put("createdAt", m.createdAt)
            }) }
        })
        put("interventions", JSONArray().apply {
            interventions.forEach { v -> put(JSONObject().apply {
                put("id", v.id)
                put("goalId", v.goalId)
                put("type", v.type)
                put("occurredAt", v.occurredAt)
                put("beforeIntensity", v.beforeIntensity)
                put("afterIntensity", v.afterIntensity)
                put("effective", v.effective ?: JSONObject.NULL)
                put("note", v.note)
                put("createdAt", v.createdAt)
            }) }
        })
    }
}

/** 单条坏数据只跳过那一条；整段读不了就返回 null（当作没有自律数据，保留本机）。 */
fun parseSelfDiscipline(o: JSONObject?): SelfDisciplineBackup? {
    if (o == null) return null
    return try {
        SelfDisciplineBackup(
            goals = o.items("goals") { g ->
                val title = g.optString("title", "").trim()
                val startDate = g.dateOrNull("startDate") ?: return@items null
                if (title.isEmpty()) null else SelfDisciplineGoalEntity(
                    id = g.optLong("id", 0L),
                    title = title,
                    goalType = g.optString("goalType", ""),
                    isEnabled = g.optBoolean("isEnabled", true),
                    startDate = startDate,
                    targetDays = g.optInt("targetDays", 0),
                    reminderEnabled = g.optBoolean("reminderEnabled", false),
                    reminderMinute = g.optInt("reminderMinute", -1),
                    note = g.optString("note", ""),
                    createdAt = g.optLong("createdAt", 0L)
                )
            },
            periods = o.items("periods") { p ->
                val startAt = p.dateOrNull("startAt") ?: return@items null
                SelfDisciplinePeriodEntity(
                    id = p.optLong("id", 0L),
                    goalId = p.optLong("goalId", 0L),
                    startAt = startAt,
                    endAt = p.dateOrNull("endAt"),
                    endReason = p.stringOrNull("endReason"),
                    status = p.optString("status", "active"),
                    createdAt = p.optLong("createdAt", 0L)
                )
            },
            checkins = o.items("checkins") { c ->
                val localDate = c.dateOrNull("localDate") ?: return@items null
                SelfDisciplineCheckinEntity(
                    id = c.optLong("id", 0L),
                    goalId = c.optLong("goalId", 0L),
                    localDate = localDate,
                    status = c.optString("status", ""),
                    mood = c.optString("mood", ""),
                    urgeLevel = c.optInt("urgeLevel", 0),
                    triggers = c.optString("triggers", ""),
                    copingMethods = c.optString("copingMethods", ""),
                    note = c.optString("note", ""),
                    createdAt = c.optLong("createdAt", 0L),
                    updatedAt = c.optLong("updatedAt", 0L)
                )
            },
            urgeLogs = o.items("urgeLogs") { u -> SelfDisciplineUrgeEntity(
                id = u.optLong("id", 0L),
                goalId = u.optLong("goalId", 0L),
                occurredAt = u.optLong("occurredAt", 0L),
                intensity = u.optInt("intensity", 0),
                trigger = u.optString("trigger", ""),
                copingMethod = u.optString("copingMethod", ""),
                intensityAfter = u.optInt("intensityAfter", 0),
                resolved = u.optBoolean("resolved", false),
                note = u.optString("note", ""),
                createdAt = u.optLong("createdAt", 0L)
            ) },
            settings = o.items("settings") { s ->
                val key = s.optString("key", "").trim()
                if (key.isEmpty()) null else SelfDisciplineSettingEntity(
                    key = key,
                    value = s.optString("value", "")
                )
            },
            milestones = o.items("milestones") { m -> SelfDisciplineMilestoneEntity(
                id = m.optLong("id", 0L),
                goalId = m.optLong("goalId", 0L),
                milestoneDays = m.optInt("milestoneDays", 0),
                achievedAt = m.dateOrNull("achievedAt"),
                createdAt = m.optLong("createdAt", 0L)
            ) },
            interventions = o.items("interventions") { v -> SelfDisciplineInterventionEntity(
                id = v.optLong("id", 0L),
                goalId = v.optLong("goalId", 0L),
                type = v.optString("type", ""),
                occurredAt = v.optLong("occurredAt", 0L),
                beforeIntensity = v.optInt("beforeIntensity", 0),
                afterIntensity = v.optInt("afterIntensity", 0),
                effective = v.booleanOrNull("effective"),
                note = v.optString("note", ""),
                createdAt = v.optLong("createdAt", 0L)
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

/** 与 ParsedBackup / TrainingBackup 的日期规则一致：空串 / "null" / 格式不对都当没有。 */
private fun JSONObject.dateOrNull(key: String): LocalDate? {
    val raw = optString(key, "").trim()
    if (raw.isEmpty() || raw == "null") return null
    return try { LocalDate.parse(raw) } catch (e: Exception) { null }
}

/** 可空字符串：键缺失 / JSON null 都读成 null，别和真正的空串混在一起。 */
private fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key, "")

/** 可空布尔：键缺失 / JSON null 都读成 null（未评价 ≠ false）。 */
private fun JSONObject.booleanOrNull(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else optBoolean(key, false)
