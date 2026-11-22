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
    val fromLast: Boolean = false,
    /** 统一体系 V2.1：非空 = 非力量动作（有氧/HIIT等），保存时走 WorkoutItem.Cardio。 */
    val recordType: String = "",
    /** 该动作的计划目标配置 JSON（TargetConfigJson），空串 = 力量或没目标。 */
    val targetConfigJson: String = "",
    /** B1.1：用户实际录入的时长/距离/间歇字段（WorkoutItem JSON）。目标字段与目标配置严格分离。 */
    val actualJson: String = ""
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
    val fixedCreatedAt: Long = 0L,
    /**
     * 2026-11-18：手动指定的训练时长（分钟）。0 = 没指定，按计时算。
     * 补记过去的训练必须用它（计时器对过去没意义）；正常练也可以点时长条改。
     */
    val durationOverrideMin: Int = 0,
    /** 非空 = 补记模式：这场训练算这一天的（ISO 格式 yyyy-MM-dd），计时器不跑。 */
    val backfillDate: String = "",
    /**
     * QJ 训练联动：非空 = 这场是从当天计划启动的，值为计划名快照。
     * 保存时写进 WorkoutSession.source（plan:<计划名>），供训练日记追溯来源。
     * 空 = 手动/补记/编辑，行为与旧版一致。
     */
    val sourcePlan: String = "",
    /**
     * B1.1.1：草稿版本号。每次改动由 persist() 单调递增，并随草稿一起落盘。
     * save() 用它判断「保存期间用户是否又改了草稿」：revision 变了就不能清草稿。
     */
    val revision: Long = 0L
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

    /**
     * B1.1.2.1：草稿写入前钩子，生产为空操作。internal + 默认空操作，仅测试注入异常以验证
     * 「写入失败时内存 revision 与 SP 中 revision 保持一致」（T20）。
     * SharedPreferences.apply() 只保证内存立即可见，物理磁盘写入由系统异步完成。
     */
    internal var saveHook: () -> Unit = {}

    companion object {
        /**
         * B1.1.2.1：进程级共享锁。
         * 生产架构里确实存在多个 WorkoutDraftStore 实例写同一个 workout_draft 文件
         * （TrainingRepository 持有一个；QingJiRepository 在清草稿/恢复出厂时又 new 出实例），
         * 实例级锁无法跨实例互斥，因此锁必须放在 companion 里。
         * SharedPreferences 这里是 MODE_PRIVATE，不跨进程；多进程场景不在本类保证范围。
         */
        private val DRAFT_LOCK = Any()

        private const val KEY_DRAFT = "draft_json"
        private const val KEY_UNIT = "weight_unit"   // kg / lb，纯显示层，不进备份
        private const val KEY_REST_DEFAULT = "rest_default_sec"  // 组间休息默认时长（秒）
        private const val DEFAULT_REST_SEC = 90
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
                    fromLast = e.optBoolean("fromLast", false),
                    recordType = e.optString("recordType", ""),
                    targetConfigJson = e.optString("targetConfigJson", ""),
                    actualJson = e.optString("actualJson", "")
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
                fixedCreatedAt = o.optLong("fixedCreatedAt", 0L),
                durationOverrideMin = o.optInt("durationOverrideMin", 0),
                backfillDate = o.optString("backfillDate", ""),
                sourcePlan = o.optString("sourcePlan", ""),
                revision = o.optLong("revision", 0L)
            )
        }.getOrNull()?.takeIf { it.sessionUuid.isNotBlank() && it.startedAt > 0L }
    }

    fun save(draft: WorkoutDraft) = synchronized(DRAFT_LOCK) {
        saveHook()   // B1.1.2：测试注入落盘失败（生产为空）
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
            put("durationOverrideMin", draft.durationOverrideMin)
            put("backfillDate", draft.backfillDate)
            put("sourcePlan", draft.sourcePlan)
            put("revision", draft.revision)
            put("exercises", JSONArray().apply {
                draft.exercises.forEach { e ->
                    put(JSONObject().apply {
                        put("key", e.key)
                        put("exerciseUuid", e.exerciseUuid)
                        put("name", e.name)
                        put("setsJson", TrainingMath.encodeSets(e.sets))
                        put("note", e.note)
                        put("fromLast", e.fromLast)
                        put("recordType", e.recordType)
                        put("targetConfigJson", e.targetConfigJson)
                        put("actualJson", e.actualJson)
                    })
                }
            })
        }
        sp.edit().putString(KEY_DRAFT, o.toString()).apply()
    }

    fun clear() = synchronized(DRAFT_LOCK) { sp.edit().remove(KEY_DRAFT).apply() }

    /**
     * B1.1.2.1：同时校验草稿身份与版本，两者都匹配才删除。
     * B1.1.2 只比较 revision 是不充分的：不同训练会话都可能 revision=1，
     * 旧保存完成后会误删另一份新训练的草稿。
     * 读取、身份校验、版本校验、删除全部在 DRAFT_LOCK 内完成；任一条件不满足都保留草稿。
     * 返回是否真的删除；调用方据此决定内存 _draft 是否置空。
     */
    internal fun clearIfMatches(expectedSessionUuid: String, expectedRevision: Long): Boolean =
        synchronized(DRAFT_LOCK) {
            val current = load()
            if (current != null &&
                current.sessionUuid == expectedSessionUuid &&
                current.revision == expectedRevision
            ) {
                sp.edit().remove(KEY_DRAFT).apply()
                true
            } else {
                false
            }
        }

    /** kg/lb 只在显示层换算，库里永远存 kg。 */
    fun unit(): String = sp.getString(KEY_UNIT, UNIT_KG) ?: UNIT_KG

    fun setUnit(unit: String) {
        sp.edit().putString(KEY_UNIT, if (unit == UNIT_LB) UNIT_LB else UNIT_KG).apply()
    }

    /** 组间休息默认时长（秒）。勾选「完成此组」后按它自动开始倒计时。 */
    fun restDefaultSeconds(): Int =
        sp.getInt(KEY_REST_DEFAULT, DEFAULT_REST_SEC).coerceIn(15, 600)

    fun setRestDefaultSeconds(seconds: Int) {
        sp.edit().putInt(KEY_REST_DEFAULT, seconds.coerceIn(15, 600)).apply()
    }

    /** 恢复出厂：草稿和单位偏好一起清。 */
    fun clearAll() = sp.edit().clear().apply()
}
