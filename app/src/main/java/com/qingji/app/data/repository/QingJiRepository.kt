package com.qingji.app.data.repository

import com.qingji.app.domain.AppClock
import android.content.Context
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.SeedData
import com.qingji.app.data.local.dao.DailySummary
import com.qingji.app.data.local.dao.MealSummary
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.prefs.ApiKeyStore
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.remote.AiClient
import com.qingji.app.data.remote.AiException
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.data.remote.OpenFoodFactsClient
import com.qingji.app.domain.NutritionMatch
import com.qingji.app.domain.Per100g
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * ============================================================
 * 数据仓库：界面只跟它打交道，不直接碰数据库和网络
 * ------------------------------------------------------------
 * 好处：以后想换数据库、换 AI 服务商，只改这一个文件。
 * 所有返回 Flow 的方法都是「数据一变，界面自动跟着变」。
 * ============================================================
 */
class QingJiRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val aiClient = AiClient()
    private val foodApi = OpenFoodFactsClient()

    // ---------- 个人档案 ----------
    fun observeProfile(): Flow<UserProfile?> = db.userProfileDao().observe()
    suspend fun saveProfile(profile: UserProfile) = db.userProfileDao().save(profile)

    // ---------- AI 配置 ----------
    fun observeAiConfig(): Flow<AiConfig?> = db.aiConfigDao().observe()
    suspend fun getAiConfig(): AiConfig? = db.aiConfigDao().get()
    suspend fun saveAiConfig(config: AiConfig) = db.aiConfigDao().save(config)

    /** Key 单独走加密存储，不进数据库。 */
    fun getApiKey(): String = ApiKeyStore.load(context).orEmpty()
    fun saveApiKey(key: String) = ApiKeyStore.save(context, key)
    fun hasApiKey(): Boolean = ApiKeyStore.isSet(context)

    // ---------- 首页当天数据 ----------
    fun observeDailySummary(date: LocalDate): Flow<DailySummary?> = db.foodLogDao().observeDailySummary(date)
    fun observeMealSummary(date: LocalDate): Flow<List<MealSummary>> = db.foodLogDao().observeMealSummary(date)
    fun observeFoodLogs(date: LocalDate): Flow<List<FoodLog>> = db.foodLogDao().observeByDate(date)
    fun observeWaterMl(date: LocalDate): Flow<Int?> = db.waterLogDao().observeTodayMl(date)

    // ---------- 体重 ----------
    fun observeWeightLogs(): Flow<List<WeightLog>> = db.weightLogDao().observeAll()
    suspend fun latestWeight(): WeightLog? = db.weightLogDao().latest()
    suspend fun saveWeight(log: WeightLog) {
        db.weightLogDao().save(log)
        db.usageStatDao().increment(log.date, "weight")
        // 桌面小组件显示「当前体重」，写完立刻推一把（PRD 要求 3 秒内数字变化）
        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }

    // ---------- 进展页：身体维度 / 进度照片 / 运动 / 饮水区间 ----------

    /** 全部围度记录（按日期倒序，最新的在最前）。 */
    fun observeBodyMeasures(): Flow<List<BodyMeasure>> = db.bodyMeasureDao().observeAll()

    suspend fun saveBodyMeasure(item: BodyMeasure) = db.bodyMeasureDao().save(item)

    /** 最近一次量围度是哪天。 */
    suspend fun latestBodyMeasure(): BodyMeasure? = db.bodyMeasureDao().latest()

    fun observePhotos(): Flow<List<ProgressPhoto>> = db.progressPhotoDao().observeAll()

    suspend fun addPhoto(photo: ProgressPhoto) {
        db.progressPhotoDao().insert(photo)
    }

    suspend fun deletePhoto(id: Long) = db.progressPhotoDao().deleteById(id)

    fun observeExerciseRange(from: LocalDate, to: LocalDate): Flow<List<ExerciseLog>> =
        db.exerciseLogDao().observeRange(from, to)

    suspend fun addExercise(log: ExerciseLog) {
        db.exerciseLogDao().insert(log)
        db.usageStatDao().increment(log.date, "body")
    }

    suspend fun deleteExercise(id: Long) = db.exerciseLogDao().deleteById(id)

    /** 近一周饮水：取原始记录，界面里按天汇总。 */
    fun observeWaterRange(from: LocalDate, to: LocalDate): Flow<List<WaterLog>> =
        db.waterLogDao().observeRange(from, to)

    /** 近一周饮食：进展页的营养摄入模块用。 */
    fun observeFoodRange(from: LocalDate, to: LocalDate): Flow<List<FoodLog>> =
        db.foodLogDao().observeRange(from, to)

    // ---------- 运动库 ----------

    fun observeExercisePresets(): Flow<List<com.qingji.app.data.local.entity.ExercisePreset>> =
        db.exercisePresetDao().observeAll()

    suspend fun ensurePresetExercises() {
        if (db.exercisePresetDao().count() == 0) {
            db.exercisePresetDao().insertAll(SeedData.presetExercises)
        }
    }

    suspend fun setExerciseFavorite(id: Long, favorite: Boolean) =
        db.exercisePresetDao().setFavorite(id, favorite)

    // ---------- 食物库 ----------

    fun observeAllFoods(): Flow<List<Food>> = db.foodDao().observeAll()

    fun observeUserFoods(): Flow<List<Food>> = db.foodDao().observeUserFoods()

    suspend fun setFoodFavorite(id: Long, favorite: Boolean) =
        db.foodDao().setFavorite(id, favorite)

    /** 把 AI 识别出来的食物存进自定义库（source = user，不覆盖预置数据）。 */
    suspend fun addUserFood(name: String, kcalPer100g: Double, protein: Double, carb: Double, fat: Double) {
        if (name.isBlank()) return
        val exists = db.foodDao().allNames().any { it == name }
        if (exists) return
        db.foodDao().insert(
            Food(
                name = name.trim(),
                kcalPer100g = kcalPer100g,
                proteinPer100g = protein,
                carbPer100g = carb,
                fatPer100g = fat,
                source = "user",
                category = com.qingji.app.domain.FoodCategory.guess(name)
            )
        )
    }

    // ---------- 轻断食 ----------

    fun observeFasting(date: LocalDate): Flow<com.qingji.app.data.local.entity.FastingLog?> =
        db.fastingLogDao().observe(date)

    fun observeFastingRange(from: LocalDate, to: LocalDate): Flow<List<com.qingji.app.data.local.entity.FastingLog>> =
        db.fastingLogDao().observeRange(from, to)

    suspend fun saveFasting(log: com.qingji.app.data.local.entity.FastingLog) {
        db.fastingLogDao().save(log)
    }

    suspend fun fastingHistory(): List<com.qingji.app.data.local.entity.FastingLog> =
        db.fastingLogDao().findAll()

    // ---------- 使用统计 ----------
    /** 「我的」页八格 + 调用统计卡共用：数据一变自动刷新。 */
    fun observeUsageAll(): Flow<List<UsageStat>> = db.usageStatDao().observeAll()

    // ---------- 食物库 ----------
    suspend fun searchFood(keyword: String): List<Food> = db.foodDao().search(keyword)
    suspend fun foodCount(): Int = db.foodDao().count()

    /** 可订阅的食物库条数：灌库完成后搜索页能自动刷新，不会一直空着。 */
    fun observeFoodCount(): Flow<Int> = db.foodDao().observeCount()

    /**
     * 把预置食物补齐（按名字去重，绝不覆盖用户自己录的）。
     * 返回这次实际补进去多少条。
     *
     * 两处会调它：
     * 1. App 启动时（QingJiApp）—— 全新安装灌全量，老版本升级只补新增的；
     * 2. 搜索页发现一条食物都没有时，让用户手动点「恢复预置食物库」。
     */
    suspend fun ensurePresetFoods(): Int {
        // 去重键用「名字 + 品牌」而不是只用名字：
        // 肯德基和麦当劳都有「薯条」，只用名字会把后者当成重复项丢掉。
        val existing = db.foodDao().findAll().map { it.name to it.brand }.toSet()
        val missing = SeedData.presetFoods
            .filter { (it.name to it.brand) !in existing }
            // 插入前按名字自动归类，食物库左侧导航才有内容
            .map { it.copy(category = com.qingji.app.domain.FoodCategory.guess(it.name, it.brand)) }
        if (missing.isNotEmpty()) db.foodDao().insertAll(missing)

        // 老用户的数据是 v3 之前灌的，category 全是默认「其他」，这里补一次分类
        backfillFoodCategory()
        return missing.size
    }

    /**
     * 给还没有分类的食物补上分类。
     * 只在启动时跑一次（只处理 category == "其他" 的），开销很小。
     */
    private suspend fun backfillFoodCategory() {
        val all = db.foodDao().findAll()
        val needsFix = all.filter { it.category == com.qingji.app.domain.FoodCategory.OTHER }
        if (needsFix.isEmpty()) return
        db.foodDao().insertAll(
            needsFix.map {
                it.copy(category = com.qingji.app.domain.FoodCategory.guess(it.name, it.brand))
            }
        )
    }

    // ---------- 写入 ----------
    /** 记一条饮食。顺手把「今天用了几次某功能」+1，对应「我的」页八格统计。 */
    suspend fun addFoodLog(log: FoodLog) {
        db.foodLogDao().insert(log)
        db.usageStatDao().increment(log.date, log.source)
        // 首页数字变了，桌面小组件也要跟着变（不等系统 30 分钟周期刷新）
        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }

    suspend fun addFoodLogs(logs: List<FoodLog>) {
        logs.forEach { addFoodLog(it) }
    }

    suspend fun deleteFoodLog(id: Long) = db.foodLogDao().deleteById(id)

    // ---------- 用餐详情 ----------

    /** 把这一餐的几条记录整体挪到别的日期 / 餐次（详情页菜单里的「改时间」「改餐次」）。 */
    suspend fun moveMeal(ids: List<Long>, newDate: LocalDate, newMealType: String) {
        if (ids.isEmpty()) return
        db.foodLogDao().moveLogs(ids, newDate, newMealType)
        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }

    /**
     * 重新识别后整体替换这一餐。
     * 用事务保证「先删后插」不会中途失败留下半餐数据。
     */
    suspend fun replaceMeal(date: LocalDate, mealType: String, logs: List<FoodLog>) {
        db.withTransaction {
            db.foodLogDao().deleteByMeal(date, mealType)
            if (logs.isNotEmpty()) db.foodLogDao().insertAll(logs)
        }
        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }

    /**
     * 带上「口味与做法」重新识别这一餐。
     * 和普通识别走同一条链路，只是额外把用户勾的少油/多糖/清蒸等规则拼进提示词。
     */
    suspend fun refineMeal(
        description: String,
        extraRule: String,
        date: LocalDate
    ): Result<AiRecognitionResult> {
        val config = getAiConfig()
            ?: return Result.failure(AiException("还没配置 AI 服务，去 设置 → AI 服务配置 填一次"))
        if (!config.enabled) {
            return Result.failure(AiException("AI 识别已关闭，去 设置 → AI 服务配置 打开开关"))
        }
        if (todayAiCalls(date) >= config.dailyLimit) {
            return Result.failure(
                AiException("今天的 AI 调用已达上限 ${config.dailyLimit} 次，手动修改仍然可用")
            )
        }

        val rule = buildRecognitionRules() + "\n\n" + extraRule
        val result = aiClient.recognizeText(config, getApiKey(), description, rule)
        if (result.isSuccess) {
            db.usageStatDao().increment(date, "ai")
            return Result.success(correctWithLocalFood(result.getOrThrow()))
        }
        return result
    }

    suspend fun addWater(ml: Int, date: LocalDate) {
        if (ml <= 0) return
        db.waterLogDao().insert(WaterLog(date = date, ml = ml))
        db.usageStatDao().increment(date, "water")
        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }

    // ---------- AI 调用（带单日熔断） ----------
    private suspend fun todayAiCalls(date: LocalDate): Int =
        db.usageStatDao().countByType(date, "ai") ?: 0

    /**
     * 用本地食物库校正 AI 的识别结果。
     *
     * 逻辑：AI 说了「白米饭 150g」，本地库里有「米饭」→ 用本地库的 116 kcal/100g
     * 算出 174 kcal，而不是信模型自己编的数字。匹配不上才保留 AI 的值。
     * 顺带把克数和热量都做一次合理性兜底（防止 0 克、9999 克、每百克 2000 千卡）。
     */
    private suspend fun correctWithLocalFood(result: AiRecognitionResult): AiRecognitionResult {
        val candidates = try {
            db.foodDao().findAll().map {
                it.name to Per100g(
                    kcal = it.kcalPer100g,
                    protein = it.proteinPer100g,
                    carb = it.carbPer100g,
                    fat = it.fatPer100g
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (candidates.isEmpty()) return result

        val items = result.items.map { item ->
            val grams = NutritionMatch.saneGrams(item.grams)
            val hit = NutritionMatch.pick(item.name, candidates)
            if (hit != null) {
                val per = NutritionMatch.compute(hit.per100g, grams)
                item.copy(
                    grams = grams,
                    kcal = per.kcal,
                    proteinG = per.protein,
                    carbG = per.carb,
                    fatG = per.fat
                )
            } else {
                item.copy(grams = grams, kcal = NutritionMatch.saneKcal(item.kcal, grams))
            }
        }
        return result.copy(items = items, totalKcal = items.sumOf { it.kcal })
    }

    /**
     * 文字识别。发起请求前先做三道检查：没配置 / 没启用 / 超上限。
     * 只有真正调用成功才计数，失败不扣额度。
     */
    suspend fun recognizeText(input: String, date: LocalDate): Result<AiRecognitionResult> {
        val config = getAiConfig()
            ?: return Result.failure(AiException("还没配置 AI 服务，去 设置 → AI 服务配置 填一次"))
        if (!config.enabled) {
            return Result.failure(AiException("AI 识别已关闭，去 设置 → AI 服务配置 打开开关"))
        }
        if (todayAiCalls(date) >= config.dailyLimit) {
            return Result.failure(
                AiException("今天的 AI 调用已达上限 ${config.dailyLimit} 次，手动搜索和拍照都还能用")
            )
        }

        val result = aiClient.recognizeText(config, getApiKey(), input, buildRecognitionRules())
        if (result.isSuccess) {
            db.usageStatDao().increment(date, "ai")
            return Result.success(correctWithLocalFood(result.getOrThrow()))
        }
        return result
    }

    /** 测试连接不走熔断计数，也不要求「已启用」。 */
    suspend fun testConnection(config: AiConfig, key: String): Result<String> =
        aiClient.testConnection(config, key)

    /** 图片识别和分析共用的一道闸：没配 / 没启用 / 超上限都在这里挡住。 */
    private suspend fun requireAi(date: LocalDate): AiConfig {
        val config = getAiConfig()
            ?: throw AiException("还没配置 AI 服务，去 设置 → AI 服务配置 填一次")
        if (!config.enabled) throw AiException("AI 识别已关闭，去 设置 → AI 服务配置 打开开关")
        if (todayAiCalls(date) >= config.dailyLimit) {
            throw AiException("今天的 AI 调用已达上限 ${config.dailyLimit} 次，手动搜索仍然可用")
        }
        return config
    }

    /** 图片识别（M3.2）。成功才计一次 AI 调用，失败不扣额度。 */
    suspend fun recognizeImage(base64Jpeg: String, date: LocalDate): Result<AiRecognitionResult> = try {
        val config = requireAi(date)
        val result = aiClient.recognizeImage(config, getApiKey(), base64Jpeg, buildRecognitionRules())
        if (result.isSuccess) {
            db.usageStatDao().increment(date, "ai")
            Result.success(correctWithLocalFood(result.getOrThrow()))
        } else {
            result
        }
    } catch (e: AiException) {
        Result.failure(e)
    }

    /**
     * 把设置页里的「识别偏好」翻成给模型的规则。
     * 这样改了设置不用改代码，下一次识别就生效。
     */
    private suspend fun buildRecognitionRules(): String {
        val s = SettingsStore.observe(context).first()
        return buildString {
            if (s.dietPreference.isNotBlank()) {
                append("用户忌口/过敏：${s.dietPreference}。涉及这些食材时要在 advice 里明确提醒。\n")
            }
            if (!s.autoMeal) {
                append("不要根据时间判断餐次，meal_type 统一输出 snack，由用户自己选。\n")
            }
            if (s.conservativeEstimate) {
                append("份量估算请偏保守（宁可略少），避免高估热量。\n")
            } else {
                append("份量估算按市场上常见的大份量给。\n")
            }

            // 忌口与过敏原（设置 → 饮食偏好与忌口）：
            // 拼进每一次 AI 识别的规则里，重新识别时也自动带上。
            val avoidRule = com.qingji.app.domain.AllergenRules.toPrompt(
                allergens = s.allergens.split(",").map { it.trim() }.filter { it.isNotBlank() },
                avoidFoods = s.avoidFoods.split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
            if (avoidRule.isNotBlank()) {
                append("\n").append(avoidRule).append("\n")
            }

            // 语言（设置 → 记录与识别 → AI 识别语言）：默认强制简体中文
            append("\n【输出语言】")
            if (s.aiLanguage == "auto") {
                append("输出语言跟随用户输入：用户用中文就全部用简体中文回答。\n")
            } else {
                append("所有输出（name、advice）必须使用简体中文，外国菜翻译成常用中文名。\n")
            }
        }.trim()
    }

    /** AI 分析（M3.8）：facts 是仓库层聚合好的数字，prompt 里不许模型编数据。 */
    suspend fun analyze(facts: String, date: LocalDate): Result<String> = try {
        val config = requireAi(date)
        val result = aiClient.analyze(config, getApiKey(), facts)
        if (result.isSuccess) db.usageStatDao().increment(date, "ai")
        result
    } catch (e: AiException) {
        Result.failure(e)
    }

    /** 今日复盘的事实材料：预算、已摄入、三大营养素、吃了几餐、饮水。 */
    suspend fun buildDailyFacts(date: LocalDate): String {
        val profile = db.userProfileDao().get()
        val summary = db.foodLogDao().observeDailySummary(date).first()
        val meals = db.foodLogDao().observeMealSummary(date).first()
        val water = db.waterLogDao().observeTodayMl(date).first() ?: 0
        val kcal = summary?.totalKcal?.toInt() ?: 0
        val goal = profile?.dailyKcalGoal ?: 0
        val mealText = if (meals.isEmpty()) "今天还没有饮食记录" else meals.joinToString("、") {
            "${it.mealType}:${it.kcal?.toInt() ?: 0}千卡/${it.itemCount}项"
        }
        return buildString {
            append("【今日复盘】日期 $date。")
            append("热量预算 ${goal}千卡，已摄入 ${kcal}千卡，差额 ${goal - kcal}千卡。")
            append("蛋白 ${summary?.totalProtein?.toInt() ?: 0}/${profile?.proteinGoalG ?: 0}g，")
            append("碳水 ${summary?.totalCarb?.toInt() ?: 0}/${profile?.carbGoalG ?: 0}g，")
            append("脂肪 ${summary?.totalFat?.toInt() ?: 0}/${profile?.fatGoalG ?: 0}g。")
            append("饮水 ${water}ml。各餐：$mealText。")
            append("请给出 1-2 条具体、可执行、不带医疗表述的建议。")
        }
    }

    /** 周报的事实材料：近 7 天平均摄入、体重变化、最常吃的食物。 */
    suspend fun buildWeeklyFacts(): String {
        val today = AppClock.today()
        val from = today.minusDays(6)
        val logs = db.foodLogDao().observeRange(from, today).first()
        val weights = db.weightLogDao().observeRange(from, today).first()
        val days = logs.map { it.date }.distinct().size.coerceAtLeast(1)
        val total = logs.sumOf { it.kcal }
        val average = (total / days).toInt()
        val topFoods = logs.groupingBy { it.foodName }.eachCount()
            .entries.sortedByDescending { it.value }.take(3)
            .joinToString("、") { "${it.key}(${it.value}次)" }
            .ifBlank { "无" }
        val firstWeight = weights.firstOrNull()?.weightKg
        val lastWeight = weights.lastOrNull()?.weightKg
        val weightText = if (firstWeight != null && lastWeight != null) {
            "${String.format("%.1f", firstWeight)}kg → ${String.format("%.1f", lastWeight)}kg"
        } else "本周没有体重记录"
        return buildString {
            append("【周报】$from 到 $today，共 $days 天有记录。")
            append("总摄入 ${total.toInt()}千卡，日均 $average 千卡。")
            append("体重：$weightText。最常吃：$topFoods。")
            append("请总结这一周，并给下周一条具体改进建议，不要医疗表述。")
        }
    }

    // ---------- M3.5 常吃食物置顶 ----------
    fun observeRecentFoodNames(): Flow<List<String>> =
        db.foodLogDao().observeRecentNames(AppClock.today().minusDays(30))

    // ---------- M3.4 条码 ----------
    /** 先查本机食物表（断网也能用）。 */
    suspend fun findFoodByBarcode(code: String): Food? = db.foodDao().findByBarcode(code)

    /** 本机没有才联网查，查不到返回中文原因。 */
    suspend fun lookupBarcodeRemote(code: String) = foodApi.lookup(code)

    /** 条码查到或手动录入后，缓存进食物表，下次同一条码直接命中。 */
    suspend fun saveFood(food: Food): Long = db.foodDao().insert(food)

    // ---------- M5 日记 ----------
    fun observeDiaries(): Flow<List<Diary>> = db.diaryDao().observeAll()

    suspend fun findDiary(id: Long): Diary? = db.diaryDao().findById(id)

    suspend fun saveDiary(diary: Diary): Long {
        val id = db.diaryDao().save(diary)
        db.usageStatDao().increment(diary.date, "diary")
        return id
    }

    /** 清空全部日记 */
    suspend fun clearAllDiaries() = db.diaryDao().clearAll()

    suspend fun deleteDiary(id: Long) = db.diaryDao().deleteById(id)

    // ============================================================
    // M6 / M7 新增：给「我的」页和提醒用的几个统计
    // 全部是一次性查询（不是 Flow），因为只在打开页面时算一次。
    // ============================================================

    /** 热量提醒用：今天有没有记过饮食。没记才提醒，记了就别烦人。 */
    suspend fun hasFoodLogToday(): Boolean =
        db.foodLogDao().countOn(com.qingji.app.domain.AppClock.today()) > 0

    /** 「本月 AI 调用」：从本月 1 号算到今天。 */
    suspend fun monthAiCalls(): Int {
        val today = com.qingji.app.domain.AppClock.today()
        return db.usageStatDao().sumByType("ai", today.withDayOfMonth(1), today) ?: 0
    }

    /**
     * 成就统计：把算徽章需要的数字一次查全。
     * 注意「连续天数」要把饮食日期和体重日期合起来算 —— 只记体重那天也算记录了。
     */
    suspend fun achievementStats(): com.qingji.app.domain.AchievementStats {
        val profile = db.userProfileDao().get()
        val weightLogs = db.weightLogDao().findAll()
        val usage = db.usageStatDao().findAll()
        val today = com.qingji.app.domain.AppClock.today()

        val activeDates = buildSet {
            db.foodLogDao().distinctDates().forEach { add(it.toString()) }
            weightLogs.forEach { add(it.date.toString()) }
        }

        val startWeight = profile?.startWeight ?: 0.0
        val latestWeight = weightLogs.maxByOrNull { it.date }?.weightKg ?: 0.0
        val lost = if (startWeight > 0.0 && latestWeight > 0.0) {
            (startWeight - latestWeight).coerceAtLeast(0.0)
        } else {
            0.0
        }

        return com.qingji.app.domain.AchievementStats(
            foodLogCount = db.foodLogDao().countAll(),
            weightLogCount = weightLogs.size,
            diaryCount = db.diaryDao().countAll(),
            waterCount = db.waterLogDao().countAll(),
            aiCount = usage.filter { it.type == "ai" }.sumOf { it.count },
            streakDays = com.qingji.app.domain.Achievements.streakDays(
                activeDates = activeDates,
                today = today.toString(),
                yesterday = today.minusDays(1).toString()
            ),
            weightLostKg = lost,
            distinctFoodCount = db.foodLogDao().countDistinctNames()
        )
    }

    /** 清空全部本地数据（设置 → 隐私与安全 → 清除本地数据）。 */
    suspend fun clearAllData() {
        db.foodLogDao().clearAll()
        db.waterLogDao().clearAll()
        db.weightLogDao().clearAll()
        db.diaryDao().clearAll()
        db.usageStatDao().clearAll()
        db.userProfileDao().clear()
    }
}
