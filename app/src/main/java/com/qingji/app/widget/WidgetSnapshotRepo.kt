package com.qingji.app.widget

import android.content.Context
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.ExerciseCompensation
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDate

/**
 * 小组件统一数据读取 + 最近成功快照（阶段 1）。
 *
 * 口径与 App 首页一致（用户已确认）：
 * - 饮水目标读设置 waterGoalMl（默认 3200），不再读档案 waterGoalMl（默认 2000）；
 * - 「今日剩余」用 ExerciseCompensation.remainingWithBonus（含运动补偿），
 *   与 HomeCards 主数字同一公式，不再用 goal-eaten 旧口径；
 * - 热量/营养取 QingJiRepository.observeDailySummary（与首页同一条 DAO SUM SQL）。
 *
 * 快照只做展示兜底，不写回 Room、不是业务数据源；且带业务日期 + 时区 + 时间，
 * 跨日或改时区即失效，绝不把昨天数据当今天（第二轮审计 P1-02）。
 */
object WidgetSnapshotRepo {

    private const val PREFS = "qingji_widget_snapshot"
    private const val KEY_JSON = "last_good_json"

    /** 从业务源现查，成功即刷新持久化快照。任一步失败抛异常，由调用方降级。 */
    suspend fun loadFresh(context: Context): WidgetData {
        // 独立进程/后台入口先恢复记录时区（第二轮审计 P1-05）：
        // 必须在 AppClock.today() 之前，否则日期归属会用系统时区。
        restoreZone(context)
        val today = AppClock.today()
        val settings = SettingsStore.observe(context).first()
        val repo = QingJiRepository(context)
        val profile = repo.observeProfile().first()
        val summary = repo.observeDailySummary(today).first()
        val water = repo.observeWaterMl(today).first() ?: 0
        val latest = repo.latestWeight()?.weightKg
        val exerciseKcal = HealthRepository(context).observeDailyExerciseCalories(today).first()

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
            waterGoal = settings.waterGoalMl, // 与首页一致：设置优先
            hasSnapshot = true,
            latestWeight = latest,
            unit = profile?.unit ?: "kg",
            dataDate = today,
            zoneId = AppClock.zone.id,
            snapshotAt = System.currentTimeMillis()
        )
        saveSnapshot(context, data)
        return data
    }

    /**
     * 读最近成功快照，仅当「业务日期 == 今天」且「时区 == 当前记录时区」才可用，
     * 否则返回 null（宁可显示空态，也不拿旧数据冒充今天）。
     */
    suspend fun loadValidSnapshot(context: Context): WidgetData? {
        restoreZone(context)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        val d = try { fromJson(JSONObject(raw)) } catch (e: Exception) { return null }
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

    /** 恢复记录时区：小组件独立进程不会执行 MainActivity 的 SideEffect。 */
    private suspend fun restoreZone(context: Context) {
        try {
            val zone = SettingsStore.observe(context).first().timeZone
            if (zone.isBlank()) return
            val zoneId = try { java.time.ZoneId.of(zone) } catch (e: Exception) { return }
            if (AppClock.zone.id != zoneId.id) AppClock.setZone(zone)
        } catch (e: Exception) { /* 读设置失败：沿用当前 zone，不阻断读取 */ }
    }

    private fun saveSnapshot(context: Context, d: WidgetData) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_JSON, toJson(d).toString()).apply()
        } catch (e: Exception) { /* 快照写失败不影响本次展示 */ }
    }

    private fun toJson(d: WidgetData) = JSONObject().apply {
        put("goalKcal", d.goalKcal); put("eatenKcal", d.eatenKcal); put("remainingKcal", d.remainingKcal)
        put("protein", d.protein); put("proteinGoal", d.proteinGoal)
        put("carb", d.carb); put("carbGoal", d.carbGoal)
        put("fat", d.fat); put("fatGoal", d.fatGoal)
        put("water", d.water); put("waterGoal", d.waterGoal)
        put("latestWeight", d.latestWeight ?: JSONObject.NULL)
        put("unit", d.unit)
        put("dataDate", d.dataDate?.toString() ?: "")
        put("zoneId", d.zoneId); put("snapshotAt", d.snapshotAt)
    }

    private fun fromJson(o: JSONObject) = WidgetData(
        goalKcal = o.optInt("goalKcal", 2000),
        eatenKcal = o.optInt("eatenKcal", 0),
        remainingKcal = o.optInt("remainingKcal", 2000),
        protein = o.optInt("protein"), proteinGoal = o.optInt("proteinGoal"),
        carb = o.optInt("carb"), carbGoal = o.optInt("carbGoal"),
        fat = o.optInt("fat"), fatGoal = o.optInt("fatGoal"),
        water = o.optInt("water"), waterGoal = o.optInt("waterGoal", 3200),
        latestWeight = if (o.isNull("latestWeight")) null else o.optDouble("latestWeight"),
        unit = o.optString("unit", "kg"),
        hasSnapshot = true,
        dataDate = o.optString("dataDate", "").takeIf { it.isNotBlank() }?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        },
        zoneId = o.optString("zoneId", ""),
        snapshotAt = o.optLong("snapshotAt")
    )

    /** 测试用：清空快照。 */
    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_JSON).apply()
    }
}
