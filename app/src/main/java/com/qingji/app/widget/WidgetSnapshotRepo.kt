package com.qingji.app.widget

import android.content.Context
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.ExerciseCompensation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * 小组件统一数据读取 + 最近成功快照（第二轮修复 W-01/02/03/07）。
 *
 * 快照只做展示兜底，不写回 Room、不是业务数据源。
 * - W-01：带 schemaVersion + 严格字段校验，任何损坏/版本不符都整份作废；
 * - W-02：写入串行 + snapshotAt 比较，旧结果不覆盖新结果；写失败保留旧快照；
 * - W-03：新鲜度阈值集中配置，超时快照仍可显示但标注更新时间；
 * - W-07：训练/睡眠分项可用状态，读取失败显示 --，不伪装成真实 0。
 */
object WidgetSnapshotRepo {

    private const val PREFS = "qingji_widget_snapshot"
    private const val KEY_JSON = "last_good_json"
    private const val KEY_SCHEMA = "schemaVersion"

    /** W-03：新鲜度阈值（30 分钟）。超过仍显示旧值，但必须标注更新时间。 */
    const val FRESH_THRESHOLD_MS = 30L * 60 * 1000

    /** W-02：进程内串行化快照写入（Manifest 无 android:process，Widget 与 App 同进程）。 */
    private val writeMutex = Mutex()

    /** 新鲜度分类（W-03）：日期/时区有效性与新鲜度是两个独立判断。 */
    enum class Freshness { FRESH, STALE, NO_SNAPSHOT }

    fun freshnessOf(data: WidgetData, now: Long = System.currentTimeMillis()): Freshness {
        if (!data.hasSnapshot || data.dataDate == null) return Freshness.NO_SNAPSHOT
        return if (now - data.snapshotAt > FRESH_THRESHOLD_MS) Freshness.STALE else Freshness.FRESH
    }

    /** 从业务源现查，成功即刷新持久化快照。任一步失败抛异常，由调用方降级。 */
    suspend fun loadFresh(context: Context): WidgetData {
        restoreZone(context)
        val today = AppClock.today()
        val settings = SettingsStore.observe(context).first()
        val repo = QingJiRepository(context)
        val profile = repo.observeProfile().first()
        val summary = repo.observeDailySummary(today).first()
        val water = repo.observeWaterMl(today).first() ?: 0
        val latest = repo.latestWeight()?.weightKg
        val exerciseKcal = HealthRepository(context).observeDailyExerciseCalories(today).first()

        // W-07：训练/睡眠分别读取、分别可用，一项失败不影响另一项，也不伪装成真实 0。
        val daySummary = try {
            com.qingji.app.data.repository.DailyHealthRepository(context).summaryFor(today)
        } catch (e: Exception) { null }
        val available = daySummary != null

        val goal = profile?.dailyKcalGoal?.takeIf { it > 0 } ?: 2000
        val eaten = (summary?.totalKcal ?: 0.0).toInt()
        val remaining = ExerciseCompensation.remainingWithBonus(
            goalKcal = goal,
            eatenKcal = eaten,
            exerciseKcal = exerciseKcal,
            enabled = settings.exerciseCompensation,
            pct = settings.exerciseCompensationPct
        )
        val data = WidgetData(
            goalKcal = goal,
            eatenKcal = eaten,
            remainingKcal = remaining,
            protein = (summary?.totalProtein ?: 0.0).toInt(),
            proteinGoal = profile?.proteinGoalG ?: 0,
            carb = (summary?.totalCarb ?: 0.0).toInt(),
            carbGoal = profile?.carbGoalG ?: 0,
            fat = (summary?.totalFat ?: 0.0).toInt(),
            fatGoal = profile?.fatGoalG ?: 0,
            water = water,
            waterGoal = settings.waterGoalMl,
            hasSnapshot = true,
            latestWeight = latest,
            unit = profile?.unit ?: "kg",
            workoutAvailable = available,
            sleepAvailable = available,
            workoutMinutes = daySummary?.workoutDurationMinutes ?: 0,
            sleepMinutes = daySummary?.sleepDurationMinutes ?: 0,
            dataDate = today,
            zoneId = AppClock.zone.id,
            snapshotAt = System.currentTimeMillis()
        )
        saveSnapshot(context, data)
        return data
    }

    /**
     * 读最近成功快照。仅当版本/日期/时区都合法且匹配今天才可用；
     * 版本不符或字段损坏直接作废（W-01），绝不用默认值拼出"有效"快照。
     */
    suspend fun loadValidSnapshot(context: Context): WidgetData? {
        restoreZone(context)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        val d = try {
            fromJson(JSONObject(raw))
        } catch (e: Exception) {
            // W-01：解析失败 → 整份作废，记录日志但不覆盖业务数据
            android.util.Log.w("WidgetSnapshotRepo", "快照损坏或版本不兼容，已作废", e)
            return null
        }
        if (d.dataDate == null || d.dataDate != AppClock.today() || d.zoneId != AppClock.zone.id) {
            return null
        }
        return d
    }

    /** 统一降级：成功用新数据；失败用当天有效快照（stale）；都没有则明确空态。 */
    suspend fun load(context: Context): WidgetData = try {
        loadFresh(context)
    } catch (e: Exception) {
        loadValidSnapshot(context)?.copy(stale = true)
            ?: WidgetData(stale = true, hasSnapshot = false, dataDate = AppClock.today(), zoneId = AppClock.zone.id)
    }

    /** 恢复记录时区：统一走 BusinessClock（小组件独立进程不执行 MainActivity）。 */
    private suspend fun restoreZone(context: Context) {
        try {
            com.qingji.app.domain.BusinessClock.restore(context)
        } catch (e: Exception) { /* 读设置失败：沿用当前 zone，不阻断读取 */ }
    }

    /**
     * W-02：串行写入 + 顺序保护。写入前比较 snapshotAt，只有更新才写；
     * 检查与写入在同一 Mutex 内原子完成；写失败不动旧快照。
     */
    private suspend fun saveSnapshot(context: Context, data: WidgetData) {
        writeMutex.withLock {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val existingRaw = prefs.getString(KEY_JSON, null)
                val existingAt = if (existingRaw != null) {
                    try {
                        fromJson(JSONObject(existingRaw)).snapshotAt
                    } catch (e: Exception) {
                        Long.MIN_VALUE // 旧快照损坏：视为最旧，允许新结果覆盖
                    }
                } else Long.MIN_VALUE

                if (data.snapshotAt <= existingAt) return@withLock // 旧结果不覆盖新结果

                prefs.edit().putString(KEY_JSON, toJson(data).toString()).commit()
            } catch (e: Exception) {
                // 写失败保留上一份有效快照，不影响本次展示
                android.util.Log.w("WidgetSnapshotRepo", "快照写入失败，保留上一份", e)
            }
        }
    }

    // ---------- 序列化：严格校验（W-01） ----------

    internal fun toJson(d: WidgetData) = JSONObject().apply {
        put(KEY_SCHEMA, WidgetData.SCHEMA_VERSION)
        put("goalKcal", d.goalKcal); put("eatenKcal", d.eatenKcal); put("remainingKcal", d.remainingKcal)
        put("protein", d.protein); put("proteinGoal", d.proteinGoal)
        put("carb", d.carb); put("carbGoal", d.carbGoal)
        put("fat", d.fat); put("fatGoal", d.fatGoal)
        put("water", d.water); put("waterGoal", d.waterGoal)
        put("latestWeight", d.latestWeight ?: JSONObject.NULL)
        put("unit", d.unit)
        put("workoutAvailable", d.workoutAvailable)
        put("sleepAvailable", d.sleepAvailable)
        put("workoutMinutes", d.workoutMinutes)
        put("sleepMinutes", d.sleepMinutes)
        put("dataDate", d.dataDate?.toString() ?: "")
        put("zoneId", d.zoneId); put("snapshotAt", d.snapshotAt)
    }

    /**
     * W-01：严格解析。版本不符、字段缺失、类型错误、非法数值都抛异常（整份作废）。
     * 绝不使用业务默认值（2000/0）掩盖损坏。
     */
    internal fun fromJson(o: JSONObject): WidgetData {
        if (!o.has(KEY_SCHEMA) || o.optInt(KEY_SCHEMA, -1) != WidgetData.SCHEMA_VERSION) {
            throw JSONException("schemaVersion missing or incompatible")
        }
        val goal = strictInt(o, "goalKcal", 1, 100_000)
        val eaten = strictInt(o, "eatenKcal", 0, 100_000)
        val remaining = strictInt(o, "remainingKcal", -100_000, 100_000)
        val water = strictInt(o, "water", 0, 100_000)
        val waterGoal = strictInt(o, "waterGoal", 1, 100_000)
        val protein = strictInt(o, "protein", 0, 100_000)
        val carb = strictInt(o, "carb", 0, 100_000)
        val fat = strictInt(o, "fat", 0, 100_000)
        val snapshotAt = strictLong(o, "snapshotAt", 1L)
        val dateStr = strictString(o, "dataDate")
        val zoneId = strictString(o, "zoneId")
        val dataDate = try { LocalDate.parse(dateStr) } catch (e: Exception) {
            throw JSONException("bad dataDate")
        }
        if (zoneId.isBlank()) throw JSONException("empty zoneId")

        val weight = if (o.isNull("latestWeight")) null else {
            val w = o.getDouble("latestWeight")
            if (!w.isFinite() || w < 0.0 || w > 1000.0) throw JSONException("bad latestWeight")
            w
        }

        // W-07：分项可用状态 + 分钟数严格校验
        val workoutAvailable = o.getBoolean("workoutAvailable")
        val sleepAvailable = o.getBoolean("sleepAvailable")
        val workoutMinutes = strictInt(o, "workoutMinutes", 0, 100_000)
        val sleepMinutes = strictInt(o, "sleepMinutes", 0, 100_000)

        return WidgetData(
            goalKcal = goal, eatenKcal = eaten, remainingKcal = remaining,
            protein = protein, proteinGoal = strictInt(o, "proteinGoal", 0, 100_000),
            carb = carb, carbGoal = strictInt(o, "carbGoal", 0, 100_000),
            fat = fat, fatGoal = strictInt(o, "fatGoal", 0, 100_000),
            water = water, waterGoal = waterGoal,
            latestWeight = weight,
            unit = strictString(o, "unit"),
            workoutAvailable = workoutAvailable, sleepAvailable = sleepAvailable,
            workoutMinutes = workoutMinutes, sleepMinutes = sleepMinutes,
            hasSnapshot = true,
            dataDate = dataDate, zoneId = zoneId, snapshotAt = snapshotAt
        )
    }

    private fun strictInt(o: JSONObject, key: String, min: Int, max: Int): Int {
        val v = o.getInt(key)
        if (v !in min..max) throw JSONException("$key out of range: $v")
        return v
    }

    private fun strictLong(o: JSONObject, key: String, min: Long): Long {
        val v = o.getLong(key)
        if (v < min) throw JSONException("$key out of range: $v")
        return v
    }

    private fun strictString(o: JSONObject, key: String): String = o.getString(key)

    /** 测试用：清空快照。 */
    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_JSON).commit()
    }
}
