package com.qingji.app.data.prefs

import android.content.Context
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutSet
import org.json.JSONArray
import org.json.JSONObject

/** 进行中训练里的一个动作（还没落库，所以只有临时 key）。 */
data class DraftExercise(
    val key: String,
    val exerciseUuid: String = "",
    val name: String = "",
    val sets: List<WorkoutSet> = emptyList(),
    val note: String = "",
    /** true = 这几组是从「上次成绩」自动带出来的，界面上用浅绿底标一下。 */
    val fromLast: Boolean = false
)

/** 一次「还没保存」的训练。进程被杀掉后靠它恢复到离开前的样子。 */
data class WorkoutDraft(
    val sessionUuid: String = "",
    val startedAt: Long = 0L,
    /** 0 = 没在暂停；否则是按下暂停那一刻的时间戳。 */
    val pausedAt: Long = 0L,
    /** 之前几段暂停一共多少毫秒，恢复计时时要扣掉。 */
    val pausedMs: Long = 0L,
    val note: String = "",
    val exercises: List<DraftExercise> = emptyList(),
    /** 非空 = 正在编辑当天已有的一次训练；保存时保留它原来的时间与时长，只覆盖明细。 */
    val editingUuid: String = "",
    val originalDate: String = "",
    val fixedStartedAt: Long = 0L,
    val fixedEndedAt: Long = 0L,
    val fixedDurationMinutes: Int = 0,
    val fixedCreatedAt: Long = 0L
) {
    /** 当前已练了多久（秒）。暂停时冻结在暂停那一刻。 */
    fun elapsedSeconds(now: Long): Long {
        val end = if (pausedAt > 0L) pausedAt else now
        return ((end - startedAt - pausedMs).coerceAtLeast(0L)) / 1000L
    }
}

/**
 * ============================================================
 * 进行中训练草稿 + 重量单位偏好
 * ------------------------------------------------------------
 * 为什么用 SharedPreferences 而不是 DataStore / Room：
 * 1. 需求 P0 验收写的是「杀进程后计时可恢复」，这要求进程一冷启动就能
 *    **同步**读到草稿，DataStore 是异步流，首帧会闪一下空状态；
 * 2. 草稿还没完成，不该在 workout_session 里留一条「半成品」记录，
 *    否则历史列表、日历、备份都会把它当成一次真训练；
 * 3. 只是一小段 JSON，SharedPreferences 足够，也不用改 Room 版本。
 *
 * 保存训练成功 / 主动放弃时 clear()；恢复出厂时 clearAll()。
 * ============================================================
 */
class WorkoutDraftStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("workout_draft", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DRAFT = "draft_json"
        private const val KEY_UNIT = "weight_unit"   // kg / lb，纯显示层，不进备份
        const val UNIT_KG = "kg"
        const val UNIT_LB = "lb"
    }

    fun load(): WorkoutDraft? {
        val raw = sp.getString(KEY_DRAFT, null) ?: return null
        if (raw.isBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("exercises") ?: JSONArray()
            val items = (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                DraftExercise(
                    key = e.optString("key", ""),
                    exerciseUuid = e.optString("exerciseUuid", ""),
                    name = e.optString("name", ""),
                    sets = TrainingMath.decodeSets(e.optString("setsJson", "")),
                    note = e.optString("note", ""),
                    fromLast = e.optBoolean("fromLast", false)
                )
            }.filter { it.name.isNotBlank() }
            WorkoutDraft(
                sessionUuid = o.optString("sessionUuid", ""),
                startedAt = o.optLong("startedAt", 0L),
                pausedAt = o.optLong("pausedAt", 0L),
                pausedMs = o.optLong("pausedMs", 0L),
                note = o.optString("note", ""),
                exercises = items,
                editingUuid = o.optString("editingUuid", ""),
                originalDate = o.optString("originalDate", ""),
                fixedStartedAt = o.optLong("fixedStartedAt", 0L),
                fixedEndedAt = o.optLong("fixedEndedAt", 0L),
                fixedDurationMinutes = o.optInt("fixedDurationMinutes", 0),
                fixedCreatedAt = o.optLong("fixedCreatedAt", 0L)
            )
        }.getOrNull()?.takeIf { it.sessionUuid.isNotBlank() && it.startedAt > 0L }
    }

    fun save(draft: WorkoutDraft) {
        val o = JSONObject().apply {
            put("sessionUuid", draft.sessionUuid)
            put("startedAt", draft.startedAt)
            put("pausedAt", draft.pausedAt)
            put("pausedMs", draft.pausedMs)
            put("note", draft.note)
            put("editingUuid", draft.editingUuid)
            put("originalDate", draft.originalDate)
            put("fixedStartedAt", draft.fixedStartedAt)
            put("fixedEndedAt", draft.fixedEndedAt)
            put("fixedDurationMinutes", draft.fixedDurationMinutes)
            put("fixedCreatedAt", draft.fixedCreatedAt)
            put("exercises", JSONArray().apply {
                draft.exercises.forEach { e ->
                    put(JSONObject().apply {
                        put("key", e.key)
                        put("exerciseUuid", e.exerciseUuid)
                        put("name", e.name)
                        put("setsJson", TrainingMath.encodeSets(e.sets))
                        put("note", e.note)
                        put("fromLast", e.fromLast)
                    })
                }
            })
        }
        sp.edit().putString(KEY_DRAFT, o.toString()).apply()
    }

    fun clear() = sp.edit().remove(KEY_DRAFT).apply()

    /** kg/lb 只在显示层换算，库里永远存 kg。 */
    fun unit(): String = sp.getString(KEY_UNIT, UNIT_KG) ?: UNIT_KG

    fun setUnit(unit: String) {
        sp.edit().putString(KEY_UNIT, if (unit == UNIT_LB) UNIT_LB else UNIT_KG).apply()
    }

    /** 恢复出厂：草稿和单位偏好一起清。 */
    fun clearAll() = sp.edit().clear().apply()
}
