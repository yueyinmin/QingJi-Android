# B1.1.2 复审收口：四项定点核查材料

- 仓库：`C:/Users/22109/.gradle/qingji-build/QingJi-Android`
- 分支：`feature/unified-training`
- 本轮代码提交：`344c13300abcd1be7bda2358d2e1160b984b6d11`
- 上一轮 B1.1.2 代码提交：`4358d6a77e82e7e4020d1c3964bfd2f3c9e9e1b4`
- 测试结果：`testDebugUnitTest` 826 项，0 失败 / 0 错误 / 3 跳过（新增 1 项条件清理测试）
- 构建结果：`assembleDebug`、`assembleRelease` 均 BUILD SUCCESSFUL
- 用户已有的 3 个设置页文件全程未触碰、未纳入任何提交
- Gradle 环境注记：本机默认 `preferIPv4Stack=true` 时守护进程 UDP 端口绑定失败；本轮统一用
  `-Dorg.gradle.jvmargs="... -Djava.net.preferIPv4Stack=false"` 执行，仅为环境绕行，不改仓库配置。


## 一、persist 与保存完成清理的竞态（已收口）

结论：ChatGPT 指出的竞态在原先写法下依赖「主线程串行」才碰不到，缺少机制保证。本轮已改为
**同一把锁内的按 revision 条件清理**，不再依赖时序假设：
1. `WorkoutDraftStore` 新增 `private val draftLock = Any()`；`save()`、`clear()`、新增的
   `clearIfRevision(expected)` 都在这把锁内执行；
2. `clearIfRevision()` 在锁内读取当前 SP 中草稿的 revision，只有等于 expected 才 remove；
3. `TrainingViewModel.save()` 成功后只在版本未变时调用 `clearIfRevision(snapshotRevision)`，
   返回 false 就保留新草稿，不清内存；
4. 保存期间的新草稿仍是同一个 `sessionUuid`，下次保存覆盖原场，不产生重复会话/账本/日记（T15/T16）。
新增测试 `条件清理只删除指定revision的草稿`：revision 6 不能删 revision 7；revision 7 可删。


```kotlin
    /**
     * B1.1.2 复审收口：草稿写入与「按 revision 条件清理」共用一把锁。
     * SharedPreferences.apply() 本身线程安全，但「读当前 revision + 删除」必须是
     * 一个不可分割的动作，否则保存完成时的清理可能误删保存期间刚写入的新草稿。
     */
    private val draftLock = Any()

    /**
     * B1.1.2：草稿写入前钩子，生产为空操作。internal + 默认空操作，仅测试注入异常以验证
     * 「写入失败时内存 revision 与 SP 中 revision 保持一致」（T20）。
     * SharedPreferences.apply() 只保证内存立即可见，物理磁盘写入由系统异步完成。
     */
    internal var saveHook: () -> Unit = {}
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

    fun save(draft: WorkoutDraft) = synchronized(draftLock) {
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

    fun clear() = synchronized(draftLock) { sp.edit().remove(KEY_DRAFT).apply() }

    /**
     * B1.1.2 复审收口：只在磁盘上的草稿仍是 expected 版本时才删除。
     * 读 revision 与删除在同一把 draftLock 内完成，保存期间的新写入不会被旧保存任务清掉。
     * 返回是否真的删除；调用方据此决定内存 _draft 是否置空。
     */
    internal fun clearIfRevision(expected: Long): Boolean = synchronized(draftLock) {
        if (load()?.revision == expected) {
            sp.edit().remove(KEY_DRAFT).apply()
            true
        } else {
            false
        }
    }
```

保存完成后的清理代码与 `persist()` 见第五节 5.1 / 5.3。

## 二、数据库提交成功后协程取消（边界如实标注）

已确认：
- 第一段 DB 异常分两类：`CancellationException` 原样重抛，`finally` 释放 `savingGuard` 与 `isSaving`；
  其他异常才 `onDone(false)`。取消不会被改写成「保存失败」。
- 第二段（清理/提示/回调）没有任何 suspend 调用，正常流程不会在其中被取消投递；`say()` 与
  `onDone(true)` 分别用 `runCatching` 保护，提示异常不会挡住成功回调。
- `CancellationException` 路径不调用 `onDone(false)`、不清草稿（T19 已验证锁释放与草稿保留）。
未能证明的边界：如果取消恰在 Room 事务已提交、结果返回给调用方那一刻投递，调用方仍可能收到
`CancellationException`，此时无法凭现有接口区分「未提交」与「已提交」；草稿会保留。该路径没有
自动化用例，也不宣称已验证。数据安全性上不会重复入账：`sourceId=session.uuid` 的账本 upsert +
明细整包 delete/insert 使再次保存仍是覆盖同一场（T15/T16 已验证）。如需严格在该边界也自动清草稿，
需要 Repository 暴露提交确认信号，属于新的接口改造，本轮未做。


## 三、备份恢复事务边界（代码 + 真实回滚测试）

结论：`restoreInto()` 是 internal suspend，生产唯一调用点在 `importJson()` 的 `db.withTransaction { ... }`
内；`restoreInto` 内部没有 `withContext`、没有嵌套 `withTransaction`，清表、`restoreHook()`、灌入、
自律段、汇总缓存清理全部通过传入的同一个 `target/db` 的 DAO 完成，因此处于同一 Room 事务。
`BackupRollbackB112Test` 在训练段清表后、灌入前抛异常，验证会话/力量明细/有氧明细/账本/日记投影/
备注全部回滚保留。测试使用内存 Room，**没有**做「关闭数据库再打开」的持久化复读，也没有真机验证。


```kotlin
    suspend fun importJson(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(IllegalStateException("打不开这个文件"))

            // ---------- 第一步：整份解析（完全不碰数据库） ----------
            // 这一步失败就原样返回，用户数据库一个字节都没动。
            // 旧版是把「清表」摆在最前面，解析到一半出错就变成
            // 「老数据没了、新数据只进一半」，找都找不回来。
            val parsed = parseBackup(text).getOrElse {
                return@withContext Result.failure(it)
            }

            // ---------- 第二步：解析全部成功，才开事务落库 ----------
            // 清「记录类」+ 灌入新数据都在 restoreInto 里，任何一步失败整事务回滚。
            db.withTransaction {
                restoreInto(db, parsed)
            }
            // 阶段B：仅在整份恢复事务成功提交后统一刷新一次（不在事务内、不逐条刷）。
            com.qingji.app.widget.WidgetRefresher.refresh(context)

            Result.success(summaryText(parsed))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    internal suspend fun restoreInto(target: AppDatabase, p: ParsedBackup) {
        // 清「记录类」+ 用户自建食物/收藏。
        // 预置运动库不动 —— 它是 App 自带的，不是用户数据。
        target.foodLogDao().clearAll()
        target.weightLogDao().clearAll()
        target.waterLogDao().clearAll()
        target.diaryDao().clearAll()
        target.usageStatDao().clearAll()
        target.userFoodDao().clearAll()
        target.foodFavoriteDao().clearAll()
        target.bodyMeasureDao().clearAll()
        target.progressPhotoDao().clearAll()
        target.exerciseLogDao().clearAll()
        target.fastingLogDao().clearAll()
        // 只清用户自建的运动，预置的 30 条留着
        target.exercisePresetDao()
            .deleteByCategory(com.qingji.app.domain.ExerciseCalculator.CUSTOM_CATEGORY)

        // v5 自建品牌段：只有 version>=5 的备份才整段覆盖，
        // 老备份（那时还没有品牌功能）导入时不清本机品牌，避免误删。
        if (p.version >= 5 && p.hasUserBrands) {
            target.userBrandDao().clearAll()
            if (p.userBrands.isNotEmpty()) target.userBrandDao().insertAllBrands(p.userBrands)
            if (p.userBrandDishes.isNotEmpty()) target.userBrandDao().insertAllDishes(p.userBrandDishes)
        }

        // v6 食物组段：只有 version>=6 的备份才整段覆盖，
        // 老备份（那时还没有食物组功能）导入时绝不清本机食物组，避免误删用户刚建的组。
        // 红线：清表绝不能放进上面「无条件清记录」那批（那批对 v1~v5 老备份也执行）。
        if (p.version >= 6 && p.hasUserFoodGroups) {
            target.userFoodGroupDao().clearAllItems()
            target.userFoodGroupDao().clearAllGroups()
            if (p.userFoodGroups.isNotEmpty()) target.userFoodGroupDao().insertAllGroups(p.userFoodGroups)
            if (p.userFoodGroupItems.isNotEmpty()) target.userFoodGroupDao().insertAllItems(p.userFoodGroupItems)
        }

        if (p.hasProfile) {
            val dao = target.userProfileDao()
            if (p.profile != null) dao.save(p.profile!!) else dao.clear()
        }
        // AI 配置：v7 多服务数组优先（整包覆盖），老备份只有单条 aiConfig 时保留为一条服务。
        // Key 仍不随备份走——恢复后若没有对应 Key，界面会显示「Key 未保存」要求重填。
        if (p.version >= 7 && p.hasAiConfigs) {
            val dao = target.aiConfigDao()
            dao.clearAll()
            p.aiConfigs.forEach { dao.save(it) }
            // 激活 ID 重新校验：指向不存在的配置时回退到第一条，没有则置 0（未配置）
            val restoredIds = p.aiConfigs.map { it.id }
            val active = com.qingji.app.data.prefs.SettingsStore.observe(context).first().aiActiveProviderId
            val valid = if (active in restoredIds) active else (restoredIds.firstOrNull() ?: 0)
            com.qingji.app.data.prefs.SettingsStore.set(
                context, com.qingji.app.data.prefs.SettingsKeys.AI_ACTIVE_PROVIDER_ID, valid
            )
        } else if (p.hasAiConfig) {
            val dao = target.aiConfigDao()
            if (p.aiConfig != null) dao.save(p.aiConfig!!) else dao.clearAll()
        }

        if (p.foods.isNotEmpty()) target.userFoodDao().insertAll(p.foods)

        // 收藏按名字存（标准库只读，收藏只能记在用户库里）
        if (p.favorites.isNotEmpty()) {
            p.favorites.forEach { name ->
                target.foodFavoriteDao().add(com.qingji.app.data.local.entity.FoodFavorite(name))
            }
        } else if (p.version < 2) {
            // v1 的老备份没有 foodFavorites 段，用「用户食物里 favorite=1」兜底
            p.foods.filter { it.favorite }.forEach {
                target.foodFavoriteDao().add(com.qingji.app.data.local.entity.FoodFavorite(it.name))
            }
        }

        if (p.foodLogs.isNotEmpty()) target.foodLogDao().insertAll(p.foodLogs)
        p.weights.forEach { target.weightLogDao().save(it) }
        p.waters.forEach { target.waterLogDao().insert(it) }
        p.diaries.forEach { target.diaryDao().save(it) }
        p.usage.forEach { target.usageStatDao().insertIfAbsent(it) }

        // v2 的五段：老备份里没有，列表是空的，这里自然什么都不做
        p.bodyMeasures.forEach { target.bodyMeasureDao().save(it) }
        p.photos.forEach { target.progressPhotoDao().insert(it) }
        p.exercises.forEach { target.exerciseLogDao().insert(it) }
        p.fastings.forEach { target.fastingLogDao().save(it) }
        p.customPresets.forEach { target.exercisePresetDao().insertOne(it) }

        // ---------- v3 训练段 ----------
        // 老备份（version < 3）里根本没有训练数据，一行都不动，避免把新记的训练清掉。
        // 段本身读坏了（hasTraining=true 但 training=null）也选择保留本机数据，宁可不覆盖。
        val t = p.training
        if (p.version >= 3 && t != null) {
            // 动作库不再 clearAll：旧备份里的 38/200 条内置动作不能把新 302 条覆盖掉。
            // 只恢复两类用户数据：自建动作（整条，用户优先）与内置动作的收藏状态。
            val exerciseDao = target.exerciseDao()
            val localNames = exerciseDao.findAll().associate { it.name to it.uuid }.toMutableMap()
            val customToRestore = mutableListOf<ExerciseEntity>()
            t.exercises.filter { !it.isBuiltIn }.forEach { backup ->
                var name = backup.name
                if (localNames[name] != null && localNames[name] != backup.uuid) {
                    var n = 2
                    while (localNames.containsKey(name)) {
                        name = backup.name + "（自建" + n + "）"
                        n++
                    }
                }
                localNames[name] = backup.uuid
                customToRestore.add(backup.copy(name = name))
            }
            if (customToRestore.isNotEmpty()) exerciseDao.insertAll(customToRestore)
            val now = System.currentTimeMillis()
            t.exercises.filter { it.isBuiltIn && it.isFavorite }.forEach {
                exerciseDao.setBuiltInFavorite(it.uuid, true, now)
            }

            target.trainingPlanDao().clearAll()
            target.trainingPlanDayDao().clearAll()
            target.planExerciseDao().clearAll()
            target.workoutSessionDao().clearAll()
            target.workoutExerciseDao().clearAll()
            target.workoutCardioDetailDao().clearAll()
            // B1.1.2：训练段「已清表、未灌入」的中途点注入异常，验证整事务回滚。
            restoreHook()
            if (t.plans.isNotEmpty()) target.trainingPlanDao().insertAll(t.plans)
            if (t.planDays.isNotEmpty()) target.trainingPlanDayDao().insertAll(t.planDays)
            if (t.planExercises.isNotEmpty()) target.planExerciseDao().insertAll(t.planExercises)
            if (t.sessions.isNotEmpty()) target.workoutSessionDao().insertAll(t.sessions)
            if (t.workoutExercises.isNotEmpty()) target.workoutExerciseDao().insertAll(t.workoutExercises)
            if (t.cardioDetails.isNotEmpty()) target.workoutCardioDetailDao().insertAll(t.cardioDetails)
        }

        // ---------- v4 自律段 ----------
        // 和 training 一样：version < 4 的老备份没有自律数据，而模块上线后本机可能已有记录，
        // 一行都不动，避免导旧备份把新记的自律数据清掉。
        // 段读坏了（hasSelfDiscipline=true 但 sd=null）也保留本机，宁可不覆盖。
        // 红线：清表绝不能放进上面「无条件清记录」那批——那批对 v1/v2/v3 老备份也执行。
        // 清灌都在调用方的 db.withTransaction 内，保持「整份解析成功才开事务」。
        val sd = p.selfDiscipline
        if (p.version >= 4 && sd != null) {
            target.selfDisciplineGoalDao().clearAll()
            target.selfDisciplinePeriodDao().clearAll()
            target.selfDisciplineCheckinDao().clearAll()
            target.selfDisciplineUrgeDao().clearAll()
            target.selfDisciplineSettingDao().clearAll()
            target.selfDisciplineMilestoneDao().clearAll()
            target.selfDisciplineInterventionDao().clearAll()
            // 恢复顺序：goals 先 → 6 张 goalId 关联 / 子表 → settings 最后
            if (sd.goals.isNotEmpty()) target.selfDisciplineGoalDao().insertAll(sd.goals)
            if (sd.periods.isNotEmpty()) target.selfDisciplinePeriodDao().insertAll(sd.periods)
            if (sd.checkins.isNotEmpty()) target.selfDisciplineCheckinDao().insertAll(sd.checkins)
            if (sd.urgeLogs.isNotEmpty()) target.selfDisciplineUrgeDao().insertAll(sd.urgeLogs)
            if (sd.milestones.isNotEmpty()) target.selfDisciplineMilestoneDao().insertAll(sd.milestones)
            if (sd.interventions.isNotEmpty()) target.selfDisciplineInterventionDao().insertAll(sd.interventions)
            if (sd.settings.isNotEmpty()) target.selfDisciplineSettingDao().insertAll(sd.settings)
        }

        // P1-01：备份恢复后清掉每日健康汇总缓存，否则下次查询仍读到恢复前的旧汇总。
        // 仍在调用方的 db.withTransaction 内，整份恢复失败时连带回滚。
        target.dailyHealthSummaryDao().clearAll()
    }
```


## 四、saveHook / restoreHook 生产隔离（已收敛）

- `TrainingRepository.saveHook`：`private val`，构造函数默认 `{}`，仅测试通过构造参数注入；
- `WorkoutDraftStore.saveHook`：本轮从 public 改为 `internal var`，默认 `{}`；
- `BackupManager.restoreHook`：本轮从 public 改为 `internal var`，默认 `{}`；
- 全仓 grep 仅测试文件对其赋值，业务 UI / ViewModel 无任何引用；生产默认空操作，不改变业务路径。
保持现状，不为此重构架构。


```kotlin
class TrainingRepository(
    private val context: Context,
    /** V3 最终缺陷修复：可注入刷新钩子（生产默认走 WidgetRefresher，测试可替换验证行为）。 */
    private val refreshHook: (Context, Set<com.qingji.app.widget.WidgetTarget>) -> Unit =
        { c, targets -> com.qingji.app.widget.WidgetRefresher.refresh(c, targets) },
    /** V2.1阶段A4 同款测试注入：独立 Room 实例，生产不传。 */
    dbOverride: AppDatabase? = null,
    /** B1.1.1 测试注入：保存事务内的钩子，生产为空。用于模拟慢保存/数据库异常。 */
    private val saveHook: suspend () -> Unit = {}
) {
```


## 五、指定源码全文（从当前 HEAD 原样导出）

### 5.1 TrainingViewModel.save() 完整实现

```kotlin
    fun save(onDone: (Boolean) -> Unit) {
        // 1) 抢锁：任何时刻只有一个 save 能往前走。 superstition: 不用 UI disable 当唯一保护。
        if (!savingGuard.compareAndSet(false, true)) {
            say("正在保存，请稍候")
            onDone(false)
            return
        }
        _isSaving.value = true

        val d = _draft.value
        if (d == null) {
            savingGuard.set(false)
            _isSaving.value = false
            onDone(false)
            return
        }
        // 2) 不可变快照：本次所有 DB 操作只读这个 d，不受后续编辑影响。
        val snapshot = d
        val snapshotRevision = snapshot.revision
        // B1.1 P0：强类型分流。空串按历史语义回退 STRENGTH；未知 key 降级 OTHER，绝不默认 CARDIO。
        val typed = d.exercises.map { e ->
            val entity = exercises.value.firstOrNull { it.uuid == e.exerciseUuid }
            val type = TrainingItemMapper.resolveType(e.recordType, entity)
            e to type
        }
        val strengthDrafts = typed.filter { (e, type) ->
            type == com.qingji.app.domain.ExerciseRecordType.STRENGTH &&
                e.sets.any { s -> s.reps > 0 || s.weightKg > 0.0 || (s.durationSec ?: 0) > 0 }
        }.map { (e, _) ->
            e.copy(sets = e.sets.filter { s -> s.reps > 0 || s.weightKg > 0.0 || (s.durationSec ?: 0) > 0 })
        }
        val nonStrength = typed.filter { (_, type) -> type != com.qingji.app.domain.ExerciseRecordType.STRENGTH }
        if (strengthDrafts.isEmpty() && nonStrength.isEmpty()) {
            say("至少要记一组有效力量或一个非力量项目才能保存")
            savingGuard.set(false)
            _isSaving.value = false
            onDone(false)
            return
        }

        val editing = d.editingUuid.isNotEmpty()
        // 2026-11-18：指定时长优先（补记必须有；正常练点了「改时长」也按它算）
        val overrideMin = d.durationOverrideMin
        val backfillDate = runCatching { LocalDate.parse(d.backfillDate) }.getOrNull()
        // QJ 来源信息：从计划启动的训练写 plan:<计划名>，其余保持 manual（旧行为）
        val sessionSource = if (d.sourcePlan.isNotBlank()) "plan:${d.sourcePlan}" else "manual"
        val session = if (editing) {
            WorkoutSession(
                uuid = d.sessionUuid,
                date = runCatching { LocalDate.parse(d.originalDate) }.getOrDefault(AppClock.today()),
                startedAt = d.fixedStartedAt,
                endedAt = d.fixedEndedAt,
                durationMinutes = overrideMin.takeIf { it > 0 } ?: d.fixedDurationMinutes,
                note = d.note,
                source = sessionSource,
                createdAt = d.fixedCreatedAt
            )
        } else if (backfillDate != null && overrideMin > 0) {
            // 补记：日期用选的那天，时长用手填的分钟数
            WorkoutSession(
                uuid = d.sessionUuid,
                date = backfillDate,
                startedAt = backfillDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endedAt = backfillDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() +
                    overrideMin * 60_000L,
                durationMinutes = overrideMin,
                note = d.note,
                source = sessionSource,
                createdAt = System.currentTimeMillis()
            )
        } else {
            val seconds = d.elapsedSeconds(System.currentTimeMillis())
            val durationMin = overrideMin.takeIf { it > 0 }
                ?: ((seconds + 59L) / 60L).toInt().coerceAtLeast(1)
            val endedAt = if (overrideMin > 0) d.startedAt + overrideMin * 60_000L
                else System.currentTimeMillis()
            WorkoutSession(
                uuid = d.sessionUuid,
                date = AppClock.today(),
                startedAt = d.startedAt,
                endedAt = endedAt,
                durationMinutes = durationMin,
                note = d.note,
                source = sessionSource,
                createdAt = System.currentTimeMillis()
            )
        }

        viewModelScope.launch {
            // B1.1.2 P0：weight 提到最外层作用域，供「数据库段」与「提示段」共用。
            val weight = settleWeight.value
            try {
                // ---------- 第一段：数据库保存。只有这里的异常才算「保存失败」 ----------
                val result = try {
                    val mets = metDefaults.value
                    // 按快照构建统一项目；目标只在 targetConfigJson，实际值只来自用户录入。
                    val allItems = snapshot.exercises.mapIndexed { i, e ->
                        val entity = exercises.value.firstOrNull { it.uuid == e.exerciseUuid }
                        val type = TrainingItemMapper.resolveType(e.recordType, entity)
                        if (type == com.qingji.app.domain.ExerciseRecordType.STRENGTH) {
                            val cleaned = if (strengthDrafts.any { it.key == e.key }) {
                                e.copy(sets = e.sets.filter { s -> s.reps > 0 || s.weightKg > 0.0 || (s.durationSec ?: 0) > 0 })
                            } else e
                            TrainingItemMapper.draftToItem(cleaned, type, snapshot.sessionUuid, i)
                        } else {
                            var item = TrainingItemMapper.draftToItem(e, type, snapshot.sessionUuid, i)
                            if (item is WorkoutItem.Cardio) {
                                item = TrainingItemMapper.withCardioKcal(item, entity, mets, weight)
                            }
                            item
                        }
                    }
                    val strengthItems = allItems.filterIsInstance<WorkoutItem.Strength>()
                    val cardioItems = allItems.filterIsInstance<WorkoutItem.Cardio>()
                    val otherItems = allItems.filter {
                        it !is WorkoutItem.Strength && it !is WorkoutItem.Cardio
                    }
                    // 统一入口：力量 + HIIT/柔韧/其他写 workout_exercise，只有 CARDIO 写有氧明细。
                    repo.saveUnifiedSession(session, strengthItems + otherItems, cardioItems, weight)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    // B1.1.2 P0：协程取消不是保存失败，交给结构化并发正确处理（不吞）。
                    throw c
                } catch (e: Exception) {
                    // 数据库/事务失败：草稿一个字节都不动，回调 false 让用户重试。
                    runCatching { say("保存失败，草稿已保留，请重试：${e.message ?: "未知错误"}") }
                    runCatching { onDone(false) }
                    return@launch
                }

                // ---------- 第二段：数据库已成功提交，以下任何异常都不改变「已保存」事实 ----------
                try {
                    // B1.1.2 复审收口：版本比对和清理在 WorkoutDraftStore 的同一把锁内完成，
                    // 且只删「本次保存的那一个 revision」，旧保存任务不可能清掉期间写入的新草稿。
                    val current = _draft.value
                    when {
                        current == null -> {
                            runCatching { draftStore.clear() }
                            _draft.value = null
                        }
                        current.revision == snapshotRevision -> {
                            val cleared = runCatching {
                                draftStore.clearIfRevision(snapshotRevision)
                            }.getOrDefault(false)
                            if (cleared) {
                                _draft.value = null
                            } else {
                                runCatching { say("这次训练已保存，但草稿版本已变化，草稿已保留以便检查") }
                            }
                        }
                        else -> {
                            // 保存期间又有新修改：新草稿（含同一 sessionUuid）留给下一次保存（T15/T16）
                            runCatching { say("这次训练已保存；你在保存期间的新修改仍保留在草稿里，可再次保存") }
                        }
                    }
                    val hasActual = snapshot.exercises.any { e ->
                        e.sets.any { s -> s.reps > 0 || s.weightKg > 0.0 } ||
                            WorkoutItem.fromJson(e.actualJson)?.hasActualData() == true
                    }
                    val kcalText = ExerciseCalorieHint.message(hasActual, weight > 0.0, result)
                    runCatching {
                        say(
                            when {
                                result.hasSkipped -> "训练已保存${kcalText}；${result.notComputableItems} 项缺少可靠时长/参数未计入消耗"
                                !editing -> "训练已保存${kcalText}"
                                else -> "已更新这次训练，消耗已重新结算${kcalText}"
                            }
                        )
                    }
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (e: Exception) {
                    // 草稿清理/提示异常：数据已落库，绝不误报保存失败，也不回滚数据库。
                    runCatching { say("训练已保存，但界面刷新遇到问题") }
                }
                // 回调只执行一次；其异常属于调用方 UI，不影响已经完成的数据库保存。
                runCatching { onDone(true) }
            } finally {
                // 所有正常 / 异常 / 取消路径都释放锁。
                savingGuard.set(false)
                _isSaving.value = false
            }
        }
    }
```

### 5.2 WorkoutDraftStore：load / save / clear / clearIfRevision

```kotlin
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

    fun save(draft: WorkoutDraft) = synchronized(draftLock) {
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

    fun clear() = synchronized(draftLock) { sp.edit().remove(KEY_DRAFT).apply() }

    /**
     * B1.1.2 复审收口：只在磁盘上的草稿仍是 expected 版本时才删除。
     * 读 revision 与删除在同一把 draftLock 内完成，保存期间的新写入不会被旧保存任务清掉。
     * 返回是否真的删除；调用方据此决定内存 _draft 是否置空。
     */
    internal fun clearIfRevision(expected: Long): Boolean = synchronized(draftLock) {
        if (load()?.revision == expected) {
            sp.edit().remove(KEY_DRAFT).apply()
            true
        } else {
            false
        }
    }
```

### 5.3 TrainingViewModel.persist()

```kotlin
    private fun persist(d: WorkoutDraft) {
        val versioned = d.copy(revision = d.revision + 1L)
        val ok = runCatching { draftStore.save(versioned) }
        if (ok.isSuccess) {
            _draft.value = versioned
        } else {
            say("草稿保存失败（存储空间可能不足），这次修改没有保留，请重试")
        }
    }
```

### 5.4 BackupManager.importJson() / restoreInto()

```kotlin
    suspend fun importJson(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(IllegalStateException("打不开这个文件"))

            // ---------- 第一步：整份解析（完全不碰数据库） ----------
            // 这一步失败就原样返回，用户数据库一个字节都没动。
            // 旧版是把「清表」摆在最前面，解析到一半出错就变成
            // 「老数据没了、新数据只进一半」，找都找不回来。
            val parsed = parseBackup(text).getOrElse {
                return@withContext Result.failure(it)
            }

            // ---------- 第二步：解析全部成功，才开事务落库 ----------
            // 清「记录类」+ 灌入新数据都在 restoreInto 里，任何一步失败整事务回滚。
            db.withTransaction {
                restoreInto(db, parsed)
            }
            // 阶段B：仅在整份恢复事务成功提交后统一刷新一次（不在事务内、不逐条刷）。
            com.qingji.app.widget.WidgetRefresher.refresh(context)

            Result.success(summaryText(parsed))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    internal suspend fun restoreInto(target: AppDatabase, p: ParsedBackup) {
        // 清「记录类」+ 用户自建食物/收藏。
        // 预置运动库不动 —— 它是 App 自带的，不是用户数据。
        target.foodLogDao().clearAll()
        target.weightLogDao().clearAll()
        target.waterLogDao().clearAll()
        target.diaryDao().clearAll()
        target.usageStatDao().clearAll()
        target.userFoodDao().clearAll()
        target.foodFavoriteDao().clearAll()
        target.bodyMeasureDao().clearAll()
        target.progressPhotoDao().clearAll()
        target.exerciseLogDao().clearAll()
        target.fastingLogDao().clearAll()
        // 只清用户自建的运动，预置的 30 条留着
        target.exercisePresetDao()
            .deleteByCategory(com.qingji.app.domain.ExerciseCalculator.CUSTOM_CATEGORY)

        // v5 自建品牌段：只有 version>=5 的备份才整段覆盖，
        // 老备份（那时还没有品牌功能）导入时不清本机品牌，避免误删。
        if (p.version >= 5 && p.hasUserBrands) {
            target.userBrandDao().clearAll()
            if (p.userBrands.isNotEmpty()) target.userBrandDao().insertAllBrands(p.userBrands)
            if (p.userBrandDishes.isNotEmpty()) target.userBrandDao().insertAllDishes(p.userBrandDishes)
        }

        // v6 食物组段：只有 version>=6 的备份才整段覆盖，
        // 老备份（那时还没有食物组功能）导入时绝不清本机食物组，避免误删用户刚建的组。
        // 红线：清表绝不能放进上面「无条件清记录」那批（那批对 v1~v5 老备份也执行）。
        if (p.version >= 6 && p.hasUserFoodGroups) {
            target.userFoodGroupDao().clearAllItems()
            target.userFoodGroupDao().clearAllGroups()
            if (p.userFoodGroups.isNotEmpty()) target.userFoodGroupDao().insertAllGroups(p.userFoodGroups)
            if (p.userFoodGroupItems.isNotEmpty()) target.userFoodGroupDao().insertAllItems(p.userFoodGroupItems)
        }

        if (p.hasProfile) {
            val dao = target.userProfileDao()
            if (p.profile != null) dao.save(p.profile!!) else dao.clear()
        }
        // AI 配置：v7 多服务数组优先（整包覆盖），老备份只有单条 aiConfig 时保留为一条服务。
        // Key 仍不随备份走——恢复后若没有对应 Key，界面会显示「Key 未保存」要求重填。
        if (p.version >= 7 && p.hasAiConfigs) {
            val dao = target.aiConfigDao()
            dao.clearAll()
            p.aiConfigs.forEach { dao.save(it) }
            // 激活 ID 重新校验：指向不存在的配置时回退到第一条，没有则置 0（未配置）
            val restoredIds = p.aiConfigs.map { it.id }
            val active = com.qingji.app.data.prefs.SettingsStore.observe(context).first().aiActiveProviderId
            val valid = if (active in restoredIds) active else (restoredIds.firstOrNull() ?: 0)
            com.qingji.app.data.prefs.SettingsStore.set(
                context, com.qingji.app.data.prefs.SettingsKeys.AI_ACTIVE_PROVIDER_ID, valid
            )
        } else if (p.hasAiConfig) {
            val dao = target.aiConfigDao()
            if (p.aiConfig != null) dao.save(p.aiConfig!!) else dao.clearAll()
        }

        if (p.foods.isNotEmpty()) target.userFoodDao().insertAll(p.foods)

        // 收藏按名字存（标准库只读，收藏只能记在用户库里）
        if (p.favorites.isNotEmpty()) {
            p.favorites.forEach { name ->
                target.foodFavoriteDao().add(com.qingji.app.data.local.entity.FoodFavorite(name))
            }
        } else if (p.version < 2) {
            // v1 的老备份没有 foodFavorites 段，用「用户食物里 favorite=1」兜底
            p.foods.filter { it.favorite }.forEach {
                target.foodFavoriteDao().add(com.qingji.app.data.local.entity.FoodFavorite(it.name))
            }
        }

        if (p.foodLogs.isNotEmpty()) target.foodLogDao().insertAll(p.foodLogs)
        p.weights.forEach { target.weightLogDao().save(it) }
        p.waters.forEach { target.waterLogDao().insert(it) }
        p.diaries.forEach { target.diaryDao().save(it) }
        p.usage.forEach { target.usageStatDao().insertIfAbsent(it) }

        // v2 的五段：老备份里没有，列表是空的，这里自然什么都不做
        p.bodyMeasures.forEach { target.bodyMeasureDao().save(it) }
        p.photos.forEach { target.progressPhotoDao().insert(it) }
        p.exercises.forEach { target.exerciseLogDao().insert(it) }
        p.fastings.forEach { target.fastingLogDao().save(it) }
        p.customPresets.forEach { target.exercisePresetDao().insertOne(it) }

        // ---------- v3 训练段 ----------
        // 老备份（version < 3）里根本没有训练数据，一行都不动，避免把新记的训练清掉。
        // 段本身读坏了（hasTraining=true 但 training=null）也选择保留本机数据，宁可不覆盖。
        val t = p.training
        if (p.version >= 3 && t != null) {
            // 动作库不再 clearAll：旧备份里的 38/200 条内置动作不能把新 302 条覆盖掉。
            // 只恢复两类用户数据：自建动作（整条，用户优先）与内置动作的收藏状态。
            val exerciseDao = target.exerciseDao()
            val localNames = exerciseDao.findAll().associate { it.name to it.uuid }.toMutableMap()
            val customToRestore = mutableListOf<ExerciseEntity>()
            t.exercises.filter { !it.isBuiltIn }.forEach { backup ->
                var name = backup.name
                if (localNames[name] != null && localNames[name] != backup.uuid) {
                    var n = 2
                    while (localNames.containsKey(name)) {
                        name = backup.name + "（自建" + n + "）"
                        n++
                    }
                }
                localNames[name] = backup.uuid
                customToRestore.add(backup.copy(name = name))
            }
            if (customToRestore.isNotEmpty()) exerciseDao.insertAll(customToRestore)
            val now = System.currentTimeMillis()
            t.exercises.filter { it.isBuiltIn && it.isFavorite }.forEach {
                exerciseDao.setBuiltInFavorite(it.uuid, true, now)
            }

            target.trainingPlanDao().clearAll()
            target.trainingPlanDayDao().clearAll()
            target.planExerciseDao().clearAll()
            target.workoutSessionDao().clearAll()
            target.workoutExerciseDao().clearAll()
            target.workoutCardioDetailDao().clearAll()
            // B1.1.2：训练段「已清表、未灌入」的中途点注入异常，验证整事务回滚。
            restoreHook()
            if (t.plans.isNotEmpty()) target.trainingPlanDao().insertAll(t.plans)
            if (t.planDays.isNotEmpty()) target.trainingPlanDayDao().insertAll(t.planDays)
            if (t.planExercises.isNotEmpty()) target.planExerciseDao().insertAll(t.planExercises)
            if (t.sessions.isNotEmpty()) target.workoutSessionDao().insertAll(t.sessions)
            if (t.workoutExercises.isNotEmpty()) target.workoutExerciseDao().insertAll(t.workoutExercises)
            if (t.cardioDetails.isNotEmpty()) target.workoutCardioDetailDao().insertAll(t.cardioDetails)
        }

        // ---------- v4 自律段 ----------
        // 和 training 一样：version < 4 的老备份没有自律数据，而模块上线后本机可能已有记录，
        // 一行都不动，避免导旧备份把新记的自律数据清掉。
        // 段读坏了（hasSelfDiscipline=true 但 sd=null）也保留本机，宁可不覆盖。
        // 红线：清表绝不能放进上面「无条件清记录」那批——那批对 v1/v2/v3 老备份也执行。
        // 清灌都在调用方的 db.withTransaction 内，保持「整份解析成功才开事务」。
        val sd = p.selfDiscipline
        if (p.version >= 4 && sd != null) {
            target.selfDisciplineGoalDao().clearAll()
            target.selfDisciplinePeriodDao().clearAll()
            target.selfDisciplineCheckinDao().clearAll()
            target.selfDisciplineUrgeDao().clearAll()
            target.selfDisciplineSettingDao().clearAll()
            target.selfDisciplineMilestoneDao().clearAll()
            target.selfDisciplineInterventionDao().clearAll()
            // 恢复顺序：goals 先 → 6 张 goalId 关联 / 子表 → settings 最后
            if (sd.goals.isNotEmpty()) target.selfDisciplineGoalDao().insertAll(sd.goals)
            if (sd.periods.isNotEmpty()) target.selfDisciplinePeriodDao().insertAll(sd.periods)
            if (sd.checkins.isNotEmpty()) target.selfDisciplineCheckinDao().insertAll(sd.checkins)
            if (sd.urgeLogs.isNotEmpty()) target.selfDisciplineUrgeDao().insertAll(sd.urgeLogs)
            if (sd.milestones.isNotEmpty()) target.selfDisciplineMilestoneDao().insertAll(sd.milestones)
            if (sd.interventions.isNotEmpty()) target.selfDisciplineInterventionDao().insertAll(sd.interventions)
            if (sd.settings.isNotEmpty()) target.selfDisciplineSettingDao().insertAll(sd.settings)
        }

        // P1-01：备份恢复后清掉每日健康汇总缓存，否则下次查询仍读到恢复前的旧汇总。
        // 仍在调用方的 db.withTransaction 内，整份恢复失败时连带回滚。
        target.dailyHealthSummaryDao().clearAll()
    }
```

### 5.5 T15/T16/T17/T18/T19/T20 测试全文：TrainingViewModelB112Test.kt

```kotlin
package com.qingji.app.training

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.prefs.DraftExercise
import com.qingji.app.data.prefs.WorkoutDraft
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TargetConfig
import com.qingji.app.domain.TargetConfigJson
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

/**
 * B1.1.2 ViewModel 层测试：T15/T16 两次保存语义、T17/T18 混合草稿保存期间修改、
 * T19 取消后锁释放、T20 落盘失败内存/磁盘一致。
 * 全部走真实 Room + Repository + ViewModel（saveHook 仅注入延迟/异常，不替换 DB 行为）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrainingViewModelB112Test {

    private lateinit var db: AppDatabase
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun repoWith(saveHook: suspend () -> Unit = {}) =
        TrainingRepository(ctx, refreshHook = { _, _ -> }, dbOverride = db, saveHook = saveHook)

    private fun vm(repo: TrainingRepository) = TrainingViewModel(app, repo)

    /** 仅力量的草稿：一组 40kg。 */
    private fun strengthDraft(uuid: String, revision: Long = 1L, editing: Boolean = false) = WorkoutDraft(
        sessionUuid = uuid, startedAt = 1L, revision = revision,
        editingUuid = if (editing) uuid else "",
        originalDate = "2026-11-22", fixedDurationMinutes = 30,
        exercises = listOf(
            DraftExercise(
                key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))
            )
        )
    )

    private suspend fun sessionCount(uuid: String) =
        db.workoutSessionDao().findAll().count { it.uuid == uuid }
    private suspend fun ledgerCount(uuid: String) =
        db.exerciseSessionCalorieDao()
            .findSourceIds(HealthRepository.SOURCE_STRENGTH_TRAINING).count { it == uuid }
    private suspend fun diaryOf(uuid: String) = db.exerciseLogDao().findBySourceId(uuid)

    // ==================== T15：新建训练，保存期间修改草稿后再次保存 ====================
    @Test
    fun `T15 新建训练两次保存只一场会话且最终内容与新草稿一致`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        // 第一次保存卡在 gate，第二次直接放行
        val repo = repoWith(saveHook = {
            if (call.incrementAndGet() == 1) gate.await()
        })
        repo.draftStore.save(strengthDraft("vm112-t15"))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }   // 第一次：卡在事务内
        // 保存期间把重量从 40 改成 50（revision 递增，草稿保留）
        vm.updateSet("d1", 0, WorkoutSet(weightKg = 50.0, reps = 8))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        // 第一次保存后：草稿应仍在（新 revision），旧快照 40kg 已落库
        assertNotNull("保存期间的新草稿必须保留", repo.draftStore.load())
        assertEquals(1, sessionCount("vm112-t15"))

        // 第二次保存：用新草稿覆盖同一场
        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        // 验证：仍是一场会话/一条账本/一条日记，sessionUuid 不变，明细为最新 50kg
        assertEquals("不得产生重复会话", 1, sessionCount("vm112-t15"))
        assertEquals("账本不得重复", 1, ledgerCount("vm112-t15"))
        assertNotNull(diaryOf("vm112-t15"))
        val rows = db.workoutExerciseDao().findBySession("vm112-t15")
        assertEquals(1, rows.size)
        val sets = com.qingji.app.domain.TrainingMath.decodeSets(rows[0].setsJson)
        assertEquals("最终内容必须是新草稿 50kg", 50.0, sets[0].weightKg, 0.001)
        assertEquals("最终次数必须是新草稿 8 次", 8, sets[0].reps)
        assertNull("成功后草稿应清理", repo.draftStore.load())
    }

    // ==================== T16：编辑历史训练，两次保存仍更新原场、不波及其他场 ====================
    @Test
    fun `T16 编辑历史训练两次保存只更新原历史场不覆盖其他场`() = runBlocking {
        // 预置两场历史训练：目标场 + 另一个场
        val plainRepo = repoWith()
        plainRepo.saveUnifiedSession(
            com.qingji.app.data.local.entity.WorkoutSession(
                uuid = "vm112-hist", date = java.time.LocalDate.of(2026, 11, 20),
                durationMinutes = 30, note = "原历史", source = "manual", createdAt = 1L
            ),
            listOf(WorkoutItem.Strength(
                itemId = "hs", exerciseUuid = "ex-squat", name = "深蹲", order = 0,
                sets = listOf(WorkoutSet(weightKg = 30.0, reps = 10))
            )), emptyList(), 70.0
        )
        plainRepo.saveUnifiedSession(
            com.qingji.app.data.local.entity.WorkoutSession(
                uuid = "vm112-other", date = java.time.LocalDate.of(2026, 11, 20),
                durationMinutes = 20, note = "其他历史", source = "manual", createdAt = 2L
            ),
            listOf(WorkoutItem.Strength(
                itemId = "os", exerciseUuid = "ex-bench", name = "卧推", order = 0,
                sets = listOf(WorkoutSet(weightKg = 60.0, reps = 5))
            )), emptyList(), 70.0
        )

        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        val vm = vm(repo)
        // 走真实 editSession 载入历史场草稿
        val loaded = CompletableDeferred<Unit>()
        vm.editSession("vm112-hist") { loaded.complete(Unit) }
        withTimeout(5_000) { loaded.await() }
        assertEquals("editingUuid 必须指向原历史场", "vm112-hist", vm.draft.value!!.editingUuid)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }
        val before = vm.draft.value!!
        vm.updateSet(before.exercises[0].key, 0, WorkoutSet(weightKg = 45.0, reps = 6))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })
        assertNotNull(repo.draftStore.load())

        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        // 目标历史场仍是原 uuid、一场、明细更新为 45kg
        assertEquals("原历史场不得重复", 1, sessionCount("vm112-hist"))
        val histRows = db.workoutExerciseDao().findBySession("vm112-hist")
        assertEquals(45.0, com.qingji.app.domain.TrainingMath.decodeSets(histRows[0].setsJson)[0].weightKg, 0.001)
        assertEquals("原历史日期不得被改成今天",
            java.time.LocalDate.of(2026, 11, 20), db.workoutSessionDao().findByUuid("vm112-hist")!!.date)
        assertEquals(1, ledgerCount("vm112-hist"))
        assertNotNull(diaryOf("vm112-hist"))
        // 其他历史场一字未动
        assertEquals(1, sessionCount("vm112-other"))
        assertEquals("其他历史场重量不得被覆盖", 60.0,
            com.qingji.app.domain.TrainingMath.decodeSets(
                db.workoutExerciseDao().findBySession("vm112-other")[0].setsJson)[0].weightKg, 0.001)
    }

    // ==================== T17：混合训练保存期间新增有氧动作 ====================
    @Test
    fun `T17 混合训练保存期间新增有氧草稿再次保存后力量有氧都在`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        val running = WorkoutItem.Cardio(
            itemId = "c-run", exerciseUuid = "wg:running", name = "跑步", order = 1,
            durationSec = 1200L, distanceMeters = 4000.0,
            calculatedKcal = 100.0, calculationMethod = "met_type_default",
            calculationVersion = "v1", weightSnapshot = 70.0
        )
        val draft = WorkoutDraft(
            sessionUuid = "vm112-t17", startedAt = 1L, revision = 3L,
            exercises = listOf(
                DraftExercise(key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                    sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))),
                DraftExercise(key = "d2", exerciseUuid = "wg:running", name = "跑步",
                    recordType = ExerciseRecordType.CARDIO.key,
                    actualJson = WorkoutItem.toJson(running))
            )
        )
        repo.draftStore.save(draft)
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }
        // 保存期间把有氧实际时长改成 1800s
        vm.updateActual("d2", running.copy(durationSec = 1800L))
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        assertEquals(1, sessionCount("vm112-t17"))
        assertEquals("力量明细应在", 1, db.workoutExerciseDao().findBySession("vm112-t17").size)
        val cardio = db.workoutCardioDetailDao().findBySession("vm112-t17")
        assertEquals("有氧明细应在", 1, cardio.size)
        assertEquals("有氧实际时长应为新值 1800s", 1800L, cardio[0].durationSeconds)
        assertEquals("混合日记分类", "混合", diaryOf("vm112-t17")!!.type)
    }

    // ==================== T18：混合训练保存期间删除动作 ====================
    @Test
    fun `T18 混合训练保存期间删除有氧动作再次保存后有氧明细被清除`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val call = AtomicInteger(0)
        val repo = repoWith(saveHook = { if (call.incrementAndGet() == 1) gate.await() })
        val running = WorkoutItem.Cardio(
            itemId = "c-run", exerciseUuid = "wg:running", name = "跑步", order = 1,
            durationSec = 1200L, distanceMeters = 4000.0,
            calculatedKcal = 100.0, calculationMethod = "met_type_default",
            calculationVersion = "v1", weightSnapshot = 70.0
        )
        repo.draftStore.save(WorkoutDraft(
            sessionUuid = "vm112-t18", startedAt = 1L, revision = 2L,
            exercises = listOf(
                DraftExercise(key = "d1", exerciseUuid = "ex-squat", name = "深蹲",
                    sets = listOf(WorkoutSet(weightKg = 40.0, reps = 10))),
                DraftExercise(key = "d2", exerciseUuid = "wg:running", name = "跑步",
                    recordType = ExerciseRecordType.CARDIO.key,
                    actualJson = WorkoutItem.toJson(running))
            )
        ))
        val vm = vm(repo)

        val first = CompletableDeferred<Boolean>()
        vm.save { first.complete(it) }
        // 保存期间删掉有氧动作
        vm.removeExercise("d2")
        gate.complete(Unit)
        assertTrue(withTimeout(5_000) { first.await() })

        val second = CompletableDeferred<Boolean>()
        vm.save { second.complete(it) }
        assertTrue(withTimeout(5_000) { second.await() })

        assertEquals(1, sessionCount("vm112-t18"))
        assertEquals(1, db.workoutExerciseDao().findBySession("vm112-t18").size)
        assertTrue("被删除的有氧明细必须清除",
            db.workoutCardioDetailDao().findBySession("vm112-t18").isEmpty())
        assertEquals("删掉有氧后应归为纯力量", "力量", diaryOf("vm112-t18")!!.type)
    }

    // ==================== T19：保存期间协程取消后锁正确释放 ====================
    @Test
    fun `T19 保存被取消后保存锁正确释放且草稿保留`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repo = repoWith(saveHook = {
            gate.await()
            throw CancellationException("模拟协程取消")
        })
        repo.draftStore.save(strengthDraft("vm112-t19", revision = 9L))
        val vm = vm(repo)

        val done = CompletableDeferred<Boolean>()
        vm.save { done.complete(it) }
        gate.complete(Unit)
        // 取消不回调 onDone(false)（不是普通失败），用 isSaving 轮询锁释放
        withTimeout(5_000) {
            while (vm.isSaving.value) kotlinx.coroutines.delay(10)
        }
        assertFalse("取消后保存锁必须释放", vm.isSaving.value)
        assertTrue("取消不得被当成普通保存失败回调 false", !done.isCompleted)
        assertNotNull("取消不应清草稿", repo.draftStore.load())
        // 锁释放后仍可再次正常保存
        val repo2 = repoWith()
        repo2.draftStore.save(strengthDraft("vm112-t19b"))
        val vm2 = vm(repo2)
        val ok = CompletableDeferred<Boolean>()
        vm2.save { ok.complete(it) }
        assertTrue(withTimeout(5_000) { ok.await() })
    }

    // ==================== 复审收口：按 revision 条件清理，不与保存期间的新写入互相覆盖 ====================
    @Test
    fun `条件清理只删除指定revision的草稿`() = runBlocking {
        val store = repoWith().draftStore
        store.save(strengthDraft("vm112-cond", revision = 7L))

        assertFalse("旧 revision 不得删掉新草稿", store.clearIfRevision(6L))
        assertNotNull("条件清理失败时草稿必须还在", store.load())
        assertEquals(7L, store.load()!!.revision)

        assertTrue("同 revision 才允许清理", store.clearIfRevision(7L))
        assertNull("条件清理成功后草稿应清空", store.load())
    }

    // ==================== P0-1：数据库成功但 UI 回调异常不得误判为保存失败 ====================
    @Test
    fun `回调异常不得把已成功的数据库保存改成失败`() = runBlocking {
        val repo = repoWith()
        repo.draftStore.save(strengthDraft("vm112-cb"))
        val vm = vm(repo)

        var successCalled = 0
        var failCalled = 0
        val done = CompletableDeferred<Unit>()
        // onDone(true) 内部抛异常，模拟 UI 关闭页面/导航回调失败
        vm.save { ok ->
            if (ok) {
                successCalled++
                done.complete(Unit)
                throw RuntimeException("模拟 UI 回调异常")
            } else failCalled++
        }
        withTimeout(5_000) { done.await() }
        kotlinx.coroutines.delay(100) // 让 runCatching 吞掉回调异常后的 finally 执行完

        // 数据库确实保存成功；回调异常不能被 catch 转成 onDone(false)
        assertEquals(1, sessionCount("vm112-cb"))
        assertEquals("成功回调只应调用一次", 1, successCalled)
        assertEquals("UI 回调异常绝不能触发失败回调", 0, failCalled)
        assertFalse("锁必须释放", vm.isSaving.value)
        assertNull("保存成功草稿应清理", repo.draftStore.load())
    }

    // ==================== T20：草稿落盘失败时内存与磁盘 revision 一致 ====================
    @Test
    fun `T20 草稿落盘失败时内存与磁盘revision保持一致`() = runBlocking {
        val repo = repoWith()
        repo.draftStore.save(strengthDraft("vm112-t20", revision = 5L))
        val vm = vm(repo)
        val before = vm.draft.value!!.revision  // 5

        // 注入落盘失败
        var fail = true
        repo.draftStore.saveHook = {
            if (fail) throw IllegalStateException("模拟存储写入失败")
        }
        // 通过公开修改入口触发 persist：落盘失败
        vm.updateSet("d1", 0, WorkoutSet(weightKg = 99.0, reps = 3))
        val afterMem = vm.draft.value!!
        val afterDisk = repo.draftStore.load()!!
        assertEquals("落盘失败时内存 revision 不得递增", before, afterMem.revision)
        assertEquals("内存与磁盘 revision 必须一致", afterDisk.revision, afterMem.revision)
        assertEquals("内存与磁盘重量必须一致", afterMem.exercises[0].sets[0].weightKg,
            afterDisk.exercises[0].sets[0].weightKg, 0.001)

        // 恢复落盘后重试：revision 正常递增且内存磁盘一致
        fail = false
        vm.updateSet("d1", 0, WorkoutSet(weightKg = 55.0, reps = 7))
        val retried = vm.draft.value!!
        assertEquals(before + 1, retried.revision)
        assertEquals(retried.revision, repo.draftStore.load()!!.revision)
        assertEquals(55.0, repo.draftStore.load()!!.exercises[0].sets[0].weightKg, 0.001)
    }
}
```

### 5.6 备份事务回滚测试全文：BackupRollbackB112Test.kt

```kotlin
package com.qingji.app.training

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.data.backup.TrainingBackup
import com.qingji.app.data.backup.parseBackup
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.BackupFormat
import com.qingji.app.domain.WorkoutCalorieCalculator
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * B1.1.2：真实 restoreInto() 事务中途异常回滚测试。
 * 区分于 B1.1.1 的「解析失败保护」——这里异常发生在 db.withTransaction 内、
 * 训练段已清表未灌入的中途点，验证 Room 整事务回滚，已有数据一字不动。
 */
@RunWith(RobolectricTestRunner::class)
class BackupRollbackB112Test {

    private lateinit var db: AppDatabase
    private lateinit var repo: TrainingRepository
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TrainingRepository(ctx, refreshHook = { _, _ -> }, dbOverride = db)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `恢复中途真实数据库异常后整事务回滚已有训练明细账本日记全部保留`() = runBlocking {
        // 1) 本机先存一场混合训练（力量 + 有氧 + 账本 + 日记投影）
        val uuid = "rollback-keep"
        repo.saveUnifiedSession(
            WorkoutSession(uuid, LocalDate.of(2026, 11, 21), 30, note = "要保住的训练",
                source = "manual", createdAt = 1L),
            listOf(WorkoutItem.Strength(
                itemId = "s1", exerciseUuid = "ex-squat", name = "深蹲", order = 0,
                sets = listOf(WorkoutSet(weightKg = 50.0, reps = 10)))),
            listOf(WorkoutItem.Cardio(
                itemId = "c1", exerciseUuid = "wg:run", name = "跑步", order = 1,
                durationSec = 1800L, distanceMeters = 5000.0,
                calculatedKcal = 150.0, calculationMethod = WorkoutCalorieCalculator.METHOD_MET_TYPE_DEFAULT,
                calculationVersion = WorkoutCalorieCalculator.VERSION, weightSnapshot = 70.0)),
            70.0
        )
        // 记录异常前快照
        assertNotNull(db.workoutSessionDao().findByUuid(uuid))
        assertEquals(1, db.workoutExerciseDao().findBySession(uuid).size)
        assertEquals(1, db.workoutCardioDetailDao().findBySession(uuid).size)
        assertNotNull(db.exerciseSessionCalorieDao()
            .findBySource(HealthRepository.SOURCE_STRENGTH_TRAINING, uuid))
        assertNotNull(db.exerciseLogDao().findBySourceId(uuid))

        // 2) 构造一份合法 v8 备份（training 段有数据，足以走到清表后灌入）
        val backup = TrainingBackup(
            exercises = db.exerciseDao().findAll(),
            sessions = db.workoutSessionDao().findAll(),
            workoutExercises = db.workoutExerciseDao().findAll(),
            cardioDetails = db.workoutCardioDetailDao().findAll()
        )
        val root = org.json.JSONObject().apply {
            put("app", "qingji")
            put("version", BackupFormat.VERSION)
            put("training", backup.toJson())
        }
        val parsed = parseBackup(root.toString()).getOrThrow()

        // 3) 真实 restoreInto()，在训练段清表后抛异常；调用方事务包住（与 importJson 一致）
        val manager = BackupManager(ctx)
        var thrown = false
        manager.restoreHook = { throw IllegalStateException("模拟灌入中途数据库异常") }
        try {
            db.withTransaction {
                manager.restoreInto(db, parsed)
            }
            fail("应抛出事务中途异常")
        } catch (e: IllegalStateException) {
            thrown = e.message?.contains("数据库异常") == true
        }
        assertTrue("restoreHook 异常应传播出来", thrown)

        // 4) 验证整事务回滚：力量/有氧明细、账本、日记投影全部原样保留
        assertNotNull("会话必须回滚保留", db.workoutSessionDao().findByUuid(uuid))
        assertEquals("力量明细必须回滚保留", 1, db.workoutExerciseDao().findBySession(uuid).size)
        assertEquals("有氧明细必须回滚保留", 1, db.workoutCardioDetailDao().findBySession(uuid).size)
        assertNotNull("账本必须回滚保留",
            db.exerciseSessionCalorieDao().findBySource(HealthRepository.SOURCE_STRENGTH_TRAINING, uuid))
        assertNotNull("日记投影必须回滚保留", db.exerciseLogDao().findBySourceId(uuid))
        assertEquals("训练备注不得被覆盖", "要保住的训练",
            db.workoutSessionDao().findByUuid(uuid)!!.note)
    }
}
```

### 5.7 本轮完整 diff
见同目录 `B1.1.2复审收口完整diff.patch`。