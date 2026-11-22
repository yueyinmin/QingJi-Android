package com.qingji.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.TrainingSeed
import com.qingji.app.data.local.TrainingSeedImportPlanner
import com.qingji.app.data.local.TrainingTemplateData
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.LibraryMeta
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.local.entity.WorkoutCardioDetail
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.prefs.WorkoutDraftStore
import com.qingji.app.domain.PlanExerciseResolver
import com.qingji.app.domain.SessionCalorieCalculator
import com.qingji.app.domain.WorkoutCalorieCalculator
import com.qingji.app.domain.TrainingItemMapper
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * ============================================================
 * 训练中心的读写仓库（P0）
 * ------------------------------------------------------------
 * 三条铁律：
 * 1. 会话和它下面的动作/组必须一次事务写完，不许出现「有会话没明细」；
 * 2. 历史只读 workout_exercise.exerciseNameSnapshot，绝不 JOIN 动作库现名；
 * 3. v1.4 起铁律 3 改写：训练保存时在【同一事务】里结算整场消耗并写入
 *    统一运动消耗账本（HealthRepository），删除时联动清账本；
 *    独立运动模块（exercise_log）的补偿通道行为不变，只是多了账本镜像。
 * ============================================================
 */
class TrainingRepository(
    private val context: Context,
    /** V3 最终缺陷修复：可注入刷新钩子（生产默认走 WidgetRefresher，测试可替换验证行为）。 */
    private val refreshHook: (Context, Set<com.qingji.app.widget.WidgetTarget>) -> Unit =
        { c, targets -> com.qingji.app.widget.WidgetRefresher.refresh(c, targets) },
    /** V2.1阶段A4 同款测试注入：独立 Room 实例，生产不传。 */
    dbOverride: AppDatabase? = null
) {

    private val db = dbOverride ?: AppDatabase.get(context)
    private val exerciseDao = db.exerciseDao()
    private val libraryMetaDao = db.libraryMetaDao()
    private val planDao = db.trainingPlanDao()
    private val planDayDao = db.trainingPlanDayDao()
    private val planExerciseDao = db.planExerciseDao()
    private val sessionDao = db.workoutSessionDao()
    private val workoutExerciseDao = db.workoutExerciseDao()
    private val workoutCardioDao = db.workoutCardioDetailDao()
    private val exerciseLogDao = db.exerciseLogDao()

    /** 健康体系 V1.0：统一运动消耗账本（结算/删除联动都在这里做）。 */
    private val healthRepository = HealthRepository(context, dbOverride = dbOverride)

    /** 进行中草稿与 kg/lb 偏好（SharedPreferences，不进数据库）。 */
    val draftStore = WorkoutDraftStore(context)

    /** 结算用体重（沿用现有运动模块口径：档案起始体重，兜底 70kg）。 */
    fun observeSettleWeight() = db.userProfileDao().observe()
        .map { it?.startWeight?.takeIf { w -> w > 0 } ?: 70.0 }

    internal fun health(): HealthRepository = healthRepository

    fun observeExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()
    fun observeSessions(): Flow<List<WorkoutSession>> = sessionDao.observeAll()

    /** 训练日记算每次的总组数 / 训练量用，跟着库变化自动刷新。 */
    fun observeAllWorkoutExercises(): Flow<List<WorkoutExercise>> =
        workoutExerciseDao.observeAll()

    /** B1.1：历史/日记统一读取入口——力量 + 有氧 + HIIT/柔韧/其他全部映射成同一领域模型。 */
    fun observeAllSessionItems(): Flow<List<WorkoutItem>> = combine(
        workoutExerciseDao.observeAll(),
        workoutCardioDao.observeAll()
    ) { strengthRows, cardioRows ->
        strengthRows.map(TrainingItemMapper::workoutExerciseToItem) +
            cardioRows.map(TrainingItemMapper::cardioToItem)
    }

    /**
     * 按 UUID 增量补齐 Workout Guide 302 条内置动作。
     *
     * 保护规则见 TrainingSeedImportPlanner：不清表、不 REPLACE，用户收藏/自建/历史/计划不碰。
     * library_meta 记录已导入版本，版本一致且库完整时直接跳过，重复启动零写入。
     */
    suspend fun ensureSeed() {
        val seeds = TrainingSeed.builtIns()
        val existing = exerciseDao.findAll()
        val installed = libraryMetaDao.get(TrainingSeed.LIBRARY_VERSION_KEY)
        if (!TrainingSeedImportPlanner.importNeeded(installed, existing.size, seeds.size)) return

        val plan = TrainingSeedImportPlanner.plan(existing, seeds)
        db.withTransaction {
            if (plan.toInsert.isNotEmpty()) exerciseDao.insertMissing(plan.toInsert)
            plan.toUpdate.forEach { exerciseDao.update(it) }
            libraryMetaDao.put(
                LibraryMeta(TrainingSeed.LIBRARY_VERSION_KEY, TrainingSeed.LIBRARY_VERSION)
            )
        }
    }

    suspend fun addCustomExercise(name: String, category: String): ExerciseEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        // 名字唯一索引：同名就改成直接复用已有的那条，别让界面崩在 SQLiteConstraintException
        val existing = exerciseDao.findAll().firstOrNull { it.name == trimmed }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val item = ExerciseEntity(
            uuid = "user-${java.util.UUID.randomUUID()}",
            name = trimmed,
            category = category.ifBlank { TrainingSeed.CORE },
            isBuiltIn = false,
            createdAt = now,
            updatedAt = now
        )
        exerciseDao.insertAll(listOf(item))
        return item
    }

    suspend fun toggleFavorite(uuid: String, favorite: Boolean) {
        val item = exerciseDao.findByUuid(uuid) ?: return
        exerciseDao.update(item.copy(isFavorite = favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCustomExercise(uuid: String) = exerciseDao.deleteCustom(uuid)

    /** 「上次成绩自动带出」。动作被改名/删除时由 DAO 的名字快照回退匹配兜底。 */
    suspend fun lastSets(exerciseUuid: String, nameSnapshot: String): List<WorkoutSet> =
        TrainingMath.decodeSets(workoutExerciseDao.findLatestSets(exerciseUuid, nameSnapshot))

    /**
     * 保存一次训练（新建与「当天编辑」共用），整包写、整包替换明细。
     * v1.4 健康体系：结算整场消耗并在【同一个事务】里写入统一运动消耗账本——
     * 要么会话+明细+账本一起成功，要么都不写，不会出现「记了训练没记消耗」。
     * settleWeight 传 0.0 以下表示跳过结算（不该发生，防御用）。
     * @return 账本行的消耗值（估算，UI 用于「已保存，预计消耗 N 千卡」反馈）
     */
    suspend fun saveSession(
        session: WorkoutSession,
        items: List<WorkoutExercise>,
        settleWeightKg: Double
    ): Double {
        var settledKcal = 0.0
        db.withTransaction {
            sessionDao.insert(session)
            workoutExerciseDao.deleteBySession(session.uuid)
            // id 一律重新生成，避免编辑时把老明细的 id 带进来撞主键
            val inserted = items.map { it.copy(id = 0L) }
            workoutExerciseDao.insertAll(inserted)
            if (settleWeightKg > 0.0) {
                val row = healthRepository.settleStrengthSession(
                    session = session,
                    items = inserted,
                    weightKg = settleWeightKg,
                    method = SessionCalorieCalculator.METHOD_SEGMENTED
                )
                settledKcal = row.caloriesGross
            }
            // v1.4.2：同一事务里写一条 exercise_log，力量训练才能进「运动日记」。
            // 带 sourceId=场次uuid → 防重复（一次训练一条）、编辑复用同一条；
            // HealthRepository 对带 sourceId 的日志跳过账本镜像，消耗不会算两次。
            upsertTrainingDiaryLog(session, settledKcal, DIARY_TYPE_STRENGTH)
        }
        if (session.date == com.qingji.app.domain.AppClock.today()) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
        return settledKcal
    }

    /**
     * 统一训练体系：一场【力量 + 有氧混合】训练一次事务写完。
     *
     * 与原 saveSession 的区别：原方法纯力量、账本只结算力量；本方法额外写有氧明细，
     * 并用 WorkoutCalorieCalculator 把力量结算结果 + 有氧录入已确认热量汇总成
     * 【一条整场账本】（sourceType=STRENGTH_TRAINING、sourceId=session.uuid），
     * 力量和有氧项目都不各自写整场账本，杜绝双结算（工单 §5.1/§5.2）。
     *
     * 幂等：账本走 upsert（唯一索引 sourceType+sourceId），明细整包 delete+insert，
     * 重复保存同一 session.uuid 只会有一行账本、一组明细——保存重试/双击不产生重复。
     * 纯有氧场次传空 items 即可；纯力量场次仍走原 saveSession，行为零变化。
     *
     * @param settleWeightKg <=0 表示跳过全部热量结算（防御用），明细仍照常保存
     * @return 整场热量汇总结果（含被跳过的项目数，供 UI 如实提示）
     */
    suspend fun saveUnifiedSession(
        session: WorkoutSession,
        items: List<WorkoutItem>,
        cardio: List<WorkoutItem.Cardio>,
        settleWeightKg: Double
    ): WorkoutCalorieCalculator.Result {
        val allItems: List<WorkoutItem> = items + cardio
        var result = WorkoutCalorieCalculator.Result(
            totalKcal = 0.0, strengthKcal = 0.0, cardioKcal = 0.0,
            cardioItems = 0, notComputableItems = cardio.count { it.calculatedKcal <= 0.0 || !it.calculatedKcal.isFinite() },
            method = WorkoutCalorieCalculator.METHOD_CARDIO_ONLY
        )
        db.withTransaction {
            // 1+2：会话 + workout_exercise（力量与 HIIT/柔韧/其他，整包替换，id 重生成）
            sessionDao.insert(session)
            workoutExerciseDao.deleteBySession(session.uuid)
            val insertedRows = items.mapIndexed { i, item ->
                TrainingItemMapper.itemToWorkoutExercise(item, session.uuid, item.order.takeIf { it >= 0 } ?: i)
                    .copy(id = 0L)
            }
            workoutExerciseDao.insertAll(insertedRows)

            // 3：有氧明细（整包替换，只有 CARDIO 进这张表）
            workoutCardioDao.deleteBySession(session.uuid)
            val insertedCardio = cardio.mapIndexed { i, item ->
                TrainingItemMapper.itemToCardio(item, session.uuid, item.order.takeIf { it >= 0 } ?: (items.size + i))
                    .copy(id = 0L, sessionUuid = session.uuid)
            }
            if (insertedCardio.isNotEmpty()) workoutCardioDao.insertAll(insertedCardio)

            // 4+5：整场热量汇总 → 一条账本（不重算有氧、不双结算）
            if (settleWeightKg > 0.0) {
                val strengthRows = insertedRows.filter { row ->
                    val type = WorkoutItem.fromJson(row.itemMetaJson)?.recordType
                    type == null || type == com.qingji.app.domain.ExerciseRecordType.STRENGTH
                }
                val strengthKcal = if (strengthRows.isEmpty()) 0.0 else {
                    healthRepository.settleStrengthSession(
                        session = session,
                        items = strengthRows,
                        weightKg = settleWeightKg,
                        method = SessionCalorieCalculator.METHOD_SEGMENTED
                    ).caloriesGross
                }
                val combined = WorkoutCalorieCalculator.combine(strengthKcal, insertedCardio)
                result = combined
                // 若整场可计算热量为 0，不写“0 千卡成功结算”的账本行；历史行由 upsert 覆盖清除。
                if (combined.totalKcal > 0.0) {
                    healthRepository.ledgerDao().upsert(
                        com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity(
                            sourceType = HealthRepository.SOURCE_STRENGTH_TRAINING,
                            sourceId = session.uuid,
                            date = session.date,
                            durationSeconds = session.durationMinutes * 60,
                            caloriesGross = combined.totalKcal,
                            weightSnapshot = settleWeightKg,
                            calculationMethod = combined.method,
                            calculationVersion = combined.version,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    healthRepository.ledgerDao().deleteBySource(
                        HealthRepository.SOURCE_STRENGTH_TRAINING, session.uuid
                    )
                }
            }

            // 6：运动日记摘要投影（sourceId=uuid → 不再次触发账本镜像）
            upsertTrainingDiaryLog(session, result.totalKcal, TrainingItemMapper.diaryType(allItems))
        }
        if (session.date == com.qingji.app.domain.AppClock.today()) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
        return result
    }

    /** 读一场训练的完整明细：力量 + 有氧 + HIIT/柔韧/其他，统一返回领域模型。 */
    suspend fun sessionDetail(uuid: String): SessionDetail {
        val strengthRows = workoutExerciseDao.findBySession(uuid)
        val cardioRows = workoutCardioDao.findBySession(uuid)
        return SessionDetail(
            session = sessionDao.findByUuid(uuid),
            strength = strengthRows,
            cardio = cardioRows,
            items = strengthRows.map(TrainingItemMapper::workoutExerciseToItem) +
                cardioRows.map(TrainingItemMapper::cardioToItem)
        )
    }

    /** 兼容旧调用名；语义已与 sessionDetail 相同（完整会话）。 */
    suspend fun sessionDetailWithCardio(uuid: String): SessionDetail = sessionDetail(uuid)

    data class SessionDetail(
        val session: WorkoutSession?,
        val strength: List<WorkoutExercise>,
        val cardio: List<WorkoutCardioDetail>,
        val items: List<WorkoutItem> = emptyList()
    )

    /**
     * 把一场力量训练写进运动日记（exercise_log）。
     * 按 sourceId（场次 uuid）查：有就更新、没有就插入 → 新建/编辑共用一条，
     * 重复保存也不会多记。kcal 用本场结算值，日记显示与账本一致。
     * type 固定「力量」，与运动日记卡、手动记运动的分类口径一致。
     */
    private suspend fun upsertTrainingDiaryLog(session: WorkoutSession, kcal: Double, type: String) {
        val existing = exerciseLogDao.findBySourceId(session.uuid)
        val log = ExerciseLog(
            id = existing?.id ?: 0L,
            date = session.date,
            type = type,
            minutes = session.durationMinutes,
            kcal = kcal,
            sourceId = session.uuid,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        if (existing == null) exerciseLogDao.insert(log) else exerciseLogDao.update(log)
    }

    companion object {
        /** 力量训练写进运动日记的固定分类名（与手动「记一次运动」的力量分类同词）。 */
        const val DIARY_TYPE_STRENGTH = "力量"
        const val DIARY_TYPE_CARDIO = "有氧"
        const val DIARY_TYPE_MIXED = "混合"
        const val DIARY_TYPE_FLEXIBILITY = "柔韧"
        const val DIARY_TYPE_OTHER = "其他"
    }

    /** 删除会话要连明细、连消耗账本、连运动日记日志一起删，不留孤儿行。 */
    suspend fun deleteSession(uuid: String) {
        // V3 最终缺陷修复 P1：先在事务内取业务日期与实际删除行数，
        // 事务成功提交后仅当「目标存在且业务日期==AppClock.today()」才刷新 OVERVIEW。
        val shouldRefresh = db.withTransaction {
            val sessionDate = sessionDao.findByUuid(uuid)?.date
            workoutExerciseDao.deleteBySession(uuid)
            workoutCardioDao.deleteBySession(uuid)
            sessionDao.deleteByUuid(uuid)
            healthRepository.ledgerDao().deleteBySource(
                HealthRepository.SOURCE_STRENGTH_TRAINING, uuid
            )
            exerciseLogDao.deleteBySourceId(uuid)
            sessionDate != null && sessionDate == com.qingji.app.domain.AppClock.today()
        }
        if (shouldRefresh) {
            refreshHook(context, setOf(com.qingji.app.widget.WidgetTarget.OVERVIEW))
        }
    }

    /**
     * 只改一次训练的标题/速记（note 单列），不触碰动作、重量、次数、组数、时长、
     * 来源与消耗结算。训练日记的备注编辑走这里，绝不走 saveSession 整包保存。
     */
    suspend fun updateSessionNote(uuid: String, note: String) =
        sessionDao.updateSessionNote(uuid, note)


    // ============================================================
    // P1：训练计划 / 动作库 / 个人最佳
    // ============================================================

    fun observePlans(): Flow<List<TrainingPlan>> = planDao.observeAll()
    fun observeActivePlan(): Flow<TrainingPlan?> = planDao.observeActive()

    fun observePlanExercises(): Flow<List<PlanExercise>> = planExerciseDao.observeAll()
    fun observeAllPlanDays(): Flow<List<TrainingPlanDay>> = planDayDao.observeAll()
    fun observeFavorites(): Flow<List<ExerciseEntity>> = exerciseDao.observeFavorites()

    /**
     * QJ 训练联动：取「当前启用计划 · 今天（星期）」的动作安排，按 orderInDay 排序。
     * 复用现有的 activePlan / planDays / planExercises 三条查询，不复制日期逻辑：
     * 星期用 AppClock.today().dayOfWeek.value（周一=1 … 周日=7），与
     * TrainingPlanViewModel.todayArrangement() 完全同一口径。
     * 休息日 / 未启用计划 / 当天无安排 → 返回空列表，由调用方走空白训练。
     */
    suspend fun todayPlanExercises(): List<PlanExercise> {
        val plan = observeActivePlan().first() ?: return emptyList()
        val days = observeAllPlanDays().first().filter { it.planUuid == plan.uuid }
        val weekday = com.qingji.app.domain.AppClock.today().dayOfWeek.value
        val day = days.firstOrNull { it.weekday == weekday } ?: return emptyList()
        if (day.isRest) return emptyList()
        return observePlanExercises().first()
            .filter { it.dayId == day.id }
            .sortedBy { it.orderInDay }
    }

    /**
     * 启用一份计划（模板或自定义）：先把所有旧计划停用，再整包写新计划。
     * 旧计划只是 isActive=false，数据留着 → 「停用不清历史」。
     */
    suspend fun activatePlan(name: String, source: String, days: List<TrainingTemplateData.TemplateDay>): String {
        val uuid = "plan-${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val library = exerciseDao.findAll()
        db.withTransaction {
            planDao.deactivateAll()
            planDao.insertAll(listOf(TrainingPlan(uuid, name, source, true, now)))
            (1..7).forEach { weekday ->
                val tplDay = days.firstOrNull { it.weekday == weekday }
                val dayId = planDayDao.insertOne(
                    TrainingPlanDay(
                        planUuid = uuid,
                        weekday = weekday,
                        title = tplDay?.title ?: "休息",
                        isRest = tplDay == null
                    )
                )
                if (tplDay != null) {
                    val items = tplDay.exerciseNames.mapIndexed { i, exerciseName ->
                        // QJ 训练联动：优先用编辑器里填的目标参数（组/次/重量），
                        // 预置模板没填 targets → 沿用原来的 3组×10次×0kg，行为不变。
                        val t = tplDay.targets?.get(exerciseName)
                        val cardioCfg = tplDay.cardioTargets?.get(exerciseName)
                        val libExercise = library.firstOrNull { it.name == exerciseName }
                        val resolvedType = com.qingji.app.domain.TrainingTypeResolver.resolve(
                            persistedRecordType = libExercise?.recordType, name = exerciseName,
                            englishName = libExercise?.englishName, exerciseType = libExercise?.exerciseType,
                            category = libExercise?.category, isStretch = libExercise?.isStretch ?: false,
                            exerciseUuid = libExercise?.uuid
                        ).type
                        PlanExercise(
                            dayId = dayId,
                            exerciseUuid = libExercise?.uuid.orEmpty(),
                            exerciseNameSnapshot = exerciseName,
                            targetSets = t?.first ?: 3,
                            targetReps = t?.second ?: 10,
                            targetWeightKg = t?.third ?: 0.0,
                            // 统一体系 V2.1：非力量动作写 recordType + 版本化目标 JSON
                            recordType = if (resolvedType == com.qingji.app.domain.ExerciseRecordType.STRENGTH)
                                "" else resolvedType.key,
                            targetConfigJson = if (cardioCfg != null)
                                com.qingji.app.domain.TargetConfigJson.encode(
                                    cardioCfg.copy(type = resolvedType)
                                ) else "",
                            orderInDay = i
                        )
                    }
                    if (items.isNotEmpty()) planExerciseDao.insertAll(items)
                }
            }
        }
        return uuid
    }

    suspend fun useTemplate(template: TrainingTemplateData.Template): String =
        activatePlan(template.name, "template:${template.id}", template.days)

    /**
     * v1.4 健康体系（工单 4.5）：把一场历史训练复制为「我的计划」（默认不激活）。
     * 动作按名字快照解析成稳定动作 ID，动作库找不到的就保留名字、ID 留空，
     * 返回缺失名单给 UI 做兼容提示（工单要求：动作缺失时给出提示）。
     * 计划是独立副本，之后改计划 / 停用 / 删除都不会反向动到这次历史训练。
     *
     * @return Pair(planUuid, 动作库里找不到的动作名列表)
     */
    suspend fun saveSessionAsTemplate(sessionUuid: String, name: String): Pair<String, List<String>> {
        val detail = sessionDetail(sessionUuid)
        val session = detail.session ?: throw IllegalArgumentException("训练记录不存在或已删除")
        val items = detail.items.filter { it.name.isNotBlank() }.sortedBy { it.order }
        require(items.isNotEmpty()) { "这场训练没有动作明细" }
        val library = exerciseDao.findAll()
        // UUID 优先、名字兜底；历史复制不修改动作库，也不改原会话。
        val uuids = library.map { it.uuid }.toSet()
        val names = library.associate { it.name to it.uuid }
        val resolved = items.map { item ->
            item to PlanExerciseResolver.resolve(
                PlanExerciseResolver.Ref(item.exerciseUuid, item.name), uuids, names
            )
        }
        val missing = resolved.mapNotNull { it.second.missingName }
        val weekday = session.date.dayOfWeek.value
        val title = "从历史复制 · ${session.date}"
        val uuid = "plan-${java.util.UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        db.withTransaction {
            planDao.insertAll(listOf(TrainingPlan(uuid, name, "history:${sessionUuid}", false, now)))
            val dayId = planDayDao.insertOne(
                TrainingPlanDay(planUuid = uuid, weekday = weekday, title = title, isRest = false)
            )
            planExerciseDao.insertAll(resolved.mapIndexed { i, (item, hit) ->
                val isStrength = item is WorkoutItem.Strength
                val sets = (item as? WorkoutItem.Strength)?.sets.orEmpty()
                // 计划目标优先取历史里保存的 targetReps/targetWeight；没有才用当次实际值作为可复用建议。
                val targetReps = sets.firstOrNull { it.targetReps != null && it.targetReps!! > 0 }?.targetReps
                    ?: sets.firstOrNull { it.reps > 0 }?.reps ?: 0
                val targetWeight = sets.firstOrNull { it.targetWeightKg != null && it.targetWeightKg!! > 0.0 }?.targetWeightKg
                    ?: sets.firstOrNull { it.weightKg > 0.0 }?.weightKg ?: 0.0
                val targetJson = if (item.targetConfigJson.isNotBlank()) item.targetConfigJson else {
                    com.qingji.app.domain.TargetConfigJson.encode(
                        com.qingji.app.domain.TargetConfig(
                            type = item.recordType,
                            sets = sets.size.takeIf { it > 0 },
                            reps = targetReps.takeIf { it > 0 },
                            weightKg = targetWeight.takeIf { it > 0.0 }
                        )
                    )
                }
                PlanExercise(
                    dayId = dayId,
                    exerciseUuid = hit.exerciseUuid,
                    exerciseNameSnapshot = item.name,
                    targetSets = if (isStrength) sets.size.coerceAtLeast(1) else 0,
                    targetReps = if (isStrength) targetReps else 0,
                    targetWeightKg = if (isStrength) targetWeight else 0.0,
                    orderInDay = i,
                    // 力量保持空串走旧三列；非力量写明确类型与目标配置，不复制实际成绩为目标。
                    recordType = if (isStrength) "" else item.recordType.key,
                    targetConfigJson = targetJson
                )
            })
        }
        return uuid to missing
    }

    suspend fun deactivatePlan(uuid: String) = planDao.setActive(uuid, false)

    /** 从历史里重新启用：仍然保证同时只有一个 isActive=true。 */
    suspend fun reactivatePlan(uuid: String) {
        db.withTransaction {
            planDao.deactivateAll()
            planDao.setActive(uuid, true)
        }
    }

    /** 删计划：连它下面的日与动作安排一起删，不留孤儿行。 */
    suspend fun deletePlan(uuid: String) {
        db.withTransaction {
            val dayIds = planDayDao.findByPlan(uuid).map { it.id }
            if (dayIds.isNotEmpty()) planExerciseDao.deleteByDayIds(dayIds)
            planDayDao.deleteByPlan(uuid)
            planDao.deleteByUuid(uuid)
        }
    }

    // 备份的整表导出/覆盖恢复统一放在 data/backup/TrainingBackup.kt + BackupManager，
    // 这里不再做第二套，免得以后两边字段不一致。
}

