package com.qingji.app.data.repository

import com.qingji.app.domain.AppClock
import android.content.Context
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.FoodCatalogDatabase
import com.qingji.app.data.local.PhotoDirs
import com.qingji.app.data.local.SeedData
import com.qingji.app.data.local.entity.CatalogFood
import com.qingji.app.data.local.entity.FoodFavorite
import com.qingji.app.data.local.entity.UserFood
import com.qingji.app.data.local.entity.toFood
import com.qingji.app.data.local.entity.toUserFood
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
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.remote.AiClient
import com.qingji.app.data.remote.AiException
import com.qingji.app.data.remote.AiRecognitionResult
import com.qingji.app.data.remote.OpenFoodFactsClient
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.remote.ai.AiMessage
import com.qingji.app.data.remote.ai.AiPrompts
import com.qingji.app.data.remote.ai.AiProvider
import com.qingji.app.data.remote.ai.AiRecognitionParser
import com.qingji.app.data.remote.ai.AiRequest
import com.qingji.app.data.remote.ai.OpenAiCompatibleProvider
import com.qingji.app.domain.AiResultRules
import com.qingji.app.domain.NutritionMatch
import com.qingji.app.domain.OmniFoodDetail
import com.qingji.app.domain.Per100g
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * ============================================================
 * 数据仓库：界面只跟它打交道，不直接碰数据库和网络
 * ------------------------------------------------------------
 * 好处：以后想换数据库、换 AI 服务商，只改这一个文件。
 * 所有返回 Flow 的方法都是「数据一变，界面自动跟着变」。
 * ============================================================
 */
class QingJiRepository(
    private val context: Context,
    /** 阶段B：可注入刷新钩子（生产走 WidgetRefresher，测试记录 Target）。 */
    private val refreshHook: (Context, Set<com.qingji.app.widget.WidgetTarget>) -> Unit =
        { c, targets -> com.qingji.app.widget.WidgetRefresher.refresh(c, targets) },
    /** V2.1阶段A4：独立数据库注入（仅测试用；生产 null 走进程单例）。 */
    private val dbOverride: com.qingji.app.data.local.AppDatabase? = null,
    /**
     * QJ-AI-HISTORY-DATE-V1：仅测试注入的文本分析执行器（生产 null 走 AiProvider）。
     * 用于在单测里捕获真正发给模型的 facts，验证历史日期数据未串到今天。
     */
    private val analyzeExecutor: (suspend (AiConfig, String, String) -> Result<String>)? = null,
    /** B01：AI 服务提供者，生产走 OpenAI 兼容实现。 */
    private val aiProvider: AiProvider = OpenAiCompatibleProvider()
) {

    /** 用户自己的数据（可写） */
    private val db = dbOverride ?: AppDatabase.get(context)
    /** 标准食物库（只读，随 APK 预置） */
    private val catalog = FoodCatalogDatabase.get(context)
    /**
     * QJ-GOV-2026-04 / P2-03：网络客户端延迟初始化。
     * 本地只读场景（读档案/体重/历史）不再构造 OkHttp 客户端，
     * 首次真正调用 AI 或条码查询时才创建。数据库/标准库仍是进程单例，不重复创建。
     */
    private val foodApi: OpenFoodFactsClient by lazy { OpenFoodFactsClient() }

    private val catalogDao = catalog.catalogDao()
    private val omniDao = catalog.omniFoodDao()
    private val brandDao = catalog.brandCatalogDao()
    private val takeawayDao = catalog.takeawayDao()
    private val userFoodDao = db.userFoodDao()
    private val userFoodGroupDao = db.userFoodGroupDao()
    private val userBrandDao = db.userBrandDao()
    private val favoriteDao = db.foodFavoriteDao()

    /** 标准库 id 从 100001 起，用户库 id 从 1 起，用这个数字区分来源，永不撞号。 */
    companion object {
        const val CATALOG_ID_START = 100001L
        /** 搜索 / 默认列表一次最多返回多少条（界面一屏也就用得掉这些） */
        const val SEARCH_LIMIT = 50
        /** 默认列表里给「自己录的食物」预留的条数，剩下的位置交给标准库补 */
        const val DEFAULT_CUSTOM_LIMIT = 15
    }

    // ---------- 个人档案 ----------
    fun observeProfile(): Flow<UserProfile?> = db.userProfileDao().observe()
    /** 阶段B：档案字段差异 → 刷新目标（纯函数，可单测；不动 DB）。 */
    internal fun targetsForProfileChange(
        old: UserProfile?, new: UserProfile
    ): Set<com.qingji.app.widget.WidgetTarget> {
        val targets = mutableSetOf<com.qingji.app.widget.WidgetTarget>()
        if (old == null || old.dailyKcalGoal != new.dailyKcalGoal ||
            old.proteinGoalG != new.proteinGoalG ||
            old.carbGoalG != new.carbGoalG || old.fatGoalG != new.fatGoalG
        ) targets.add(com.qingji.app.widget.WidgetTarget.OVERVIEW)
        if (old == null || old.heightCm != new.heightCm || old.birthday != new.birthday ||
            old.gender != new.gender || old.targetWeight != new.targetWeight ||
            old.startWeight != new.startWeight || old.unit != new.unit
        ) targets.add(com.qingji.app.widget.WidgetTarget.BODY_DATA)
        // V2.1：单位变化同时影响体重闪记显示
        if (old != null && old.unit != new.unit) targets.add(com.qingji.app.widget.WidgetTarget.WEIGHT)
        return targets
    }

    suspend fun saveProfile(profile: UserProfile) {
        val old = db.userProfileDao().observe().firstOrNull()
        db.userProfileDao().save(profile)
        // 阶段B：按实际字段差异定向刷新，不一律 ALL。
        val targets = targetsForProfileChange(old, profile)
        if (targets.isNotEmpty()) refreshHook(context, targets)
    }

    // ---------- AI 配置（多服务统一入口） ----------
    /** 观察全部服务配置（配置页列表用）。 */
    fun observeAiConfigs(): Flow<List<AiConfig>> = db.aiConfigDao().observeAll()
    suspend fun getAiConfigs(): List<AiConfig> = db.aiConfigDao().getAll()

    /**
     * 当前激活配置。统一入口：所有 AI 业务经这里取配置，绝不各自读固定 id。
     * 读取流程：Settings 里的激活 id → 命中则用它；id=0（老用户首次升级/未配置）
     * 则取现有第一条配置并把它的 id 写回 Settings（迁移），以后就固定了。
     */
    suspend fun getActiveAiConfig(): AiConfig? {
        val dao = db.aiConfigDao()
        val savedId = SettingsStore.observe(context).first().aiActiveProviderId
        if (savedId > 0) {
            val hit = dao.getById(savedId)
            if (hit != null) return hit
        }
        // 未设置或指向已删配置：回退到第一条现存配置并写回（删当前服务后自动选其它）
        val fallback = dao.getAll().firstOrNull()
        if (fallback != null) setActiveAiConfig(fallback.id)
        return fallback
    }

    /** 当前激活配置的 API Key（按该配置 id 从加密存储读）。 */
    suspend fun getActiveApiKey(): String =
        getActiveAiConfig()?.let { ApiKeyStore.load(context, it.id).orEmpty() }.orEmpty()

    /** 切换当前服务：只写激活 id，不碰任何配置行、不清 Key。 */
    suspend fun setActiveAiConfig(id: Int) {
        SettingsStore.set(context, SettingsKeys.AI_ACTIVE_PROVIDER_ID, id)
    }

    /**
     * 新增或更新服务。id=0 = 新增，分配 max(id)+1 保证唯一且稳定；
     * 编辑传入原 id 保留。改名/改地址都不动 id。
     */
    suspend fun saveAiConfig(config: AiConfig, key: String? = null): AiConfig {
        val id = if (config.id > 0) config.id else (db.aiConfigDao().maxId() ?: 0) + 1
        val saved = config.copy(id = id, updatedAt = System.currentTimeMillis())
        db.aiConfigDao().save(saved)
        // 传了 key 才覆盖该配置的 Key；null = 保持原 Key（编辑留空）
        if (key != null) ApiKeyStore.save(context, id, key)
        // 首次保存（还没有激活服务）自动激活第一条，避免用户存完还要手动切
        if (getActiveAiConfig() == null) setActiveAiConfig(id)
        return saved
    }

    /** 删除服务：只删指定 id 的行与 Key；若删的是当前激活，自动切到其余第一条，没有则置 0。 */
    suspend fun deleteAiConfig(id: Int) {
        db.aiConfigDao().deleteById(id)
        ApiKeyStore.clear(context, id)
        val active = SettingsStore.observe(context).first().aiActiveProviderId
        if (active == id) {
            val next = db.aiConfigDao().getAll().firstOrNull()?.id ?: 0
            setActiveAiConfig(next)
        }
    }

    suspend fun hasApiKey(id: Int): Boolean = ApiKeyStore.isSet(context, id)
    /** 按指定 id 读 Key（编辑某配置时「留空保持原 Key」用）。 */
    fun getApiKeyFor(id: Int): String = ApiKeyStore.load(context, id).orEmpty()

    /**
     * 观察当前激活配置（随切换实时刷新）。
     * 与 getActiveAiConfig() 同样处理「active id 未设置（0）或指向已删配置」：
     * 回退到第一条现存配置，避免配置页显示「无当前服务」而实际 AI 调用在用第一条。
     * 注意 Flow 里不写 DataStore（getActiveAiConfig 会在首次调用时写回），这里只做只读回退。
     */
    fun observeAiConfig(): Flow<AiConfig?> =
        SettingsStore.observe(context).map { it.aiActiveProviderId }
            .flatMapLatest { id ->
                db.aiConfigDao().observeById(id).flatMapLatest { hit ->
                    if (hit != null) flowOf(hit)
                    else db.aiConfigDao().observeAll().map { it.firstOrNull() }
                }
            }
    suspend fun getAiConfig(): AiConfig? = getActiveAiConfig()

    /**
     * B01：所有 AI 识别统一走 AiProvider。
     * 保留旧的 Result<AiRecognitionResult> 契约，ViewModel 与屏幕不需要改调用方式。
     */
    private suspend fun recognizeWithProvider(
        config: AiConfig,
        key: String,
        systemPrompt: String,
        userMessage: AiMessage,
        temperature: Double,
        forceJsonObject: Boolean
    ): Result<AiRecognitionResult> = try {
        val response = aiProvider.generate(
            AiRequest(
                config = config,
                key = key,
                messages = listOf(AiMessage.system(systemPrompt), userMessage),
                temperature = temperature,
                forceJsonObject = forceJsonObject
            )
        )
        Result.success(AiRecognitionParser.parse(response.content))
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiError) {
        Result.failure(e.toLegacy())
    } catch (e: AiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}"))
    }

    private fun AiError.toLegacy(): AiException =
        AiException(message ?: code)

    // ---------- 首页当天数据 ----------
    fun observeDailySummary(date: LocalDate): Flow<DailySummary?> = db.foodLogDao().observeDailySummary(date)
    fun observeMealSummary(date: LocalDate): Flow<List<MealSummary>> = db.foodLogDao().observeMealSummary(date)
    fun observeFoodLogs(date: LocalDate): Flow<List<FoodLog>> = db.foodLogDao().observeByDate(date)
    fun observeWaterMl(date: LocalDate): Flow<Int?> = db.waterLogDao().observeTodayMl(date)

    /** QJ-HEALTH-DATE-WATER-004：指定日期的逐笔饮水明细，按 createdAt 升序。 */
    fun observeWaterLogs(date: LocalDate): Flow<List<WaterLog>> = db.waterLogDao().observeByDate(date)

    // ---------- 体重 ----------
    fun observeWeightLogs(): Flow<List<WeightLog>> = db.weightLogDao().observeAll()
    suspend fun latestWeight(): WeightLog? = db.weightLogDao().latest()

    /**
     * Widget 专用当前有效体重（工单 A3）：排除未来日期回填/误录，同日多条取最新一条。
     * 既有 latest() 保持不变，避免影响首页/曲线页的历史语义。
     */
    suspend fun latestEffectiveWeight(today: LocalDate): WeightLog? =
        db.weightLogDao().latestEffective(today)

    suspend fun saveWeight(log: WeightLog) {
        db.weightLogDao().save(log)
        db.usageStatDao().increment(log.date, "weight")
        // 阶段B：体重变化 → 体重闪记 + 身体数据
        refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.WEIGHT,
            com.qingji.app.widget.WidgetTarget.BODY_DATA))
    }

    /** 阶段B：单条体重删除（DAO 已有 deleteByDate）。删 0 行不刷新；成功刷 WEIGHT + BODY_DATA。 */
    suspend fun deleteWeight(date: java.time.LocalDate): Boolean {
        // DAO deleteByDate 返回 Unit，先查存在性才知道是否真的删到行（删除 0 行不刷新）。
        val exists = db.weightLogDao().findAll().any { it.date == date }
        if (!exists) return false
        db.weightLogDao().deleteByDate(date)
        refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.WEIGHT,
            com.qingji.app.widget.WidgetTarget.BODY_DATA))
        return true
    }

    // ---------- 进展页：身体维度 / 进度照片 / 运动 / 饮水区间 ----------

    /** 全部围度记录（按日期倒序，最新的在最前）。 */
    fun observeBodyMeasures(): Flow<List<BodyMeasure>> = db.bodyMeasureDao().observeAll()

    suspend fun saveBodyMeasure(item: BodyMeasure) = db.bodyMeasureDao().save(item)

    fun observePhotos(): Flow<List<ProgressPhoto>> = db.progressPhotoDao().observeAll()

    suspend fun addPhoto(photo: ProgressPhoto) {
        db.progressPhotoDao().insert(photo)
    }

    suspend fun deletePhoto(id: Long) = db.progressPhotoDao().deleteById(id)

    /** 当天的运动记录（首页热量缺口用） */
    fun observeExerciseLogs(d: LocalDate): Flow<List<ExerciseLog>> =
        observeExerciseRange(d, d)

    fun observeExerciseRange(from: LocalDate, to: LocalDate): Flow<List<ExerciseLog>> =
        db.exerciseLogDao().observeRange(from, to)

    suspend fun addExercise(log: ExerciseLog) {
        // 统一训练体系：日志 + 账本镜像放在同一事务里，失败一起回滚，
        // 不会出现「有运动记录没账本」的半成品（工单 §8 保存原子性）。
        db.withTransaction {
            val newId = db.exerciseLogDao().insert(log)
            // P1-04：账本镜像与源记录同事务、异常同回滚。移除原 runCatching 吞异常的写法，
            // 镜像失败不再伪装成「运动记录保存成功但账本没有」。
            healthRepository().mirrorExerciseLog(log.copy(id = newId))
            db.usageStatDao().increment(log.date, "body")
        }
        // 阶段B：新增运动 → 今日剩余（运动补偿）。仅当日影响 OVERVIEW。
        if (log.date == com.qingji.app.domain.AppClock.today()) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
    }

    /** v1.3.7：按 id 取一条有氧记录，给确认页回填「修改」。 */
    suspend fun findExercise(id: Long): ExerciseLog? = db.exerciseLogDao().findById(id)

    /**
     * v1.3.7：修改一条有氧记录。账本镜像先删后重插，避免改了日期/类型后
     * 旧镜像残留（sourceId 虽然一样，但 sourceType 会随 type 变）。
     */
    suspend fun updateExercise(log: ExerciseLog) {
        // V2.1阶段A4：原日期必须在 update 前查（update 后查到的是新日期，会漏判"今天改历史"）。
        val oldDate = db.exerciseLogDao().findById(log.id)?.date
        db.withTransaction {
            db.exerciseLogDao().update(log)
            // P1-04：先删旧镜像再重建，同事务、异常同回滚（不再 runCatching）
            healthRepository().deleteExerciseLogMirror(log.id)
            healthRepository().mirrorExerciseLog(log)
        }
        // 原日期或新日期任一是今天都刷。
        if (oldDate == com.qingji.app.domain.AppClock.today() ||
            log.date == com.qingji.app.domain.AppClock.today()
        ) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
    }

    suspend fun deleteExercise(id: Long) {
        // P1-04：删源记录与删账本镜像放在同一事务，同时成功或同时回滚，
        // 杜绝「源没了但账本残留孤儿行导致消耗多计」。
        // 工单 A2：只有删除真的发生（影响行数 > 0）才刷新 OVERVIEW——
        // Widget 的「今日剩余」含运动补偿，删运动后必须与首页同口径即时更新。
        val deleted = db.withTransaction {
            // 删除前先取日期：历史日期的删除不影响今日剩余，避免无差别刷新。
            val logDate = db.exerciseLogDao().findById(id)?.date
            val n = db.exerciseLogDao().deleteByIdCount(id)
            if (n > 0) {
                healthRepository().deleteExerciseLogMirror(id)
                logDate == AppClock.today() || logDate == null
            } else false
        }
        if (deleted) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
    }

    /** 统一运动消耗账本（健康体系 V1.0）。懒加载避免和 AppDatabase 初始化抢顺序。 */
    private fun healthRepository(): HealthRepository =
        _healthRepository ?: HealthRepository(context, dbOverride = dbOverride).also { _healthRepository = it }
    private var _healthRepository: HealthRepository? = null

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

    /** v3.3.0：右上角「新建」存一条自定义运动，用 category = 「自定义」做筛选标记。 */
    suspend fun addCustomExercise(name: String, met: Double): Long =
        db.exercisePresetDao().insertOne(
            com.qingji.app.data.local.entity.ExercisePreset(
                name = name,
                met = met,
                category = com.qingji.app.domain.ExerciseCalculator.CUSTOM_CATEGORY
            )
        )

    /** v3.3.0：「最近」页签的数据源，界面里再去重、按这个顺序排。 */
    fun observeRecentExercises(limit: Int = 60) =
        db.exerciseLogDao().observeRecent(limit)

    // ---------- 食物库（v1.0.4：只读标准库 + 用户库，合并后给界面） ----------

    /**
     * 把两路数据拼成界面熟悉的 Food。
     * 收藏存在用户库的 food_favorite 表里、按名字记——标准库只读，写不进去，
     * 而且以后扩库 id 会变，名字才稳定。
     */
    private fun mergeFoods(
        preset: List<CatalogFood>,
        custom: List<UserFood>,
        favoriteNames: Set<String>
    ): List<Food> =
        preset.map { it.toFood(favorite = it.name in favoriteNames) } +
            custom.map { it.toFood(favorite = it.name in favoriteNames || it.favorite) }

    /** 食物库全量：标准 654 条 + 用户自建，按名字排序交给界面分组。 */
    fun observeAllFoods(): Flow<List<Food>> =
        combine(
            catalogDao.observeAll(),
            userFoodDao.observeAll(),
            favoriteDao.observeAll()
        ) { preset, custom, favs ->
            mergeFoods(preset, custom, favs.map { it.name }.toSet())
        }

    /** 「自定义」页签：只显示用户自己录的。 */
    fun observeUserFoods(): Flow<List<Food>> =
        combine(userFoodDao.observeAll(), favoriteDao.observeAll()) { custom, favs ->
            val names = favs.map { it.name }.toSet()
            custom.map { it.toFood(favorite = it.name in names || it.favorite) }
        }

    /**
     * v1.6 TASK-05 数据隔离：「标准库」页签只显示 food_catalog.db 的标准食物，
     * 绝不掺 user_food。收藏仍复用 food_favorite（按名字），不新建第二套。
     */
    fun observeCatalogFoods(): Flow<List<Food>> =
        combine(catalogDao.observeAll(), favoriteDao.observeAll()) { preset, favs ->
            val names = favs.map { it.name }.toSet()
            preset.map { it.toFood(favorite = it.name in names) }
        }

    /** 一次性取全量，给 AI 识别结果校正营养值、查重用。 */
    private suspend fun allFoodsOnce(): List<Food> =
        mergeFoods(catalogDao.findAll(), userFoodDao.findAll(), favoriteDao.allNames().toSet())

    /** 删除自定义食物；标准库只读，id >= 100001 的一律不删。 */
    suspend fun deleteFood(id: Long) {
        if (id >= CATALOG_ID_START) return
        // v1.6：删自定义食物前先清掉它在所有食物组里的关联，避免留孤立关联行。
        // 只删关联、不碰组本身，更不碰 food_log 历史快照。
        userFoodGroupDao.clearItemsOfFood(id)
        userFoodDao.deleteById(id)
    }

    suspend fun setFoodFavorite(id: Long, favorite: Boolean) {
        val name = if (id >= CATALOG_ID_START) {
            catalogDao.findById(id)?.name
        } else {
            userFoodDao.findById(id)?.name
        } ?: return
        if (favorite) favoriteDao.add(FoodFavorite(name)) else favoriteDao.remove(name)
        // 用户自建食物同时留一份在 user_food 里，备份导出时能带出去
        if (id < CATALOG_ID_START) userFoodDao.setFavorite(id, favorite)
    }

    /**
     * 把 AI 识别出来的食物存进用户库（source = user，绝不写只读标准库）。
     * 2026-11-18 修复：原来和标准库同名就直接静默跳过，但 AI 识别的餐名
     * 基本都来自标准库，导致「存进我的食物库」永远存不进去；
     * 现在改成允许与标准库同名（这是主人这一餐自己的折算值，跟标准库是两回事），
     * 只和自定义库自己重名的才去重。
     * @return true = 真的写进去了；false = 自定义库已有同名，跳过
     */
    suspend fun addUserFood(
        name: String,
        kcalPer100g: Double,
        protein: Double,
        carb: Double,
        fat: Double
    ): Boolean {
        if (name.isBlank()) return false
        val trimmed = name.trim()
        if (userFoodDao.findByName(trimmed) != null) return false
        userFoodDao.insert(
            UserFood(
                name = trimmed,
                kcalPer100g = kcalPer100g,
                proteinPer100g = protein,
                carbPer100g = carb,
                fatPer100g = fat,
                source = "user",
                category = com.qingji.app.domain.FoodCategory.guess(trimmed)
            )
        )
        return true
    }

    /**
     * v1.5 阶段二：从「新增/编辑自定义食物」表单整条保存。
     * @param id 0 = 新增；>0 = 编辑已有自定义食物（标准库 id 一律拒绝）
     * @return 保存后的记录 id；名字为空返回 -1；编辑标准库返回 -2
     */
    suspend fun saveCustomFood(
        id: Long = 0,
        name: String,
        brand: String = "",
        kcalPerBasis: Double,
        protein: Double,
        carb: Double,
        fat: Double,
        fiber: Double? = null,
        sodiumMg: Double? = null,
        basis: String = com.qingji.app.domain.FoodNutritionCalculator.PER_100G,
        servingGrams: Double = 100.0,
        barcode: String? = null,
        category: String = "其他",
        note: String = ""
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        if (id >= CATALOG_ID_START) return -2L
        val safeBasis = com.qingji.app.domain.FoodNutritionCalculator.normalizeBasis(basis)
        return if (id > 0) {
            val old = userFoodDao.findById(id) ?: return -1L
            val updated = old.copy(
                name = trimmed,
                brand = brand.trim(),
                kcalPer100g = kcalPerBasis.coerceAtLeast(0.0),
                proteinPer100g = protein.coerceAtLeast(0.0),
                carbPer100g = carb.coerceAtLeast(0.0),
                fatPer100g = fat.coerceAtLeast(0.0),
                fiberPer100g = fiber?.takeIf { it >= 0.0 },
                sodiumMgPer100g = sodiumMg?.takeIf { it >= 0.0 },
                defaultGrams = if (servingGrams > 0) servingGrams else old.defaultGrams,
                nutritionBasis = safeBasis,
                barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
                category = category,
                note = note.trim(),
                source = "user"
            )
            userFoodDao.update(updated)
            id
        } else {
            userFoodDao.insert(
                UserFood(
                    name = trimmed,
                    brand = brand.trim(),
                    kcalPer100g = kcalPerBasis.coerceAtLeast(0.0),
                    proteinPer100g = protein.coerceAtLeast(0.0),
                    carbPer100g = carb.coerceAtLeast(0.0),
                    fatPer100g = fat.coerceAtLeast(0.0),
                    fiberPer100g = fiber?.takeIf { it >= 0.0 },
                    sodiumMgPer100g = sodiumMg?.takeIf { it >= 0.0 },
                    defaultGrams = if (servingGrams > 0) servingGrams else 100.0,
                    nutritionBasis = safeBasis,
                    barcode = barcode?.trim()?.takeIf { it.isNotEmpty() },
                    category = category,
                    note = note.trim(),
                    source = "user"
                )
            )
        }
    }

    /** v1.5：按 id 取一条自定义食物（编辑页回填用），标准库 id 返回 null。 */
    suspend fun findCustomFood(id: Long): com.qingji.app.data.local.entity.Food? {
        if (id <= 0 || id >= CATALOG_ID_START) return null
        return userFoodDao.findById(id)?.toFood()
    }

    // ---------- v1.6 食物库整体重构：自定义食物组（多对多） ----------

    fun observeFoodGroups(): Flow<List<com.qingji.app.data.local.entity.UserFoodGroup>> =
        userFoodGroupDao.observeGroups()

    suspend fun allFoodGroups(): List<com.qingji.app.data.local.entity.UserFoodGroup> =
        userFoodGroupDao.allGroups()

    /** 新建食物组。重名返回 -1，界面提示「已有同名组」。 */
    suspend fun createFoodGroup(name: String, coverUri: String = ""): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || userFoodGroupDao.allGroups().any { it.name == trimmed }) return -1L
        val order = userFoodGroupDao.allGroups().maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        return userFoodGroupDao.insertGroup(
            com.qingji.app.data.local.entity.UserFoodGroup(
                name = trimmed,
                coverUri = coverUri,
                sortOrder = order
            )
        )
    }

    suspend fun updateFoodGroup(group: com.qingji.app.data.local.entity.UserFoodGroup) =
        userFoodGroupDao.updateGroup(group.copy(updatedAt = System.currentTimeMillis()))

    /** 删组：DAO 事务里先清关联再删组，组内食物本身不动。 */
    suspend fun deleteFoodGroup(groupId: Long) = userFoodGroupDao.deleteGroupCascade(groupId)

    /** 组内食物 id 列表（界面据此从 observeUserFoods 里过滤出成员）。 */
    fun observeGroupFoodIds(groupId: Long): Flow<List<Long>> =
        userFoodGroupDao.observeFoodIdsInGroup(groupId)

    suspend fun groupFoodIds(groupId: Long): List<Long> =
        userFoodGroupDao.foodIdsInGroup(groupId)

    /** 每个组的成员数，界面显示「N 个食物」。 */
    fun observeGroupFoodCount(groupId: Long): Flow<Int> =
        userFoodGroupDao.observeGroupCount(groupId)

    /** 某个食物被加进了哪些组（食物卡片「已归入 N 组」）。 */
    fun observeGroupsOfFood(foodId: Long): Flow<List<Long>> =
        userFoodGroupDao.observeGroupsOfFood(foodId)

    /** 一次性查某食物所在的组（打开「加入组」弹窗时用）。 */
    suspend fun groupsOfFoodOnce(foodId: Long): List<Long> =
        userFoodGroupDao.observeGroupsOfFood(foodId).first()

    /** 把一个食物加入组；重复加入被 DAO 的 IGNORE 吞掉，不会报错。 */
    suspend fun addFoodToGroup(groupId: Long, foodId: Long) =
        userFoodGroupDao.insertItem(
            com.qingji.app.data.local.entity.UserFoodGroupItem(
                groupId = groupId,
                foodId = foodId
            )
        )

    suspend fun removeFoodFromGroup(groupId: Long, foodId: Long) =
        userFoodGroupDao.removeItem(groupId, foodId)

    // ---------- v1.5 阶段四：用户自建外卖品牌 + 品牌菜品 ----------

    fun observeUserBrands(): Flow<List<com.qingji.app.data.local.entity.UserBrand>> =
        userBrandDao.observeBrands()

    suspend fun allUserBrands(): List<com.qingji.app.data.local.entity.UserBrand> =
        userBrandDao.allBrands()

    suspend fun findUserBrand(id: Long): com.qingji.app.data.local.entity.UserBrand? =
        userBrandDao.brandById(id)

    /** 新建品牌。重名返回 -1，界面提示「已有同名品牌」。 */
    suspend fun createUserBrand(
        name: String,
        icon: String = "🏪",
        note: String = "",
        sourceLabel: String = ""
    ): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || userBrandDao.brandByName(trimmed) != null) return -1L
        val order = userBrandDao.brandCount()
        return userBrandDao.insertBrand(
            com.qingji.app.data.local.entity.UserBrand(
                name = trimmed,
                icon = icon.ifBlank { "🏪" },
                note = note.trim(),
                sourceLabel = sourceLabel.trim(),
                sortOrder = order
            )
        )
    }

    suspend fun updateUserBrand(brand: com.qingji.app.data.local.entity.UserBrand) =
        userBrandDao.updateBrand(brand)

    /** 删品牌：DAO 事务里先清菜品再删品牌，杜绝孤立菜品；food_log 是快照不受影响。 */
    suspend fun deleteUserBrand(brandId: Long) = userBrandDao.deleteBrandCascading(brandId)

    fun observeBrandDishes(brandId: Long): Flow<List<com.qingji.app.data.local.entity.UserBrandDish>> =
        userBrandDao.observeDishes(brandId)

    suspend fun findBrandDish(id: Long): com.qingji.app.data.local.entity.UserBrandDish? =
        userBrandDao.dishById(id)

    /** 保存菜品：id<=0 新建，否则整行更新。营养基准非法时回落 PER_100G。 */
    suspend fun saveBrandDish(dish: com.qingji.app.data.local.entity.UserBrandDish): Long {
        val safeBasis = com.qingji.app.domain.FoodNutritionCalculator.normalizeBasis(dish.nutritionBasis)
        val safeSource = com.qingji.app.domain.FoodDataSource.normalize(dish.source)
        val fixed = dish.copy(
            name = dish.name.trim(),
            nutritionBasis = safeBasis,
            source = safeSource,
            servingGrams = if (fixedServingValid(dish.servingGrams)) dish.servingGrams else 100.0,
            kcal = dish.kcal.coerceAtLeast(0.0),
            protein = dish.protein.coerceAtLeast(0.0),
            fat = dish.fat.coerceAtLeast(0.0),
            carb = dish.carb.coerceAtLeast(0.0)
        )
        return if (dish.id <= 0) userBrandDao.insertDish(fixed.copy(id = 0))
        else { userBrandDao.updateDish(fixed); dish.id }
    }

    private fun fixedServingValid(v: Double) = v.isFinite() && v > 0.0

    suspend fun deleteBrandDish(id: Long) = userBrandDao.deleteDishById(id)

    suspend fun setBrandDishFavorite(id: Long, favorite: Boolean) =
        userBrandDao.setDishFavorite(id, favorite)

    /** 搜索页用：全局搜自建菜品。 */
    suspend fun searchUserBrandDishes(keyword: String): List<com.qingji.app.data.local.entity.UserBrandDish> =
        userBrandDao.searchDishes(keyword.trim())

    /**
     * v1.5 阶段四：全局搜索，一次覆盖标准食物/自定义/自建品牌/品牌菜品。
     * @param scope 搜索范围（全部/家庭厨房/外卖快餐/自定义）
     */
    suspend fun searchAllFoods(
        keyword: String,
        scope: com.qingji.app.domain.FoodSearchResult.Scope
    ): List<com.qingji.app.domain.FoodSearchResult> {
        val key = keyword.trim()
        if (key.isEmpty()) return emptyList()
        val out = mutableListOf<com.qingji.app.domain.FoodSearchResult>()

        // 标准 + 自定义食物（现有 searchFood 已支持中文/拼音/别名）
        if (scope == com.qingji.app.domain.FoodSearchResult.Scope.ALL ||
            scope == com.qingji.app.domain.FoodSearchResult.Scope.KITCHEN ||
            scope == com.qingji.app.domain.FoodSearchResult.Scope.CUSTOM
        ) {
            val foods = searchFood(key)
            foods.forEach { f ->
                val isCustom = f.id > 0 && f.id < CATALOG_ID_START
                val isTakeaway = f.brand.isNotBlank() || f.category == com.qingji.app.domain.FoodCategory.FAST_FOOD
                val include = when (scope) {
                    com.qingji.app.domain.FoodSearchResult.Scope.KITCHEN -> !isTakeaway
                    com.qingji.app.domain.FoodSearchResult.Scope.CUSTOM -> isCustom
                    else -> true
                }
                if (include) out.add(com.qingji.app.domain.FoodSearchResult.FoodItem(f))
            }
        }

        // 自建品牌 + 品牌菜品
        if (scope == com.qingji.app.domain.FoodSearchResult.Scope.ALL ||
            scope == com.qingji.app.domain.FoodSearchResult.Scope.TAKEAWAY
        ) {
            val lower = key.lowercase()
            val brands = userBrandDao.allBrands().filter { it.name.contains(key, true) || it.name.lowercase().contains(lower) }
            brands.forEach { b -> out.add(com.qingji.app.domain.FoodSearchResult.BrandItem(b)) }
            val brandNames = userBrandDao.allBrands().associateBy({ it.id }, { it.name })
            userBrandDao.searchDishes(key).forEach { d ->
                out.add(com.qingji.app.domain.FoodSearchResult.DishItem(d, brandNames[d.brandId] ?: ""))
            }
        }
        return out
    }

    /**
     * 自建菜品按实际食用量折算后写入饮食记录（快照式 food_log）。
     * 热量/三宏量走统一折算工具，同一基准，历史记录不受菜品后续编辑影响。
     * @return 写入的克数对应折算是否成功（份量非法时 ratio=0，仍写入 0 值记录）
     */
    suspend fun addBrandDishToMeal(
        dish: com.qingji.app.data.local.entity.UserBrandDish,
        grams: Double,
        mealType: String,
        dayOffset: Int = 0
    ) {
        val consumed = com.qingji.app.domain.FoodNutritionCalculator.compute(
            kcalPerBasis = dish.kcal,
            proteinPerBasis = dish.protein,
            carbPerBasis = dish.carb,
            fatPerBasis = dish.fat,
            basis = dish.nutritionBasis,
            servingGrams = dish.servingGrams,
            grams = grams
        )
        addFoodLog(
            com.qingji.app.data.local.entity.FoodLog(
                date = AppClock.today().minusDays(dayOffset.toLong()),
                mealType = mealType,
                foodName = dish.name,
                grams = grams,
                kcal = consumed.kcal,
                proteinG = consumed.proteinG,
                carbG = consumed.carbG,
                fatG = consumed.fatG,
                source = "manual"
            )
        )
    }

    // ---------- 轻断食 ----------

    fun observeFastingRange(from: LocalDate, to: LocalDate): Flow<List<com.qingji.app.data.local.entity.FastingLog>> =
        db.fastingLogDao().observeRange(from, to)

    suspend fun saveFasting(log: com.qingji.app.data.local.entity.FastingLog) {
        db.fastingLogDao().save(log)
    }

    // ---------- 使用统计 ----------
    /** 「我的」页八格 + 调用统计卡共用：数据一变自动刷新。 */
    fun observeUsageAll(): Flow<List<UsageStat>> = db.usageStatDao().observeAll()

    // ---------- 搜索 / 条数 ----------

    /**
     * 中文、别名、拼音首字母、拼音全拼一次搜完（标准库 + 用户库一起）。
     * 常吃置顶仍然交给 ViewModel 的 FoodRanking，这里只把精确命中排前面。
     */
    suspend fun searchFood(keyword: String): List<Food> {
        val key = keyword.trim()
        val favs = favoriteDao.allNames().toSet()

        // 空关键词 = 默认列表（还没输入时给用户先看点东西）。
        //
        // 以前是「标准库前 50 条 + 自定义全部」再 take(50)：标准库 654 条一上来
        // 就把 50 个名额占满，用户自己录的食物永远进不了第一屏，等于白录；
        // 而且 userFoodDao.findAll() 没有 LIMIT，自定义食物攒到上千条时会整表读进内存。
        //
        // 现在两路都带上限，顺序就是优先级：
        //   ① 自己录的（最近录的在前，最多 15 条）—— 用户自己建的，最该先看到；
        //   ② 标准库按名字补满剩下的名额（最多 35 条）。
        // 常吃置顶仍然交给 ViewModel 的 FoodRanking 在这 50 条里重排。
        if (key.isEmpty()) {
            val custom = userFoodDao.findTop(DEFAULT_CUSTOM_LIMIT)
            val preset = catalogDao.findTop(SEARCH_LIMIT - custom.size)
            val customFoods = custom.map { it.toFood(favorite = it.name in favs || it.favorite) }
            val presetFoods = preset.map { it.toFood(favorite = it.name in favs) }
            return (customFoods + presetFoods).take(SEARCH_LIMIT)
        }

        // 拼音字段全是小写，纯英文输入统一转小写，避免「MF」搜不到「mf」；
        // 中文字符不受 lowercase() 影响。
        val normalized = key.lowercase()
        return mergeFoods(catalogDao.search(normalized), userFoodDao.search(normalized), favs)
            .sortedWith(
                compareBy<Food>(
                    { if (it.name == normalized) 0 else if (it.name.startsWith(normalized)) 1 else 2 },
                    { it.name }
                )
            )
            .take(SEARCH_LIMIT)
    }

    suspend fun foodCount(): Int = catalogDao.count() + userFoodDao.count()

    /**
     * OmniFood8K 一条食物的「细账」：组成明细 + 样本来源 + 异常提示。
     * v1.3.2 界面接入——主表早就搜得到这 166 条，这里把只读新表里的细账
     * 按中文名接出来，给搜索页的份量弹窗展示。
     *
     * 只有 OmniFood8K 来源的食物才有细账：别的来源直接返回 null，
     * 界面不显示这一块，行为和旧版完全一样。
     */
    suspend fun omniFoodDetail(food: Food): OmniFoodDetail? {
        if (food.sourceId != "OmniFood8K") return null
        val entity = omniDao.findByNameCn(food.name) ?: return null
        val nutrition = omniDao.nutritionFor(entity.foodId)
        val sampleId = nutrition?.sourceSampleId?.takeIf { it.isNotBlank() }
        val sample = sampleId?.let { omniDao.sampleById(it) }
        val composition = sample?.let { omniDao.compositionForSample(it.sampleId) } ?: emptyList()
        val issues = sample?.let { omniDao.issuesForSample(it.sampleId) } ?: emptyList()
        // 营养和明细都查不到时，弹一块空卡片没意义，直接当没有
        if (nutrition == null && composition.isEmpty()) return null
        return OmniFoodDetail(
            entity = entity,
            nutrition = nutrition,
            sample = sample,
            composition = composition,
            issueFlags = issues.map { it.issueFlags }
        )
    }

    /** 可订阅的总条数（标准库 + 用户自建），搜索页靠它区分「载入中 / 空 / 没搜到」。 */
    fun observeFoodCount(): Flow<Int> =
        combine(catalogDao.observeCount(), userFoodDao.observeCount()) { a, b -> a + b }

    // ==================== 品牌食物库（第六阶段 UI）====================
    // 用户拍板：外卖快餐 → 选品牌 → 用堂食菜当菜单（301 条真实老乡鸡菜，
    // 标注「堂食·溯源报告2.0」，可搜可收藏可看溯源）。外卖商品 8 张表全空，
    // 空态如实提示，绝不把堂食菜复制成外卖商品（红线 3/15）。

    /** 已启用品牌列表（数据库动态驱动，UI 不写死；换品牌只换菜单）。 */
    fun observeBrands(): Flow<List<com.qingji.app.data.local.entity.BrandEntity>> =
        brandDao.observeBrands()

    /** 某品牌堂食菜菜单（source_id = brandId）。收藏状态按名字绑定现有机制，不动用户数据。 */
    fun observeBrandMenu(brandId: String): Flow<List<com.qingji.app.data.local.entity.Food>> =
        combine(
            catalogDao.observeByBrand(brandId),
            favoriteDao.observeAll()
        ) { dishes, favs ->
            val names = favs.map { it.name }.toSet()
            dishes.map { it.toFood(favorite = it.name in names) }
        }

    /** 某品牌实际有菜的分类（无菜分类不展示）。 */
    suspend fun brandCategories(brandId: String): List<String> = catalogDao.categoriesForBrand(brandId)

    /** 品牌维度搜索：只搜当前品牌的堂食菜，中文/别名/拼音都能搜。 */
    suspend fun searchBrandMenu(brandId: String, keyword: String): List<com.qingji.app.data.local.entity.Food> {
        val key = keyword.trim()
        val favs = favoriteDao.allNames().toSet()
        val rows = if (key.isEmpty()) {
            catalogDao.byBrandAndCategory(brandId, "").take(100)
        } else {
            catalogDao.searchByBrand(brandId, key.lowercase())
        }
        return rows.map { it.toFood(favorite = it.name in favs) }
    }

    /** 一道堂食菜的溯源聚合；没查到 trace 返回 null，界面显示「暂无公开资料」。 */
    suspend fun brandDishDetail(food: com.qingji.app.data.local.entity.Food): com.qingji.app.domain.BrandDishDetail? {
        if (food.sourceId.isBlank()) return null
        val trace = brandDao.traceForDish(food.id.toString()) ?: return null
        return com.qingji.app.domain.BrandDishDetail(
            trace = trace,
            ingredients = brandDao.ingredientsForDish(food.id.toString()),
            processing = brandDao.processingForDish(food.id.toString())
        )
    }

    /** 外卖商品是否有真实数据（首期恒为 0 → UI 显示空态，不造假）。 */
    suspend fun takeawayProductCount(): Int = takeawayDao.productCount()

    /**
     * v1.0.4 起标准食物库随 APK 预置在 assets 里，Room 首次打开时自动拷出来，
     * 不用再逐条灌库，也不会碰到用户任何一条数据。
     *
     * 方法名保留是为了不改动 QingJiApp 和搜索页那个按钮；
     * 现在只做「确认标准库能打开、返回条数」，读失败返回 0，不让 App 崩。
     */
    suspend fun ensurePresetFoods(): Int = try {
        catalogDao.count()
    } catch (e: Exception) {
        android.util.Log.e("QingJiRepository", "标准食物库读取失败", e)
        0
    }

    /** 搜索历史：搜过一次记一笔，重复搜同一个词只刷新时间（keyword 上有唯一索引）。 */
    suspend fun rememberSearch(keyword: String) {
        val key = keyword.trim()
        if (key.isBlank()) return
        db.foodSearchHistoryDao().save(
            com.qingji.app.data.local.entity.FoodSearchHistory(keyword = key)
        )
    }

    /** 搜索页的「最近搜过」快捷词，直接返回词列表，界面不用认识数据库对象。 */
    fun observeSearchHistory(limit: Int = 8): Flow<List<String>> =
        db.foodSearchHistoryDao().observeRecent(limit).map { list -> list.map { it.keyword } }

    // ---------- 写入 ----------
    /** 记一条饮食。顺手把「今天用了几次某功能」+1，对应「我的」页八格统计。 */
    suspend fun addFoodLog(log: FoodLog) {
        db.foodLogDao().insert(log)
        db.usageStatDao().increment(log.date, log.source)
        // 首页数字变了，桌面小组件也要跟着变（不等系统 30 分钟周期刷新）
        com.qingji.app.widget.WidgetRefresher.refresh(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
    }

    suspend fun addFoodLogs(logs: List<FoodLog>) {
        logs.forEach { addFoodLog(it) }
    }

    /**
     * 工单 A1：删除饮食记录后必须让 Widget 的今日总览即时反映新热量/营养。
     * 只有实际删除了行（影响行数 > 0）才触发 OVERVIEW 定向刷新；
     * id 不存在 / 删除失败 / 事务回滚时不发「数据已更新」的副作用。
     */
    suspend fun deleteFoodLog(id: Long) {
        val deleted = db.foodLogDao().deleteByIdCount(id)
        if (deleted > 0) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
    }

    // ---------- 用餐详情 ----------

    /** 把这一餐的几条记录整体挪到别的日期 / 餐次（详情页菜单里的「改时间」「改餐次」）。 */
    suspend fun moveMeal(ids: List<Long>, newDate: LocalDate, newMealType: String) {
        if (ids.isEmpty()) return
        db.foodLogDao().moveLogs(ids, newDate, newMealType)
        com.qingji.app.widget.WidgetRefresher.refresh(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
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
        com.qingji.app.widget.WidgetRefresher.refresh(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
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

        val rule = AiPrompts.SYSTEM_PROMPT + "\n\n用户偏好（优先遵守）：\n" +
            buildRecognitionRules() + "\n\n" + extraRule
        val result = recognizeWithProvider(
            config = config,
            key = getApiKeyFor(config.id),
            systemPrompt = rule,
            userMessage = AiMessage.user(description),
            temperature = 0.1,
            forceJsonObject = true
        )
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
        com.qingji.app.widget.WidgetRefresher.refresh(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
    }

    /**
     * QJ-HEALTH-DATE-WATER-004：支持指定日期 + 时间的饮水记录。
     * 只复用 WaterLog.createdAt（不新增时间字段/表）：把 (date, time) 按
     * AppClock.zone 转成 epoch 毫秒写进 createdAt，时区口径与全 App 一致（不假设 UTC）。
     * 上面两参的 addWater(ml, date) 保持不变，所有旧调用点无需改动。
     */
    suspend fun addWater(ml: Int, date: LocalDate, time: java.time.LocalTime) {
        if (ml <= 0) return
        val createdAt = java.time.LocalDateTime.of(date, time)
            .atZone(com.qingji.app.domain.AppClock.zone)
            .toInstant().toEpochMilli()
        db.waterLogDao().insert(WaterLog(date = date, ml = ml, createdAt = createdAt))
        db.usageStatDao().increment(date, "water")
        com.qingji.app.widget.WidgetRefresher.refresh(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
    }

    // ---------- AI 调用（带单日熔断） ----------
    private suspend fun todayAiCalls(date: LocalDate): Int =
        db.usageStatDao().countByType(date, "ai") ?: 0

    /** AI 管家/聊天入口复用同一配置闸门：未配置、关闭、超日限都在这里挡住。 */
    suspend fun ensureAiAvailable(date: LocalDate): AiConfig {
        val config = getAiConfig()
            ?: throw AiException("还没配置 AI 服务，去 设置 → AI 服务配置 填一次")
        if (!config.enabled) throw AiException("AI 功能已关闭，去 设置 → AI 服务配置 打开开关")
        if (todayAiCalls(date) >= config.dailyLimit) {
            throw AiException("今天的 AI 调用已达上限 ${config.dailyLimit} 次，手动修改仍然可用")
        }
        return config
    }

    /** 一次助手任务记一次调用，不按内部 Agent 轮次重复扣。 */
    suspend fun recordAiCall(date: LocalDate) {
        db.usageStatDao().increment(date, "ai")
    }

    /**
     * v3.4.0：把用户在确认页改过的名字记下来，下次识别到同样的 AI 原名自动套用。
     * 存的是 DataStore 里一个字符串，不建表、不升 Room 版本。
     */
    suspend fun rememberAiNameCorrections(pairs: List<Pair<String, String>>) {
        if (pairs.isEmpty()) return
        try {
            var raw = SettingsStore.observe(context).first().aiNameCorrections
            pairs.forEach { (from, to) ->
                raw = com.qingji.app.domain.AiNameCorrections.put(raw, from, to)
            }
            SettingsStore.set(context, com.qingji.app.data.prefs.SettingsKeys.AI_NAME_CORRECTIONS, raw)
        } catch (e: Exception) {
            // 记不住纠错不影响这一餐正常保存，静默跳过
        }
    }

    /**
     * 用本地食物库校正 AI 的识别结果。
     *
     * 逻辑：AI 说了「白米饭 150g」，本地库里有「米饭」→ 用本地库的 116 kcal/100g
     * 算出 174 kcal，而不是信模型自己编的数字。匹配不上才保留 AI 的值。
     * 顺带把克数和热量都做一次合理性兜底（防止 0 克、9999 克、每百克 2000 千卡）。
     *
     * v3.4.0：先套用用户的名字纠错，再拿改后的名字去本地库匹配，
     * 否则「改了名却还是按 AI 原名算热量」会让人以为改了没用。
     */
    private suspend fun correctWithLocalFood(result: AiRecognitionResult): AiRecognitionResult {
        val corrections = try {
            SettingsStore.observe(context).first().aiNameCorrections
        } catch (e: Exception) {
            ""
        }
        val renamed = if (corrections.isBlank()) {
            result
        } else {
            result.copy(
                items = result.items.map {
                    it.copy(name = com.qingji.app.domain.AiNameCorrections.apply(corrections, it.name))
                }
            )
        }

        val candidates = try {
            // v1.3.10：候选表带上别名（split "|"），AI 叫别名时也能命中同名库条目。
            allFoodsOnce().flatMap {
                val per = Per100g(
                    kcal = it.kcalPer100g,
                    protein = it.proteinPer100g,
                    carb = it.carbPer100g,
                    fat = it.fatPer100g
                )
                val primary = it.name to per
                val aliasList = it.aliases.split("|")
                    .map { a -> a.trim() }
                    .filter { a -> a.isNotEmpty() && a != it.name }
                    .map { a -> a to per }
                listOf(primary) + aliasList
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (candidates.isEmpty()) return auditNutritionConsistency(renamed)

        val items = renamed.items.map { item ->
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
        // v1.3.10 兜底：命不中库（或命中的库条目本身宏量全 0）时，AI 偷懒只给热量，
        // 蛋白/碳水/脂肪就会落成 0（用户吃了个 250 千卡的姜母鸭三大营养素全是 0）。
        // 对这些条目按「精确名（含别名）」回查一次库，命中就用库宏量按克数折算；
        // 查不到 / 库里本身是真实 0（白酒、无糖茶）就保持原样，不编数。
        val backfilled = items.map { item ->
            if (!(item.kcal > 0 && item.proteinG <= 0 && item.carbG <= 0 && item.fatG <= 0)) {
                return@map item
            }
            val key = NutritionMatch.normalizeName(item.name)
            val hasMacro: (Per100g) -> Boolean =
                { per -> per.protein > 0 || per.carb > 0 || per.fat > 0 }
            val exact = candidates.firstOrNull { (name, per) ->
                NutritionMatch.normalizeName(name) == key && hasMacro(per)
            } ?: candidates.firstOrNull { (name, per) ->
                val n = NutritionMatch.normalizeName(name)
                (n.contains(key) || key.contains(n)) && hasMacro(per)
            }
            if (exact == null) {
                item
            } else {
                val per = NutritionMatch.compute(exact.second, item.grams)
                item.copy(proteinG = per.protein, carbG = per.carb, fatG = per.fat)
            }
        }
        return auditNutritionConsistency(
            renamed.copy(items = backfilled, totalKcal = backfilled.sumOf { it.kcal })
        )
    }

    /**
     * 写库前的最后一道一致性检查（Review 提的那条：AI 营养数据自相矛盾）。
     *
     * 放在这里而不是解析层，是因为**这一步拿到才是要真正存进数据库的数**：
     * 命中本地食物库的条目已经被本地营养值覆盖，这时候没问题的就不该再报警；
     * 没命中、还留着 AI 自己编的数字，才需要人来核对。
     *
     * 判定交给 [AiResultRules.auditNutrition]（纯函数，有单测）：
     * 三大营养素加起来比食物还重、热量和 4-9-4 反推值差太远，都算对不上。
     *
     * 处理方式刻意**不改数字**：改了用户就看不出来哪条有问题，反而更容易被存下去。
     * 这里只把该条置信度压到 [AiResultRules.SUSPECT_CONFIDENCE]。
     *
     * tabfix（AI 专项）：不再往 advice 里追加第二行「⚠ 请核对后再保存」——
     * 解析层 AiClient.withSuspectNotes 已经写过一条同款提示，两行叠在顶部
     * 就是需求里说的「异常提示重复堆叠」。具体哪儿对不上改由确认页的
     * AiSaveGate 按当前数值实时算、逐条显示在卡片里，用户改完立刻消失。
     */
    private fun auditNutritionConsistency(result: AiRecognitionResult): AiRecognitionResult {
        val items = result.items.map { item ->
            val audit = AiResultRules.auditNutrition(
                grams = item.grams,
                kcal = item.kcal,
                proteinG = item.proteinG,
                carbG = item.carbG,
                fatG = item.fatG
            )
            if (audit.ok) {
                item
            } else {
                item.copy(confidence = minOf(item.confidence, AiResultRules.SUSPECT_CONFIDENCE))
            }
        }
        return result.copy(items = items)
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

        val rule = AiPrompts.SYSTEM_PROMPT + "\n\n用户偏好（优先遵守）：\n" + buildRecognitionRules()
        val result = recognizeWithProvider(
            config = config,
            key = getApiKeyFor(config.id),
            systemPrompt = rule,
            userMessage = AiMessage.user(input),
            temperature = 0.1,
            forceJsonObject = true
        )
        if (result.isSuccess) {
            db.usageStatDao().increment(date, "ai")
            return Result.success(correctWithLocalFood(result.getOrThrow()))
        }
        return result
    }

    /** 测试连接不走熔断计数，也不要求「已启用」。 */
    suspend fun testConnection(config: AiConfig, key: String): Result<String> = try {
        val result = aiProvider.testConnection(config, key)
        if (result.success) Result.success(result.message)
        else Result.failure(AiException(result.message))
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiError) {
        Result.failure(e.toLegacy())
    } catch (e: Exception) {
        Result.failure(AiException("连不上：${e.message?.take(100) ?: "未知错误"}"))
    }

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
        val rule = AiPrompts.VISION_PROMPT + "\n\n用户偏好（优先遵守）：\n" + buildRecognitionRules()
        val result = recognizeWithProvider(
            config = config,
            key = getApiKeyFor(config.id),
            systemPrompt = rule,
            userMessage = AiMessage.userImage("识别这份食物并估算份量", base64Jpeg),
            temperature = 0.1,
            forceJsonObject = false
        )
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

    /**
     * AI 分析（M3.8）：facts 是仓库层聚合好的数字，prompt 里不许模型编数据。
     * QJ-AI-HISTORY-DATE-V1：第二参 usageDate = 实际调用当天，仅用于每日额度查询与调用统计写入；
     * 分析对象日期（dataDate）已经体现在 facts 里，不得用 usageDate 反推或替换。
     */
    suspend fun analyze(facts: String, usageDate: LocalDate): Result<String> = try {
        val config = requireAi(usageDate)
        val result = if (analyzeExecutor != null) {
            analyzeExecutor(config, getApiKeyFor(config.id), facts)
        } else {
            val response = aiProvider.generate(
                AiRequest(
                    config = config,
                    key = getApiKeyFor(config.id),
                    messages = listOf(
                        AiMessage.system(AiPrompts.ANALYSIS_RULE),
                        AiMessage.user(facts)
                    ),
                    temperature = config.temperature,
                    forceJsonObject = false
                )
            )
            val text = response.content.replace("```json", "").replace("```", "").trim()
            Result.success(text.ifBlank { "AI 没有给出内容，换个时间范围再试" })
        }
        if (result.isSuccess) db.usageStatDao().increment(usageDate, "ai")
        result
    } catch (e: AiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}"))
    }

    /**
     * 指定日期复盘的事实材料：当天饮食、餐次、营养素、饮水；目标值为用户当前配置。
     * QJ-AI-HISTORY-DATE-V1：date = dataDate（分析哪天），所有记录查询都锚定它；
     * 热量预算/三大营养素目标是当前档案配置，不是历史快照，必须如实告诉模型，
     * 不能说成「那天定的目标」，更不能拿今天的数据补空缺。
     */
    suspend fun buildDailyFacts(date: LocalDate): String {
        val profile = db.userProfileDao().get()
        val summary = db.foodLogDao().observeDailySummary(date).first()
        val meals = db.foodLogDao().observeMealSummary(date).first()
        val water = db.waterLogDao().observeTodayMl(date).first() ?: 0
        val kcal = summary?.totalKcal?.toInt() ?: 0
        val goal = profile?.dailyKcalGoal ?: 0
        val isToday = date == AppClock.today()
        val dayLabel = if (isToday) "今天" else "$date"
        val mealText = if (meals.isEmpty()) "${dayLabel}没有饮食记录" else meals.joinToString("、") {
            "${it.mealType}:${it.kcal?.toInt() ?: 0}千卡/${it.itemCount}项"
        }
        return buildString {
            append("【${if (isToday) "今日复盘" else "历史复盘"}】分析日期：$date（$dayLabel）。")
            append("热量预算 ${goal}千卡（用户当前配置的目标，非该日历史目标），${dayLabel}摄入 ${kcal}千卡，按当前预算差额 ${goal - kcal}千卡。")
            append("蛋白 ${summary?.totalProtein?.toInt() ?: 0}/${profile?.proteinGoalG ?: 0}g，")
            append("碳水 ${summary?.totalCarb?.toInt() ?: 0}/${profile?.carbGoalG ?: 0}g，")
            append("脂肪 ${summary?.totalFat?.toInt() ?: 0}/${profile?.fatGoalG ?: 0}g（目标均为当前配置）。")
            append("${dayLabel}饮水 ${water}ml。${dayLabel}各餐：$mealText。")
            append("只针对 $date 的上述记录给 1-2 条具体、可执行、不带医疗表述的建议；")
            append("没有记录的项明确说明该日无数据，不要编造，也不要混入其他日期的数据。")
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
    /** 先查用户库里缓存过的条码（断网也能用）。标准库没有条码列，不用查。 */
    suspend fun findFoodByBarcode(code: String): Food? =
        userFoodDao.findByBarcode(code)?.let { row ->
            row.toFood(favorite = row.name in favoriteDao.allNames())
        }

    /** 本机没有才联网查，查不到返回中文原因。 */
    suspend fun lookupBarcodeRemote(code: String) = foodApi.lookup(code)

    /** 条码查到或手动录入后，缓存进用户库，下次同一条码直接命中。 */
    suspend fun saveFood(food: Food): Long = userFoodDao.insert(food.toUserFood())

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

    // ============================================================
    // 清除数据（v1.0.6：原来只有一个「清空本地数据」，而且只清了 6 张表）
    // ------------------------------------------------------------
    // 以前这里只有一个 clearAllData()，清的是饮食/体重/饮水/日记/统计/档案 6 张表，
    // 而设置页那句文案写的是「会删掉饮食、体重、饮水、日记、围度、运动、照片记录和统计」
    // —— 围度、运动、照片这三样根本就没清。用户点了「确认清空」，以为清干净了，
    // 实际上隐私相关的数据还在库里。这是承诺和实现不一致，比缺功能严重。
    //
    // 现在拆成两档，语义各自说清楚：
    //   · clearRecords()  「清除记录」：只清记录，档案/目标/AI 配置/自建食物/设置全留着
    //   · factoryReset()  「恢复出厂」：28 张表 + 设置 + API Key + 本地图片全清，回到刚装好
    // ============================================================

    /**
     * 清除全部记录，保留「配置类」的东西。
     *
     * 清的（19 张）：饮食 / 饮水 / 体重 / 日记 / 围度 / 进度照片 / 运动 / 断食 / 使用统计 / 搜索历史
     *   + 训练会话、训练明细（训练中心是记录类，用户说「清记录」就得一起没）
     *   + 自律模块 7 张表（目标/周期/打卡/冲动/设置/里程碑/应对记录，整体清空）
     * 留的（9 张 + DataStore）：个人档案、AI 配置、自建食物、收藏、预置运动库、
     *   动作库、训练计划 3 张表（这些是配置/资产，不是记录）
     *
     * 为什么这么分：用户想「重新开始记」的时候，不该被迫重新填一遍身高体重、
     * 也不该丢掉自己录的几十种常吃食物 —— 那是他攒的资产，不是记录。
     */
    suspend fun clearRecords() {
        // 照片文件要先从库里取路径，清表之后就查不到了
        val photoPaths = runCatching { db.progressPhotoDao().findAll().map { it.path } }
            .getOrDefault(emptyList())
        // 头像不算记录，必须留着（它就在同一个目录里）
        val avatar = runCatching { SettingsStore.observe(context).first().avatarPath }
            .getOrDefault("")
        // 先删文件再清表：清表事务万一失败回滚，也不会留下指向空记录的孤儿图片；
        // 反过来先清表后删文件，中途抛异常就只剩没人引用的文件了。
        photoPaths.forEach { path -> PhotoDirs.deleteImageFile(path, avatar) }

        db.withTransaction {
            db.foodLogDao().clearAll()
            db.waterLogDao().clearAll()
            db.weightLogDao().clearAll()
            db.diaryDao().clearAll()
            db.bodyMeasureDao().clearAll()
            db.progressPhotoDao().clearAll()
            db.exerciseLogDao().clearAll()
            db.fastingLogDao().clearAll()
            db.usageStatDao().clearAll()
            db.foodSearchHistoryDao().clearAll()
            // 训练中心：记录清掉，动作库与计划保留
            db.workoutSessionDao().clearAll()
            db.workoutExerciseDao().clearAll()
            // 自律模块 7 张表全清（含设置与孤立行）
            db.selfDisciplineGoalDao().clearAll()
            db.selfDisciplinePeriodDao().clearAll()
            db.selfDisciplineCheckinDao().clearAll()
            db.selfDisciplineUrgeDao().clearAll()
            db.selfDisciplineSettingDao().clearAll()
            db.selfDisciplineMilestoneDao().clearAll()
            db.selfDisciplineInterventionDao().clearAll()
        }

        // 还没保存的「进行中训练」也算记录，一并作废
        com.qingji.app.data.prefs.WorkoutDraftStore(context).clear()

        // P1-01：清记录后失效每日健康汇总缓存，避免首页/进展页读到旧汇总
        com.qingji.app.data.repository.DailyHealthRepository(context).invalidateAll()

        // 断食/饮水的通知文案依赖记录，清完顺手刷一次桌面
        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }

    /**
     * 恢复出厂：回到「刚装完这个 App」的状态。
     *
     * 28 张表全清（含训练中心 6 张 + 自律模块 7 张）+ DataStore 设置全清 + 训练草稿/单位偏好全清
     * + API Key 清掉 + 本地图片全删 + 闹钟全取消。
     * 清完之后 `onboarding_done` 变成 false，QingJiAppRoot 订阅的 settings 流
     * 会自动把界面切回引导页 —— 不需要重启 App，也不用自己写导航逻辑。
     *
     * 唯一「不清」的是 `getExternalFilesDir/backup/` 里的备份文件：
     * 那是用户自己导出来准备带走的东西，删了等于把他最后的保险也烧了。
     */
    suspend fun factoryReset() {
        db.withTransaction {
            db.foodLogDao().clearAll()
            db.waterLogDao().clearAll()
            db.weightLogDao().clearAll()
            db.diaryDao().clearAll()
            db.bodyMeasureDao().clearAll()
            db.progressPhotoDao().clearAll()
            db.exerciseLogDao().clearAll()
            db.fastingLogDao().clearAll()
            db.usageStatDao().clearAll()
            db.foodSearchHistoryDao().clearAll()
            db.userFoodDao().clearAll()
            db.foodFavoriteDao().clearAll()
            db.aiConfigDao().clearAll()
            db.exercisePresetDao().clearAll()
            db.userProfileDao().clear()
            // 训练中心 6 张表全清（下次进训练页会重新灌内置动作库）
            db.exerciseDao().clearAll()
            db.trainingPlanDao().clearAll()
            db.trainingPlanDayDao().clearAll()
            db.planExerciseDao().clearAll()
            db.workoutSessionDao().clearAll()
            db.workoutExerciseDao().clearAll()
            // 自律模块 7 张表全清（含设置与孤立行）
            db.selfDisciplineGoalDao().clearAll()
            db.selfDisciplinePeriodDao().clearAll()
            db.selfDisciplineCheckinDao().clearAll()
            db.selfDisciplineUrgeDao().clearAll()
            db.selfDisciplineSettingDao().clearAll()
            db.selfDisciplineMilestoneDao().clearAll()
            db.selfDisciplineInterventionDao().clearAll()
        }

        // 设置里含主题、昵称、目标模式、AI 纠错表、引导完成标记，一起清
        SettingsStore.clear(context)
        // 训练草稿与 kg/lb 偏好（SharedPreferences，跟 DataStore 不是一个地方）
        com.qingji.app.data.prefs.WorkoutDraftStore(context).clearAll()
        // Key 在 AndroidKeyStore 加密存储里，跟 DataStore 是两个地方（清全部服务 + 旧字段）
        ApiKeyStore.clearAll(context)

        // 图片：头像、记录图、进度照片整目录重建（不删目录本身，省得后面要判空）。
        // 清单里补了 filesDir/progress（TASK-03）；备份目录 getExternalFilesDir/backup 不在清单里，保留。
        PhotoDirs.clearDirs(context)

        // 运动库清空了要立刻重灌预置的 30 条，
        // 否则用户走完引导点「记录运动」看到的是空列表（重启才会补）。
        ensurePresetExercises()

        // 设置全清之后闹钟还挂在系统里，会照样到点响。
        // apply() 传一份默认设置 = 所有开关都是关的 = 全部取消。
        runCatching {
            com.qingji.app.notify.Reminders.apply(context, com.qingji.app.data.prefs.QingJiSettings())
        }

        // P1-01：恢复出厂一并清掉每日健康汇总缓存（否则空数据下仍显示旧汇总）
        com.qingji.app.data.repository.DailyHealthRepository(context).invalidateAll()

        com.qingji.app.widget.WidgetRefresher.refresh(context)
    }
}
