package com.qingji.app.ui.viewmodel

import com.qingji.app.domain.AppClock
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.BarcodeRules
import com.qingji.app.domain.DiaryRules
import com.qingji.app.domain.FastingRules
import com.qingji.app.domain.FoodRanking
import com.qingji.app.domain.NutritionCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 界面和数据之间的「中间人」（ViewModel）
 * ------------------------------------------------------------
 * 为什么需要它：手机转屏、切页面时界面会被重建，如果直接在界面里
 * 查数据库，每次重建都要重查一遍，还容易内存泄漏。ViewModel 活得
 * 比界面久，数据放在这里就不会丢。
 *
 * 这里全部继承 AndroidViewModel，好处是系统会自动帮我们创建，
 * 界面里写一句 viewModel() 就能拿到，不用自己写工厂类。
 *
 * 规矩：
 * 1. 返回 StateFlow 的 = 界面订阅它，数据一变界面自动刷新。
 * 2. suspend 函数 = 界面点按钮时手动调一次的「动作」。
 * ============================================================
 */

/** 首页一屏要用的全部数据，打包成一个对象，界面只订阅这一个。 */
data class HomeUiState(
    val date: LocalDate = AppClock.today(),
    val profile: UserProfile? = null,
    val totalKcal: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarb: Double = 0.0,
    val totalFat: Double = 0.0,
    val waterMl: Int = 0,
    /** 当天运动消耗（计入热量缺口） */
    val exerciseKcal: Double = 0.0,
    val logs: List<FoodLog> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    /** 当前看的是哪天。点日期条会改它，下面所有数据自动跟着换。 */
    private val _date = MutableStateFlow(AppClock.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /**
     * flatMapLatest 的意思：日期一变，就把旧的数据库订阅取消、换成新日期的。
     * combine 把「档案 / 当天汇总 / 当天明细 / 当天饮水」四股数据合成一包。
     */
    val uiState: StateFlow<HomeUiState> = _date.flatMapLatest { d ->
        combine(
            repo.observeProfile(),
            repo.observeDailySummary(d),
            repo.observeFoodLogs(d),
            repo.observeWaterMl(d),
            repo.observeExerciseLogs(d)
        ) { profile, summary, logs, water, exercises ->
            HomeUiState(
                date = d,
                profile = profile,
                totalKcal = summary?.totalKcal ?: 0.0,
                totalProtein = summary?.totalProtein ?: 0.0,
                totalCarb = summary?.totalCarb ?: 0.0,
                totalFat = summary?.totalFat ?: 0.0,
                waterMl = water ?: 0,
                // 当天运动消耗：计入「预计热量缺口」（动得多缺口大）
                exerciseKcal = exercises.sumOf { it.kcal },
                logs = logs
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * 近 7 天汇总：首页「本周小结」卡片用（v3.4.0 · R2）。
     * 五股原始 Flow 在仓库层取区间，聚合放到纯逻辑类里算，界面只拿结论。
     * 全部本地计算，不调 AI、不联网。
     */
    private val weekFrom: java.time.LocalDate = AppClock.today().minusDays(6)
    private val weekTo: java.time.LocalDate = AppClock.today()
    val weeklySummary: StateFlow<com.qingji.app.domain.WeeklySummaryCalculator.Summary> = combine(
        repo.observeFoodRange(weekFrom, weekTo),
        repo.observeWaterRange(weekFrom, weekTo),
        repo.observeExerciseRange(weekFrom, weekTo),
        repo.observeWeightLogs(),
        repo.observeProfile()
    ) { foods, waters, exercises, weights, profile ->
        val list = (0..6).map { i ->
            val d = weekFrom.plusDays(i.toLong())
            com.qingji.app.domain.WeeklySummaryCalculator.Day(
                date = d,
                kcal = foods.filter { it.date == d }.sumOf { it.kcal },
                exerciseKcal = exercises.filter { it.date == d }.sumOf { it.kcal },
                waterMl = waters.filter { it.date == d }.sumOf { it.ml },
                hasLog = foods.any { it.date == d }
            )
        }
        val goal = profile?.dailyKcalGoal?.takeIf { it > 0 } ?: 2000
        val waterGoal = profile?.waterGoalMl?.takeIf { it > 0 } ?: 2000
        val inWeek = weights.filter { it.date >= weekFrom }
        com.qingji.app.domain.WeeklySummaryCalculator.summarize(
            days = list,
            dailyGoal = goal,
            waterGoal = waterGoal,
            startWeight = inWeek.minByOrNull { it.date }?.weightKg,
            endWeight = inWeek.maxByOrNull { it.date }?.weightKg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.qingji.app.domain.WeeklySummaryCalculator.summarize(emptyList(), 2000, 2000, null, null)
    )

    fun selectDate(d: LocalDate) {
        _date.value = d
    }

    fun backToToday() {
        _date.value = AppClock.today()
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { repo.addWater(ml, _date.value) }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { repo.deleteFoodLog(id) }
    }

    /** 首页点水杯 → 首页设置里能直接调气泡量。 */
    fun setWaterBubbles(level: Int) {
        viewModelScope.launch {
            com.qingji.app.data.prefs.SettingsStore.set(
                getApplication(),
                com.qingji.app.data.prefs.SettingsKeys.WATER_BUBBLES,
                level.coerceIn(0, 3)
            )
        }
    }

    /** 首页每餐卡片是否显示营养素比例条。 */
    fun setShowMealMacro(show: Boolean) {
        viewModelScope.launch {
            com.qingji.app.data.prefs.SettingsStore.set(
                getApplication(),
                com.qingji.app.data.prefs.SettingsKeys.SHOW_MEAL_MACRO,
                show
            )
        }
    }
}

/** 手动搜索食物页（M3.5：常吃的自动置顶）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchFoodViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /**
     * 关键词 / 「最近 30 天吃过什么」/ **食物库条数** 任一变化，都会重新查库并重排。
     * 空关键词会列出前 50 条，正好当默认列表。
     *
     * v1.0.4：标准食物库是随 APK 预置的独立数据库，Room 首次打开时自动拷出来，
     * 这里仍然订阅条数，是为了兼容「首次开库稍慢」和用户自己新增食物的实时刷新。
     */
    val results: StateFlow<List<Food>> = combine(
        _keyword,
        repo.observeRecentFoodNames(),
        repo.observeFoodCount()
    ) { keyword, recentNames, _ -> keyword to recentNames }
        .mapLatest { (keyword, recentNames) ->
            val base = repo.searchFood(keyword.trim())
            FoodRanking.rank(base, { it.name }, FoodRanking.countRecent(recentNames))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 食物库里现在有多少条。-1 表示「还没查到」。
     * 界面用它区分三种状态：还在载入 / 一条都没有 / 有数据但没搜到。
     */
    val foodCount: StateFlow<Int> = repo.observeFoodCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** 最近搜过的词，搜索框下面一排快捷入口。 */
    val searchHistory: StateFlow<List<String>> = repo.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 标准食物库现在是随 APK 走的只读库，不需要「补数据」。
     * 按钮保留只是给「库没读出来」的情况一个手动重试的机会，返回当前能读到的条数。
     */
    suspend fun restorePresetFoods(): Int = repo.ensurePresetFoods()

    /** 点中一条结果时，把这次搜的词记进历史；下次打开搜索页一点就能用。 */
    fun rememberSearch() {
        val key = _keyword.value.trim()
        if (key.isBlank()) return
        viewModelScope.launch { repo.rememberSearch(key) }
    }

    fun onKeywordChange(value: String) {
        _keyword.value = value
    }

    /**
     * 把食物库的一条换算成一条饮食记录。
     * 表里存的是「每 100 克」的营养，所以要乘 克数/100。
     */
    suspend fun addFood(
        food: Food,
        grams: Double,
        mealType: String,
        date: LocalDate
    ) {
        val ratio = grams / 100.0
        repo.addFoodLog(
            FoodLog(
                date = date,
                mealType = mealType,
                foodName = food.name,
                grams = grams,
                kcal = food.kcalPer100g * ratio,
                proteinG = food.proteinPer100g * ratio,
                carbG = food.carbPer100g * ratio,
                fatG = food.fatPer100g * ratio,
                source = "manual"
            )
        )
    }
}

/** 文字 AI 识别页。识别结果的可编辑列表放在界面里，这里只管发请求和落库。 */
class TextAiViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun recognize(input: String, date: LocalDate): Result<AiRecognitionResult> =
        repo.recognizeText(input, date)

    /** v3.4.0：保存这一餐时顺手记下用户改过的名字，下次识别自动套用。 */
    suspend fun save(
        logs: List<FoodLog>,
        nameCorrections: List<Pair<String, String>> = emptyList()
    ) {
        repo.rememberAiNameCorrections(nameCorrections)
        repo.addFoodLogs(logs)
    }
}

/** 体重记录页。 */
class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun latest(): WeightLog? = repo.latestWeight()

    suspend fun save(weightKg: Double, bodyFat: Double?, note: String, date: LocalDate) {
        repo.saveWeight(
            WeightLog(
                date = date,
                weightKg = weightKg,
                bodyFat = bodyFat,
                note = note.ifBlank { null }
            )
        )
    }
}

/** AI 服务配置页。 */
class AiConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val config: StateFlow<AiConfig?> = repo.observeAiConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 是否已经存过 Key。存过就在输入框显示占位提示，避免把密文明文回显。 */
    private val _hasKey = MutableStateFlow(repo.hasApiKey())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    /** 输入框留空时继续用旧的 Key，填了新值才覆盖。 */
    fun effectiveKey(input: String): String = input.trim().ifBlank { repo.getApiKey() }

    suspend fun save(config: AiConfig, keyInput: String) {
        repo.saveAiConfig(config)
        if (keyInput.isNotBlank()) {
            repo.saveApiKey(keyInput)
            _hasKey.value = true
        }
    }

    suspend fun test(config: AiConfig, keyInput: String): Result<String> =
        repo.testConnection(config, effectiveKey(keyInput))
}

/** 个人资料页。保存时顺手按公式重算每日热量与营养目标。 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun save(profile: UserProfile, mode: String) {
        val age = profile.birthday?.let {
            java.time.Period.between(it, AppClock.today()).years
        } ?: 25
        val currentWeight = repo.latestWeight()?.weightKg
            ?: profile.startWeight.takeIf { it > 0 }
            ?: 70.0

        val goal = NutritionCalculator.compute(
            gender = profile.gender,
            age = age,
            heightCm = profile.heightCm,
            weightKg = currentWeight,
            activityLevel = profile.activityLevel,
            mode = mode
        )

        repo.saveProfile(
            profile.copy(
                dailyKcalGoal = goal.kcal,
                proteinGoalG = goal.proteinG,
                carbGoalG = goal.carbG,
                fatGoalG = goal.fatG
            )
        )

        // 目标模式同时写回 DataStore。
        // 以前只有首次引导会写这个键，用户在资料页把「减重」改成「增肌」后，
        // DataStore 里还是旧的，别的读 goalMode 的地方（首页目标状态、引导重进）
        // 就会和档案里的目标对不上。这里补上，让两处永远一致。
        SettingsStore.set(getApplication(), SettingsKeys.GOAL_MODE, mode)
    }
}

/** 进展页。时间范围用「往前看多少天」表示，null = 全部。 */
class ProgressViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    /** 运动库（预置 + 收藏置顶） */
    val exercisePresets: StateFlow<List<com.qingji.app.data.local.entity.ExercisePreset>> =
        repo.observeExercisePresets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleExerciseFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setExerciseFavorite(id, favorite) }
    }

    private val _rangeDays = MutableStateFlow<Int?>(30)
    val rangeDays: StateFlow<Int?> = _rangeDays.asStateFlow()

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val logs: StateFlow<List<WeightLog>> = repo.observeWeightLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 近一周的区间数据（进展页新增模块用） ----------
    // 统一以「今天往前 6 天」为窗口，共 7 天。
    private val weekStart: LocalDate get() = AppClock.today().minusDays(6)

    /** 近一周每天喝了多少水（7 条，缺的那天是 0）。 */
    val waterWeek: StateFlow<List<Pair<LocalDate, Int>>> = repo
        .observeWaterRange(weekStart, AppClock.today())
        .map { list ->
            val byDate = list.groupBy { it.date }.mapValues { (_, items) -> items.sumOf { it.ml } }
            (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                date to (byDate[date] ?: 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近一周每天的饮食记录（用于算热量和三大营养素）。 */
    val foodWeek: StateFlow<List<FoodLog>> = repo
        .observeFoodRange(weekStart, AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本周运动记录。 */
    val exerciseWeek: StateFlow<List<ExerciseLog>> = repo
        .observeExerciseRange(weekStart, AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部围度记录（最新在前）。 */
    val bodyMeasures: StateFlow<List<BodyMeasure>> = repo.observeBodyMeasures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 进度照片（最新在前）。 */
    val photos: StateFlow<List<ProgressPhoto>> = repo.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(days: Int?) {
        _rangeDays.value = days
    }

    /** 快捷按钮「水」：直接加 200ml。 */
    fun addWater(ml: Int) {
        viewModelScope.launch { repo.addWater(ml, AppClock.today()) }
    }

    fun saveBodyMeasure(measure: BodyMeasure) {
        viewModelScope.launch { repo.saveBodyMeasure(measure) }
    }

    fun addPhoto(photo: ProgressPhoto) {
        viewModelScope.launch { repo.addPhoto(photo) }
    }

    fun deletePhoto(id: Long) {
        viewModelScope.launch { repo.deletePhoto(id) }
    }

    fun addExercise(log: ExerciseLog) {
        viewModelScope.launch { repo.addExercise(log) }
    }

    fun deleteExercise(id: Long) {
        viewModelScope.launch { repo.deleteExercise(id) }
    }
}

/** 我的页。把 usage_stat 的流水按类型累加成八个数字。 */
class MineViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val totals: StateFlow<Map<String, Int>> = repo.observeUsageAll()
        .map { rows -> rows.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.count } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 本月 AI 调用次数（M6 的「调用统计卡」）。
     * 不用单独查库：usage_stat 的明细本来就在订阅里，按月过滤求和即可。
     */
    val monthAi: StateFlow<Int> = repo.observeUsageAll()
        .map { rows ->
            val today = com.qingji.app.domain.AppClock.today()
            val from = today.withDayOfMonth(1)
            rows.filter { it.type == "ai" && !it.date.isBefore(from) && !it.date.isAfter(today) }
                .sumOf { it.count }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 成就清单。usage_stat 一变就重算（记一笔饮食、写完日记都会触发）。 */
    val achievements: StateFlow<List<com.qingji.app.domain.Achievement>> =
        repo.observeUsageAll()
            .map { com.qingji.app.domain.Achievements.list(repo.achievementStats()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 「我的目标」改完重算热量预算。
     * 用的是最近一次体重（没有就用起始体重），并沿用个人资料里的活动量。
     */
    suspend fun saveGoal(targetWeightKg: Double, activityLevel: Int, mode: String) {
        val current = repo.observeProfile().first() ?: return
        val weight = repo.latestWeight()?.weightKg
            ?: current.startWeight.takeIf { it > 0 }
            ?: 70.0
        val age = current.birthday?.let {
            java.time.Period.between(it, com.qingji.app.domain.AppClock.today()).years
        } ?: 25
        val goal = NutritionCalculator.compute(
            gender = current.gender,
            age = age,
            heightCm = current.heightCm,
            weightKg = weight,
            activityLevel = activityLevel,
            mode = mode
        )
        repo.saveProfile(
            current.copy(
                targetWeight = targetWeightKg,
                activityLevel = activityLevel,
                dailyKcalGoal = goal.kcal,
                proteinGoalG = goal.proteinG,
                carbGoalG = goal.carbG,
                fatGoalG = goal.fatG
            )
        )

        // 和 ProfileViewModel.save 一样：目标模式要同步回 DataStore，
        // 否则首页顶部「减肥/增肌/保持」的标签还停在上一个模式。
        SettingsStore.set(getApplication(), SettingsKeys.GOAL_MODE, mode)
    }
}

// ============================================================
// 下面是 M3 / M5 新增页面的数据管家
// ============================================================

/** 图片识别（M3.2）。压缩后的 Base64 由界面传进来。 */
class ImageRecordViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun recognize(base64Jpeg: String): Result<AiRecognitionResult> =
        repo.recognizeImage(base64Jpeg, AppClock.today())

    /** v3.4.0：拍照识别同样记名字纠错。 */
    suspend fun save(
        logs: List<FoodLog>,
        nameCorrections: List<Pair<String, String>> = emptyList()
    ) {
        repo.rememberAiNameCorrections(nameCorrections)
        repo.addFoodLogs(logs)
    }
}

/** 条码（M3.4）。先本地后联网，查到就缓存进食物表，下次断网也能用。 */
class BarcodeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun resolve(rawCode: String): Result<Food> {
        val code = BarcodeRules.normalize(rawCode)
        if (code.isEmpty()) return Result.failure(IllegalArgumentException("请先输入或扫出一个条码"))

        repo.findFoodByBarcode(code)?.let { return Result.success(it) }

        return repo.lookupBarcodeRemote(code).fold(
            onSuccess = { product ->
                val food = Food(
                    name = product.name,
                    barcode = code,
                    kcalPer100g = product.kcalPer100g,
                    proteinPer100g = product.proteinPer100g,
                    carbPer100g = product.carbPer100g,
                    fatPer100g = product.fatPer100g,
                    defaultGrams = 100.0,
                    source = "openfoodfacts"
                )
                repo.saveFood(food)
                Result.success(food)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /** 手动录入一条自定义食物，同样缓存进食物表。 */
    suspend fun saveManual(name: String, kcal: Double, code: String): Food {
        val food = Food(
            name = name.trim(),
            barcode = BarcodeRules.normalize(code).ifBlank { null },
            kcalPer100g = kcal,
            defaultGrams = 100.0,
            source = "user"
        )
        repo.saveFood(food)
        return food
    }

    /** v3.5.0：日期可选（默认今天），其余行为不变。 */
    suspend fun add(food: Food, grams: Double, mealType: String, date: LocalDate = AppClock.today()) {
        val ratio = grams / 100.0
        repo.addFoodLog(
            FoodLog(
                date = date,
                mealType = mealType,
                foodName = food.name,
                grams = grams,
                kcal = food.kcalPer100g * ratio,
                proteinG = food.proteinPer100g * ratio,
                carbG = food.carbPer100g * ratio,
                fatG = food.fatPer100g * ratio,
                source = "barcode"
            )
        )
    }
}

/** AI 分析（M3.8）：今日复盘 / 周报。数字由仓库层聚合，模型只负责说人话。 */
class AiAnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun daily(): Result<String> {
        val today = AppClock.today()
        return repo.analyze(repo.buildDailyFacts(today), today)
    }

    suspend fun weekly(): Result<String> {
        val today = AppClock.today()
        return repo.analyze(repo.buildWeeklyFacts(), today)
    }
}

/** 日记列表（M5）：排序切换 + 删除，数据一变界面自动刷新。 */
class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private val _sortMode = MutableStateFlow(DiaryRules.SortMode.DATE)
    val sortMode: StateFlow<DiaryRules.SortMode> = _sortMode.asStateFlow()

    val diaries: StateFlow<List<Diary>> = combine(
        repo.observeDiaries(),
        _sortMode
    ) { list, mode ->
        DiaryRules.sort(list, mode, { it.date }, { it.updatedAt }, { it.rating })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 快捷按钮「水」：日记页也能直接记一杯。 */
    fun addWater(ml: Int) {
        viewModelScope.launch { repo.addWater(ml, AppClock.today()) }
    }

    fun setSort(mode: DiaryRules.SortMode) {
        _sortMode.value = mode
    }

    /** 清空全部日记 */
    fun clearAllDiaries() {
        viewModelScope.launch { repo.clearAllDiaries() }
    }

    suspend fun delete(id: Long) = repo.deleteDiary(id)
}

/** 日记编辑（M5）：保存时把当天体重顺手关联上。 */
class DiaryEditViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun latestWeight(): Double? = repo.latestWeight()?.weightKg

    suspend fun load(id: Long): Diary? = repo.findDiary(id)

    suspend fun save(diary: Diary) = repo.saveDiary(diary)
}

/**
 * 轻断食页的数据管家。
 *
 * 断食状态不需要后台服务：每次打开页面、或者每 30 秒 tick 一下，
 * 用「当前时间 + 设置里的进食窗口」重新算一遍就行，省电也不出错。
 */
class FastingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val settings: StateFlow<com.qingji.app.data.prefs.QingJiSettings> =
        com.qingji.app.data.prefs.SettingsStore.observe(app)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.qingji.app.data.prefs.QingJiSettings()
            )

    /** 近 7 天断食记录，用来画柱状图 */
    val weekLogs: StateFlow<List<com.qingji.app.data.local.entity.FastingLog>> = repo
        .observeFastingRange(AppClock.today().minusDays(6), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** v1.0.3：近 30 天记录倒序，历史记录卡用 */
    val historyLogs: StateFlow<List<com.qingji.app.data.local.entity.FastingLog>> = repo
        .observeFastingRange(AppClock.today().minusDays(29), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 近一年断食记录：算「连续断食天数」用。
     * 历史卡只看 30 天，但连续天数要一路往前数，30 天不够。
     */
    val yearLogs: StateFlow<List<com.qingji.app.data.local.entity.FastingLog>> = repo
        .observeFastingRange(AppClock.today().minusDays(365), AppClock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前时刻，每 30 秒刷一次，倒计时才会走 */
    var now by androidx.compose.runtime.mutableStateOf(java.time.LocalTime.now())
        private set

    fun tick() {
        now = java.time.LocalTime.now()
    }

    fun saveSettings(
        enabled: Boolean, planHours: Int, startMinute: Int, endMinute: Int,
        days: String, phaseAlert: Boolean
    ) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_ENABLED, enabled)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_PLAN_HOURS, planHours)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_EAT_START, startMinute)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_EAT_END, endMinute)
            // v1.0.3：生效周期 + 时段切换提醒；提醒排布走 Reminders.apply（settings 变化自动重算）
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_DAYS, days)
            com.qingji.app.data.prefs.SettingsStore.set(ctx, com.qingji.app.data.prefs.SettingsKeys.FASTING_PHASE_ALERT, phaseAlert)
            // 用刚保存的值重排提醒（不等 Flow 回流）
            com.qingji.app.notify.Reminders.apply(
                ctx,
                settings.value.copy(
                    fastingEnabled = enabled,
                    fastingPlanHours = planHours,
                    fastingEatStartMinute = startMinute,
                    fastingEatEndMinute = endMinute,
                    fastingDays = days,
                    fastingPhaseAlert = phaseAlert
                )
            )
            // 顺手把今天这条记录定下来，近 7 日柱状图才有数据
            repo.saveFasting(
                com.qingji.app.data.local.entity.FastingLog(
                    date = AppClock.today(),
                    planHours = planHours,
                    eatStartMinute = startMinute,
                    eatEndMinute = endMinute
                )
            )
        }
    }

    /** 断食常驻通知开关（前台服务只负责把现有断食状态挂到通知栏）。 */
    fun setOngoingNotification(enabled: Boolean) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_ONGOING_NOTIFICATION,
                enabled
            )
            com.qingji.app.notify.FastingService.sync(ctx)
        }
    }

    // ---------- 自由断食（2026-11-11 专项） ----------

    /** 给界面的一句话反馈：重复开始被拒、时长太短等。空串 = 没消息。 */
    var freeMessage by androidx.compose.runtime.mutableStateOf("")
        private set

    fun clearFreeMessage() {
        freeMessage = ""
    }

    /**
     * 开始一段自由断食。
     *
     * [startAtMillis] 允许是过去的时间（比如「昨晚 8 点开始的」），
     * 但不能是未来、也不能早于 72 小时前，否则拒绝并给一句提示。
     * 已经有一段在进行时直接忽略 —— 重复点「开始」不会把计时清零。
     */
    fun startFreeFasting(startAtMillis: Long = System.currentTimeMillis()) {
        if (settings.value.fastingFreeStart > 0L) {
            freeMessage = "已经有一段自由断食在进行中，先结束它再开始新的"
            return
        }
        val now = System.currentTimeMillis()
        val issue = FastingRules.freeStartIssue(startAtMillis, now)
        if (issue != null) {
            freeMessage = issue
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                startAtMillis
            )
            freeMessage = ""
            com.qingji.app.notify.FastingService.sync(ctx)
        }
    }

    /**
     * 结束自由断食：按真实起止算分钟数，写进当天记录。
     *
     * 注意 `fasting_log` 主键是日期、一天一条，所以当天已有方案记录时会被这条覆盖 ——
     * 这是既定表结构决定的，不额外改表；覆盖后历史卡按「自由断食」样式显示。
     */
    fun endFreeFasting() {
        val start = settings.value.fastingFreeStart
        if (start <= 0L) {
            freeMessage = "当前没有进行中的自由断食"
            return
        }
        val now = System.currentTimeMillis()
        val minutes = FastingRules.freeElapsedMinutes(start, now)
        val issue = FastingRules.freeEndIssue(minutes)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            if (issue != null) {
                // 太短：清掉状态，不写脏记录
                com.qingji.app.data.prefs.SettingsStore.set(
                    ctx,
                    com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                    0L
                )
                com.qingji.app.notify.FastingService.sync(ctx)
                freeMessage = issue
                return@launch
            }
            val zone = java.time.ZoneId.systemDefault()
            val startTime = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalTime()
            val endTime = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
            repo.saveFasting(
                com.qingji.app.data.local.entity.FastingLog(
                    date = AppClock.today(),
                    planHours = FastingRules.freePlanHours(minutes),
                    eatStartMinute = startTime.hour * 60 + startTime.minute,
                    eatEndMinute = endTime.hour * 60 + endTime.minute,
                    completed = true,
                    freeMode = true,
                    actualMinutes = minutes
                )
            )
            com.qingji.app.data.prefs.SettingsStore.set(
                ctx,
                com.qingji.app.data.prefs.SettingsKeys.FASTING_FREE_START,
                0L
            )
            com.qingji.app.notify.FastingService.sync(ctx)
            freeMessage = "已记录：${FastingRules.freeDurationLabel(minutes)}"
        }
    }
}

/** 「吃过的」列表里的一条：按食物名聚合出来的。 */
data class EatenFood(
    val name: String,
    val count: Int,
    val lastDate: java.time.LocalDate,
    val kcalPer100g: Double
)

/**
 * 食物库页的数据管家。
 * 三个页签各自一份数据：标准库（预置）、自定义（用户加的）、吃过的（历史聚合）。
 */
class FoodLibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val allFoods: StateFlow<List<com.qingji.app.data.local.entity.Food>> =
        repo.observeAllFoods()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userFoods: StateFlow<List<com.qingji.app.data.local.entity.Food>> =
        repo.observeUserFoods()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 90 天吃过的食物，按最近一次时间倒序 */
    val eatenFoods: StateFlow<List<EatenFood>> = repo
        .observeFoodRange(AppClock.today().minusDays(90), AppClock.today())
        .map { logs ->
            logs.groupBy { it.foodName }
                .map { (name, list) ->
                    val last = list.maxByOrNull { it.date }
                    val per100 = if (last != null && last.grams > 0) {
                        last.kcal / last.grams * 100.0
                    } else 0.0
                    EatenFood(
                        name = name,
                        count = list.size,
                        lastDate = last?.date ?: AppClock.today(),
                        kcalPer100g = per100
                    )
                }
                .sortedByDescending { it.lastDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(food: com.qingji.app.data.local.entity.Food) {
        viewModelScope.launch { repo.setFoodFavorite(food.id, !food.favorite) }
    }

    /** 删除自定义食物（预置库不允许删） */
    fun deleteCustomFood(food: com.qingji.app.data.local.entity.Food) {
        viewModelScope.launch { repo.deleteFood(food.id) }
    }

    /**
     * 把食物加到某一餐（dayOffset：0 今天 / 1 昨天 / 2 前天）。
     * AI 专项修真 Bug：以前只按每 100g 换算了热量，蛋白 / 碳水 / 脂肪
     * 全部走 FoodLog 默认值 0，导致从食物库页记的一笔在首页、进展页、
     * 小组件里三大营养素都少算。现在四个数值统一乘 grams/100。
     */
    fun addToMeal(
        foodName: String,
        per100gKcal: Double,
        grams: Double,
        mealType: String,
        dayOffset: Int = 0,
        per100gProtein: Double = 0.0,
        per100gCarb: Double = 0.0,
        per100gFat: Double = 0.0
    ) {
        viewModelScope.launch {
            val ratio = grams / 100.0
            repo.addFoodLog(
                com.qingji.app.data.local.entity.FoodLog(
                    date = AppClock.today().minusDays(dayOffset.toLong()),
                    mealType = mealType,
                    foodName = foodName,
                    grams = grams,
                    kcal = per100gKcal * ratio,
                    proteinG = per100gProtein * ratio,
                    carbG = per100gCarb * ratio,
                    fatG = per100gFat * ratio,
                    source = "manual"
                )
            )
        }
    }
}
