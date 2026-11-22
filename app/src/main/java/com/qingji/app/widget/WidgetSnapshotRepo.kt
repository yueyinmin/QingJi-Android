package com.qingji.app.widget

import android.content.Context
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.ExerciseCompensation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    /** 阶段C/D：两个独立投影 Key（各自 schema/时间/stale，互不拖累）。 */
    private const val KEY_TODAY = "today_remaining_v1"
    private const val KEY_BODY = "body_data_v1"

    /** W-03：新鲜度阈值（30 分钟）。超过仍显示旧值，但必须标注更新时间。 */
    const val FRESH_THRESHOLD_MS = 30L * 60 * 1000

    /** W-02：进程内串行化快照写入（Manifest 无 android:process，Widget 与 App 同进程）。 */
    private val writeMutex = Mutex()

    /**
     * 预热快照 SharedPreferences：首次 getSharedPreferences 会同步把整个 XML 读进内存，
     * 若发生在添加小组件的主线程会有可感知卡顿。App 启动时在 IO 线程先碰一次。
     */
    fun warmUp(context: Context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        } catch (e: Exception) { /* 预热失败不影响功能，真正读取时会再取一次 */ }
    }

    /** 新鲜度分类（W-03）：日期/时区有效性与新鲜度是两个独立判断。 */
    enum class Freshness { FRESH, STALE, NO_SNAPSHOT }

    fun freshnessOf(data: WidgetData, now: Long = System.currentTimeMillis()): Freshness {
        if (!data.hasSnapshot || data.dataDate == null) return Freshness.NO_SNAPSHOT
        return if (now - data.snapshotAt > FRESH_THRESHOLD_MS) Freshness.STALE else Freshness.FRESH
    }

    /**
     * 一次查询的类型化结果（验收补充工单 P0）。
     * SUCCESS = 查到值；EMPTY = 查询成功但无记录（value 为 null/0，是真值）；
     * FAILED = 读库抛异常，必须显示 --，绝不拿默认值冒充。
     */
    private data class Outcome<T>(val state: ItemState, val value: T? = null) {
        companion object {
            fun <T> of(value: T?): Outcome<T> =
                if (value == null) Outcome(ItemState.EMPTY, null) else Outcome(ItemState.SUCCESS, value)
            fun <T> fail(): Outcome<T> = Outcome(ItemState.FAILED, null)
        }
    }

    /** 把 runCatching 的 Result 映射为 Outcome：异常 → FAILED；null → EMPTY；非 null → SUCCESS。 */
    private fun <T> outcomeOf(result: Result<T?>): Outcome<T> =
        result.fold(onSuccess = { Outcome.of(it) }, onFailure = { Outcome.fail() })

    /**
     * A2：查询保护统一入口。
     * - CancellationException 必须原样重抛，不能被转成 FAILED 或 stale；
     * - 普通 Exception → FAILED Outcome（UI 显示 --，不伪 0）；
     * - 不使用 catch(Throwable)，系统级错误照常向上。
     */
    /** A2 测试注入点：可让指定 label 的查询抛出取消/业务异常，验证保护语义。生产为 null。 */
    internal var queryHookForTest: ((String) -> Unit)? = null

    private suspend fun <T> query(label: String, block: suspend () -> T?): Outcome<T> {
        val t = WidgetPerf.begin("query", label)
        try {
            queryHookForTest?.invoke(label) // 测试注入也在保护范围内
            return Outcome.of(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("WidgetSnapshotRepo", "分项查询失败: $label", e)
            return Outcome.fail()
        } finally {
            WidgetPerf.end(t)
        }
    }

    /** 从业务源现查，成功即刷新持久化快照。任一步失败抛异常，由调用方降级。 */
    suspend fun loadFresh(context: Context): WidgetData {
        val trace = WidgetPerf.begin("loadFresh", "SNAPSHOT")
        restoreZone(context)
        val today = AppClock.today()
        // 8 项查询并发发起（主线程只 await 一次总时长）；每项独立 Outcome，
        // 一项失败不影响其余项，且失败项不会用默认值伪装成真实数据。
        val settings = WidgetSettingsCache.get(context)
        val repo = QingJiRepository(context)
        val healthRepo = HealthRepository(context)
        val dailyRepo = com.qingji.app.data.repository.DailyHealthRepository(context)
        val results: List<Any> = coroutineScope {
            val tasks = listOf(
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "profile")
                    val r = query("profile") { repo.observeProfile().first() }
                    WidgetPerf.end(t); r
                },
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "dailySummary")
                    val r = query("dailySummary") { repo.observeDailySummary(today).first() }
                    WidgetPerf.end(t); r
                },
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "water")
                    // 饮水无记录时 DAO 返回 null → EMPTY(0)，0 ml 是真值；异常 → FAILED。
                    val r = query("water") { repo.observeWaterMl(today).first() }
                    WidgetPerf.end(t); r
                },
                // A3：体重用 Widget 专用有效查询，排除未来日期、同日多条取最新。
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "weight")
                    val r = query("weight") { repo.latestEffectiveWeight(today)?.weightKg }
                    WidgetPerf.end(t); r
                },
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "exerciseKcal")
                    val r = query("exerciseKcal") { healthRepo.observeDailyExerciseCalories(today).first() }
                    WidgetPerf.end(t); r
                },
                // P1：训练与睡眠分别独立读取，一个数据源失败不影响另一个。
                // 口径与 DailyHealthRepository.summaryFor 一致：workout=workoutSession.durationSum，sleep=sleep.minutesSum。
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "workoutMinutes")
                    val r = query("workoutMinutes") { dailyRepo.workoutMinutesFor(today) }
                    WidgetPerf.end(t); r
                },
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "sleepMinutes")
                    val r = query("sleepMinutes") { dailyRepo.sleepMinutesFor(today) }
                    WidgetPerf.end(t); r
                },
                // 身体数据：完整体重记录（date DESC），口径与进展页一致。
                async(Dispatchers.IO) {
                    val t = WidgetPerf.begin("query", "bodyLogs")
                    val r = query("bodyLogs") { repo.observeWeightLogs().first() }
                    WidgetPerf.end(t); r
                }
            )
            tasks.awaitAll()
        }
        WidgetPerf.end(trace, cache = "queries", extra = "n=8")

        @Suppress("UNCHECKED_CAST")
        val profile = results[0] as Outcome<com.qingji.app.data.local.entity.UserProfile>
        @Suppress("UNCHECKED_CAST")
        val summary = results[1] as Outcome<com.qingji.app.data.local.dao.DailySummary>
        @Suppress("UNCHECKED_CAST")
        val water = results[2] as Outcome<Int>
        @Suppress("UNCHECKED_CAST")
        val latest = results[3] as Outcome<Double>
        @Suppress("UNCHECKED_CAST")
        val exercise = results[4] as Outcome<Double>
        @Suppress("UNCHECKED_CAST")
        val workout = results[5] as Outcome<Int>
        @Suppress("UNCHECKED_CAST")
        val sleep = results[6] as Outcome<Int>
        @Suppress("UNCHECKED_CAST")
        val bodyLogs = results[7] as Outcome<List<com.qingji.app.data.local.entity.WeightLog>>

        // ---- 身体数据：复用进展页同一套 domain 纯函数，口径一致 ----
        val bm = com.qingji.app.domain.BodyMetricsCalculator
        val bodyOk = profile.state != ItemState.FAILED && bodyLogs.state != ItemState.FAILED
        val p = profile.value
        val currentW = bm.latestWeightKg(bodyLogs.value.orEmpty(), p?.startWeight ?: 0.0)
        val targetW = p?.targetWeight ?: 0.0
        val startW = p?.startWeight ?: 0.0
        val bmi = if (p != null && currentW > 0.0 && p.heightCm > 0)
            com.qingji.app.domain.NutritionCalculator.bmi(currentW, p.heightCm) else 0.0
        val ageYears = p?.birthday?.let {
            java.time.Period.between(it, AppClock.today()).years.takeIf { y -> y > 0 }
        }
        val measuredFat = bm.latestBodyFat(bodyLogs.value.orEmpty())
        val fatDisplay = bm.bodyFatDisplay(measuredFat, bmi, ageYears, p?.gender)
        // 阶段D：统一走方向感知 goalProgress（增重/减重/维持同一口径，与进展页一致）。
        val bodyGoal = bm.goalProgress(currentW, startW, targetW)
        val lossProgress = if (bodyGoal.computable) bodyGoal.progress else 0f

        // 只有三项输入都可用时才计算剩余；否则 remainingKcal 保持默认值但 UI 显示 --。
        val canShowRemaining = profile.state != ItemState.FAILED &&
            summary.state != ItemState.FAILED &&
            exercise.state != ItemState.FAILED
        val goal = profile.value?.dailyKcalGoal?.takeIf { it > 0 } ?: 2000
        val eaten = (summary.value?.totalKcal ?: 0.0).toInt()
        val exerciseKcal = exercise.value ?: 0.0
        val remaining = if (canShowRemaining) {
            ExerciseCompensation.remainingWithBonus(
                goalKcal = goal,
                eatenKcal = eaten,
                exerciseKcal = exerciseKcal,
                enabled = settings.exerciseCompensation,
                pct = settings.exerciseCompensationPct
            )
        } else 0

        val data = WidgetData(
            goalKcal = goal,
            eatenKcal = eaten,
            remainingKcal = remaining,
            goalMode = settings.goalMode,
            exerciseKcal = exerciseKcal.toInt().coerceAtLeast(0),
            remainingFoodOnly = if (canShowRemaining) (goal - eaten) else 0,
            protein = (summary.value?.totalProtein ?: 0.0).toInt(),
            proteinGoal = profile.value?.proteinGoalG ?: 0,
            carb = (summary.value?.totalCarb ?: 0.0).toInt(),
            carbGoal = profile.value?.carbGoalG ?: 0,
            fat = (summary.value?.totalFat ?: 0.0).toInt(),
            fatGoal = profile.value?.fatGoalG ?: 0,
            water = water.value ?: 0,
            waterGoal = settings.waterGoalMl,
            profileState = profile.state,
            dietState = summary.state,
            waterState = water.state,
            exerciseState = exercise.state,
            weightState = latest.state,
            hasSnapshot = true,
            latestWeight = latest.value,
            unit = profile.value?.unit ?: "kg",
            workoutState = workout.state,
            sleepState = sleep.state,
            workoutMinutes = workout.value ?: 0,
            sleepMinutes = sleep.value ?: 0,
            bmi = bmi,
            bmiCategory = if (bmi > 0) com.qingji.app.domain.NutritionCalculator.bmiCategory(bmi) else "",
            bodyFat = fatDisplay.value,
            bodyFatSource = fatDisplay.source.name.lowercase(),
            targetWeight = targetW,
            startWeight = startW,
            lossProgress = lossProgress,
            remainToTarget = bodyGoal.remaining,
            goalReached = bodyGoal.reached,
            bodyState = if (bodyOk) ItemState.SUCCESS else ItemState.FAILED,
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
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e // A2：协程取消不能降级为 stale 或 FAILED
    } catch (e: Exception) {
        val t = WidgetPerf.begin("loadFallback", "SNAPSHOT")
        val result = loadValidSnapshot(context)?.copy(stale = true)
            ?: WidgetData(
                stale = true, hasSnapshot = false,
                dataDate = AppClock.today(), zoneId = AppClock.zone.id
            )
        WidgetPerf.end(t, cache = if (result.hasSnapshot) "snapshot_hit" else "no_snapshot")
        result
    }

    /** 恢复记录时区：统一走 BusinessClock（小组件独立进程不执行 MainActivity）。 */
    private suspend fun restoreZone(context: Context) {
        try {
            com.qingji.app.domain.BusinessClock.restore(context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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

                // 添加卡顿主因：commit() 同步写磁盘 + fsync，在 Glance 主线程阻塞几十~几百毫秒。
                // 快照只是展示兜底（非业务数据源），丢最后一次可接受 → apply() 内存态立即生效、磁盘异步。
                val t = WidgetPerf.begin("snapshotWrite", "SNAPSHOT")
                prefs.edit().putString(KEY_JSON, toJson(data).toString()).apply()
                WidgetPerf.end(t)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        put("goalMode", d.goalMode); put("exerciseKcal", d.exerciseKcal); put("remainingFoodOnly", d.remainingFoodOnly)
        put("protein", d.protein); put("proteinGoal", d.proteinGoal)
        put("carb", d.carb); put("carbGoal", d.carbGoal)
        put("fat", d.fat); put("fatGoal", d.fatGoal)
        put("water", d.water); put("waterGoal", d.waterGoal)
        put("latestWeight", d.latestWeight ?: JSONObject.NULL)
        put("unit", d.unit)
        // P0：分项状态一并落盘，旧快照（schema 1）无这些字段 → fromJson 直接作废。
        put("profileState", d.profileState.name)
        put("dietState", d.dietState.name)
        put("waterState", d.waterState.name)
        put("exerciseState", d.exerciseState.name)
        put("weightState", d.weightState.name)
        put("workoutState", d.workoutState.name)
        put("sleepState", d.sleepState.name)
        put("workoutMinutes", d.workoutMinutes)
        put("sleepMinutes", d.sleepMinutes)
        put("bmi", d.bmi); put("bmiCategory", d.bmiCategory)
        put("bodyFat", d.bodyFat ?: JSONObject.NULL); put("bodyFatSource", d.bodyFatSource)
        put("targetWeight", d.targetWeight); put("startWeight", d.startWeight)
        put("lossProgress", d.lossProgress); put("remainToTarget", d.remainToTarget)
        put("goalReached", d.goalReached)
        put("bodyState", d.bodyState.name)
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
        val goalMode = o.optString("goalMode", "lose").ifBlank { "lose" }
        val exerciseKcal = strictInt(o, "exerciseKcal", 0, 100_000)
        val remainingFoodOnly = strictInt(o, "remainingFoodOnly", -100_000, 100_000)
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

        // P0：分项状态严格解析；缺失/非法值整份作废（不静默回落默认状态）。
        val profileState = strictState(o, "profileState")
        val dietState = strictState(o, "dietState")
        val waterState = strictState(o, "waterState")
        val exerciseState = strictState(o, "exerciseState")
        val weightState = strictState(o, "weightState")
        val workoutState = strictState(o, "workoutState")
        val sleepState = strictState(o, "sleepState")
        val workoutMinutes = strictInt(o, "workoutMinutes", 0, 100_000)
        val sleepMinutes = strictInt(o, "sleepMinutes", 0, 100_000)
        val bmi = if (o.isNull("bmi")) 0.0 else o.getDouble("bmi")
        val bmiCategory = o.optString("bmiCategory", "")
        val bodyFat = if (o.isNull("bodyFat")) null else o.getDouble("bodyFat")
        val bodyFatSource = o.optString("bodyFatSource", "none")
        val targetWeight = o.optDouble("targetWeight", 0.0)
        val startWeight = o.optDouble("startWeight", 0.0)
        val lossProgress = o.optDouble("lossProgress", 0.0).toFloat().coerceIn(0f, 1f)
        val remainToTarget = o.optDouble("remainToTarget", 0.0)
        val goalReached = o.optBoolean("goalReached", false)
        val bodyState = runCatching { ItemState.valueOf(o.optString("bodyState", "FAILED")) }.getOrDefault(ItemState.FAILED)

        return WidgetData(
            goalKcal = goal, eatenKcal = eaten, remainingKcal = remaining,
            goalMode = goalMode, exerciseKcal = exerciseKcal, remainingFoodOnly = remainingFoodOnly,
            protein = protein, proteinGoal = strictInt(o, "proteinGoal", 0, 100_000),
            carb = carb, carbGoal = strictInt(o, "carbGoal", 0, 100_000),
            fat = fat, fatGoal = strictInt(o, "fatGoal", 0, 100_000),
            water = water, waterGoal = waterGoal,
            profileState = profileState, dietState = dietState, waterState = waterState,
            exerciseState = exerciseState, weightState = weightState,
            latestWeight = weight,
            unit = strictString(o, "unit"),
            workoutState = workoutState, sleepState = sleepState,
            workoutMinutes = workoutMinutes, sleepMinutes = sleepMinutes,
            bmi = bmi, bmiCategory = bmiCategory, bodyFat = bodyFat, bodyFatSource = bodyFatSource,
            targetWeight = targetWeight, startWeight = startWeight,
            lossProgress = lossProgress, remainToTarget = remainToTarget,
            goalReached = goalReached, bodyState = bodyState,
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

    private fun strictState(o: JSONObject, key: String): ItemState {
        val name = o.getString(key)
        return runCatching { ItemState.valueOf(name) }
            .getOrElse { throw JSONException("$key invalid: $name") }
    }

    /** 测试用：清空快照。 */
    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_JSON).apply()
    }

    // ---------- 阶段 C：今日剩余独立投影 ----------

    /** 只查今日剩余所需数据：档案 / 当日饮食 / 当日运动消耗 / 设置。 */
    suspend fun loadTodayRemaining(context: Context): TodayRemainingWidgetData {
        restoreZone(context)
        val today = com.qingji.app.domain.AppClock.today()
        val repo = QingJiRepository(context)
        val settings = WidgetSettingsCache.get(context)
        val profile = query("today.profile") { repo.observeProfile().first() }
        val summary = query("today.summary") { repo.observeDailySummary(today).first() }
        val exercise = query("today.exercise") {
            HealthRepository(context).observeDailyExerciseCalories(today).first()
        }
        val goalRaw = profile.value?.dailyKcalGoal ?: 0
        val goalConfigured = goalRaw > 0
        val goal = if (goalConfigured) goalRaw else 0
        val eaten = (summary.value?.totalKcal ?: 0.0).toInt()
        val kcal = exercise.value ?: 0.0
        val canCalc = goalConfigured && profile.state != ItemState.FAILED &&
            summary.state != ItemState.FAILED && exercise.state != ItemState.FAILED
        val bonus = if (canCalc) com.qingji.app.domain.ExerciseCompensation.bonusKcal(
            kcal, settings.exerciseCompensation, settings.exerciseCompensationPct
        ) else 0
        val data = TodayRemainingWidgetData(
            date = today, zoneId = com.qingji.app.domain.AppClock.zone.id,
            goalKcal = goal, eatenKcal = eaten, exerciseKcal = kcal.toInt(),
            bonusKcal = bonus,
            remainingKcal = if (canCalc) goal - eaten + bonus else 0,
            remainingFoodOnly = if (canCalc) goal - eaten else 0,
            goalMode = settings.goalMode,
            proteinCurrent = (summary.value?.totalProtein ?: 0.0).toInt(),
            proteinGoal = profile.value?.proteinGoalG ?: 0,
            carbCurrent = (summary.value?.totalCarb ?: 0.0).toInt(),
            carbGoal = profile.value?.carbGoalG ?: 0,
            fatCurrent = (summary.value?.totalFat ?: 0.0).toInt(),
            fatGoal = profile.value?.fatGoalG ?: 0,
            profileState = profile.state, dietState = summary.state, exerciseState = exercise.state,
            goalConfigured = goalConfigured,
            snapshotAt = System.currentTimeMillis(), hasSnapshot = true
        )
        saveToday(context, data)
        return data
    }

    /** 今日剩余投影读取：新 Key 不存在时安全降级为空态，不复用大快照的伪默认值。 */
    suspend fun loadToday(context: Context): TodayRemainingWidgetData {
        restoreZone(context)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TODAY, null)
            ?: return TodayRemainingWidgetData(stale = true, hasSnapshot = false)
        return try {
            val d = todayFromJson(org.json.JSONObject(raw))
            if (d.schemaVersion != TodayRemainingWidgetData.SCHEMA_VERSION ||
                d.date != com.qingji.app.domain.AppClock.today() ||
                d.zoneId != com.qingji.app.domain.AppClock.zone.id
            ) TodayRemainingWidgetData(stale = true, hasSnapshot = false)
            else d
        } catch (e: Exception) {
            TodayRemainingWidgetData(stale = true, hasSnapshot = false)
        }
    }

    private suspend fun saveToday(context: Context, d: TodayRemainingWidgetData) {
        writeMutex.withLock {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val existing = prefs.getString(KEY_TODAY, null)
                val at = if (existing != null) {
                    try { todayFromJson(org.json.JSONObject(existing)).snapshotAt } catch (e: Exception) { Long.MIN_VALUE }
                } else Long.MIN_VALUE
                if (d.snapshotAt <= at) return@withLock
                prefs.edit().putString(KEY_TODAY, todayToJson(d).toString()).apply()
            } catch (e: Exception) { /* 快照写失败保留上一份 */ }
        }
    }

    private fun todayToJson(d: TodayRemainingWidgetData) = org.json.JSONObject().apply {
        put("schemaVersion", d.schemaVersion)
        put("date", d.date?.toString().orEmpty()); put("zoneId", d.zoneId)
        put("goalKcal", d.goalKcal); put("eatenKcal", d.eatenKcal)
        put("exerciseKcal", d.exerciseKcal); put("bonusKcal", d.bonusKcal)
        put("remainingKcal", d.remainingKcal); put("remainingFoodOnly", d.remainingFoodOnly)
        put("goalMode", d.goalMode); put("goalConfigured", d.goalConfigured)
        put("proteinCurrent", d.proteinCurrent); put("proteinGoal", d.proteinGoal)
        put("carbCurrent", d.carbCurrent); put("carbGoal", d.carbGoal)
        put("fatCurrent", d.fatCurrent); put("fatGoal", d.fatGoal)
        put("profileState", d.profileState.name); put("dietState", d.dietState.name)
        put("exerciseState", d.exerciseState.name)
        put("snapshotAt", d.snapshotAt); put("stale", d.stale); put("hasSnapshot", d.hasSnapshot)
    }

    // ---------- 阶段 D：身体数据独立投影 ----------

    /** 只查身体数据所需：档案 + 完整体重记录。不查饮食/运动/睡眠/饮水。 */
    suspend fun loadBodyData(context: Context): BodyDataWidgetData {
        restoreZone(context)
        val today = com.qingji.app.domain.AppClock.today()
        val repo = QingJiRepository(context)
        val profile = query("body.profile") { repo.observeProfile().first() }
        val logs = query("body.logs") { repo.observeWeightLogs().first() }
        val bm = com.qingji.app.domain.BodyMetricsCalculator
        val p = profile.value
        val all = logs.value.orEmpty()
        val startW = p?.startWeight ?: 0.0
        val targetW = p?.targetWeight ?: 0.0
        val currentW = bm.latestWeightKg(all, startW)
        val bmi = if (p != null && currentW > 0.0 && p.heightCm > 0)
            com.qingji.app.domain.NutritionCalculator.bmi(currentW, p.heightCm) else 0.0
        val ageYears = p?.birthday?.let {
            java.time.Period.between(it, today).years.takeIf { y -> y > 0 }
        }
        val fat = bm.bodyFatDisplay(bm.latestBodyFat(all), bmi, ageYears, p?.gender)
        val goal = bm.goalProgress(currentW, startW, targetW)
        val data = BodyDataWidgetData(
            latestWeight = currentW, unit = p?.unit ?: "kg", heightCm = p?.heightCm ?: 0.0,
            targetWeight = targetW, startWeight = startW,
            bmi = bmi, bmiCategory = if (bmi > 0) com.qingji.app.domain.NutritionCalculator.bmiCategory(bmi) else "",
            bodyFat = fat.value, bodyFatSource = fat.source.name.lowercase(),
            lossProgress = if (goal.computable) goal.progress else 0f,
            remainToTarget = goal.remaining, goalReached = goal.reached,
            goalDirection = goal.direction, goalComputable = goal.computable,
            latestRecordDate = all.firstOrNull { it.weightKg > 0 }?.date,
            recordCount = all.count { it.weightKg > 0 },
            bodyState = if (profile.state != ItemState.FAILED && logs.state != ItemState.FAILED)
                ItemState.SUCCESS else ItemState.FAILED,
            date = today, zoneId = com.qingji.app.domain.AppClock.zone.id,
            snapshotAt = System.currentTimeMillis(), hasSnapshot = true
        )
        saveBody(context, data)
        return data
    }

    suspend fun loadBody(context: Context): BodyDataWidgetData {
        restoreZone(context)
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BODY, null)
            ?: return BodyDataWidgetData(stale = true, hasSnapshot = false)
        return try {
            val d = bodyFromJson(org.json.JSONObject(raw))
            if (d.schemaVersion != BodyDataWidgetData.SCHEMA_VERSION ||
                d.date != com.qingji.app.domain.AppClock.today() ||
                d.zoneId != com.qingji.app.domain.AppClock.zone.id
            ) BodyDataWidgetData(stale = true, hasSnapshot = false) else d
        } catch (e: Exception) {
            BodyDataWidgetData(stale = true, hasSnapshot = false)
        }
    }

    private suspend fun saveBody(context: Context, d: BodyDataWidgetData) {
        writeMutex.withLock {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val existing = prefs.getString(KEY_BODY, null)
                val at = if (existing != null) {
                    try { bodyFromJson(org.json.JSONObject(existing)).snapshotAt } catch (e: Exception) { Long.MIN_VALUE }
                } else Long.MIN_VALUE
                if (d.snapshotAt <= at) return@withLock
                prefs.edit().putString(KEY_BODY, bodyToJson(d).toString()).apply()
            } catch (e: Exception) { /* 保留上一份 */ }
        }
    }

    private fun bodyToJson(d: BodyDataWidgetData) = org.json.JSONObject().apply {
        put("schemaVersion", d.schemaVersion)
        put("date", d.date?.toString().orEmpty()); put("zoneId", d.zoneId)
        put("latestWeight", d.latestWeight); put("unit", d.unit); put("heightCm", d.heightCm)
        put("targetWeight", d.targetWeight); put("startWeight", d.startWeight)
        put("bmi", d.bmi); put("bmiCategory", d.bmiCategory)
        put("bodyFat", d.bodyFat ?: org.json.JSONObject.NULL); put("bodyFatSource", d.bodyFatSource)
        put("lossProgress", d.lossProgress); put("remainToTarget", d.remainToTarget)
        put("goalReached", d.goalReached); put("goalDirection", d.goalDirection.name)
        put("goalComputable", d.goalComputable)
        put("latestRecordDate", d.latestRecordDate?.toString().orEmpty())
        put("recordCount", d.recordCount); put("bodyState", d.bodyState.name)
        put("snapshotAt", d.snapshotAt); put("stale", d.stale); put("hasSnapshot", d.hasSnapshot)
    }

    private fun bodyFromJson(o: org.json.JSONObject) = BodyDataWidgetData(
        schemaVersion = o.getInt("schemaVersion"),
        date = o.optString("date").takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it) },
        zoneId = o.optString("zoneId"),
        latestWeight = o.optDouble("latestWeight", 0.0), unit = o.optString("unit", "kg"),
        heightCm = o.optDouble("heightCm", 0.0), targetWeight = o.optDouble("targetWeight", 0.0),
        startWeight = o.optDouble("startWeight", 0.0),
        bmi = o.optDouble("bmi", 0.0), bmiCategory = o.optString("bmiCategory", ""),
        bodyFat = if (o.isNull("bodyFat")) null else o.getDouble("bodyFat"),
        bodyFatSource = o.optString("bodyFatSource", "none"),
        lossProgress = o.optDouble("lossProgress", 0.0).toFloat().coerceIn(0f, 1f),
        remainToTarget = o.optDouble("remainToTarget", 0.0),
        goalReached = o.optBoolean("goalReached", false),
        goalDirection = runCatching {
            com.qingji.app.domain.BodyMetricsCalculator.GoalDirection.valueOf(o.optString("goalDirection", "LOSE"))
        }.getOrDefault(com.qingji.app.domain.BodyMetricsCalculator.GoalDirection.LOSE),
        goalComputable = o.optBoolean("goalComputable", false),
        latestRecordDate = o.optString("latestRecordDate").takeIf { it.isNotBlank() }
            ?.let { java.time.LocalDate.parse(it) },
        recordCount = o.optInt("recordCount", 0),
        bodyState = runCatching { ItemState.valueOf(o.getString("bodyState")) }.getOrDefault(ItemState.FAILED),
        snapshotAt = o.getLong("snapshotAt"), stale = o.optBoolean("stale", false),
        hasSnapshot = o.optBoolean("hasSnapshot", true)
    )

    private fun todayFromJson(o: org.json.JSONObject) = TodayRemainingWidgetData(
        schemaVersion = o.getInt("schemaVersion"),
        date = o.optString("date").takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it) },
        zoneId = o.optString("zoneId"),
        goalKcal = o.getInt("goalKcal"), eatenKcal = o.getInt("eatenKcal"),
        exerciseKcal = o.getInt("exerciseKcal"), bonusKcal = o.getInt("bonusKcal"),
        remainingKcal = o.getInt("remainingKcal"), remainingFoodOnly = o.getInt("remainingFoodOnly"),
        goalMode = o.optString("goalMode", "lose"),
        goalConfigured = o.optBoolean("goalConfigured", true),
        proteinCurrent = o.getInt("proteinCurrent"), proteinGoal = o.getInt("proteinGoal"),
        carbCurrent = o.getInt("carbCurrent"), carbGoal = o.getInt("carbGoal"),
        fatCurrent = o.getInt("fatCurrent"), fatGoal = o.getInt("fatGoal"),
        profileState = ItemState.valueOf(o.getString("profileState")),
        dietState = ItemState.valueOf(o.getString("dietState")),
        exerciseState = ItemState.valueOf(o.getString("exerciseState")),
        snapshotAt = o.getLong("snapshotAt"), stale = o.optBoolean("stale", false),
        hasSnapshot = o.optBoolean("hasSnapshot", true)
    )
}
