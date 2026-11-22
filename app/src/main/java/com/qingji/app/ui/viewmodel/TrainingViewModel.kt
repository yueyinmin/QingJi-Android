package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.prefs.DraftExercise
import com.qingji.app.data.prefs.WorkoutDraft
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.PlanToDraftMapper
import com.qingji.app.domain.RestTimerState
import com.qingji.app.domain.SessionCalorieCalculator
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * ============================================================
 * 训练中心 P0 的 ViewModel
 * ------------------------------------------------------------
 * 「进行中训练」的全部状态放在 WorkoutDraft 里，每改一次就同步写一次草稿文件，
 * 所以杀进程、切页面、来电中断后回来，动作和组数据都还在。
 * 计时只存开始时间戳（不存「已经过了多少秒」），系统时间被改也不会算错。
 * ============================================================
 */
/** QJ 训练联动：开始训练初始化状态（供界面显示进度 / 失败重试）。 */
enum class TrainingInitState {
    Idle,           // 还没开始初始化
    Loading,        // 正在读取当天计划
    Restored,       // 恢复了已有草稿
    PlanLoaded,     // 已按当天计划载入动作
    Blank,          // 没有计划，空白训练
    Failed          // 计划读取失败（已按空白继续，界面给重试提示）
}

class TrainingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TrainingRepository(app)
    val draftStore = repo.draftStore

    // ---------- v1.4 健康体系：组间休息计时 ----------
    // 时间戳驱动（和整场训练计时同一个思路）：只存「倒计时到哪一刻结束」，
    // 页面怎么重组、每秒怎么刷新都不会丢；暂停时把剩余毫秒冻结下来。
    // 状态类在 domain/RestTimer.kt（纯逻辑可单测），这里只管持有和流转。
    private val _restTimer = MutableStateFlow(RestTimerState())
    val restTimer: StateFlow<RestTimerState> = _restTimer.asStateFlow()

    private val _restDefaultSec = MutableStateFlow(repo.draftStore.restDefaultSeconds())
    val restDefaultSec: StateFlow<Int> = _restDefaultSec.asStateFlow()

    /** 开始一轮休息倒计时。正在计时再来一次 = 从头重新计。 */
    fun startRest(seconds: Int = _restDefaultSec.value) {
        val t = System.currentTimeMillis()
        val total = seconds.coerceIn(15, 600) * 1000L
        _restTimer.value = RestTimerState(endsAt = t + total, totalMs = total)
    }

    fun pauseRest() {
        _restTimer.value = _restTimer.value.pausedAt(System.currentTimeMillis())
    }

    fun resumeRest() {
        _restTimer.value = _restTimer.value.resumedAt(System.currentTimeMillis())
    }

    /** 跳过 / 关掉这轮休息（结束后再点一次也能关掉提示）。 */
    fun skipRest() {
        _restTimer.value = RestTimerState()
    }

    /** 改默认休息时长（15~600 秒），立即写进偏好，下次休息按新值。 */
    fun setRestDefault(seconds: Int) {
        val v = seconds.coerceIn(15, 600)
        repo.draftStore.setRestDefaultSeconds(v)
        _restDefaultSec.value = v
    }

    val exercises: StateFlow<List<ExerciseEntity>> = repo.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<WorkoutSession>> = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 所有训练明细，按 sessionUuid 分组后给历史列表算组数 / 训练量。 */
    val allWorkoutExercises: StateFlow<List<WorkoutExercise>> = repo.observeAllWorkoutExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- v1.4 健康体系：热量估算支持 ----------
    /** 结算/预览用体重（口径与现有运动模块一致：档案起始体重，兜底 70kg）。 */
    val settleWeight: StateFlow<Double> = repo.observeSettleWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 70.0)

    /** 类型级 MET 参数（启动时读一次，参数库是只读种子数据）。 */
    val metDefaults: StateFlow<Map<String, Triple<Double, Double, Double>>> =
        kotlinx.coroutines.flow.flow { emit(repo.health().typeMetDefaults()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 某个动作的 MET 三档：拉伸走 flexibility，其余走中等力量；参数库没读到给兜底。 */
    fun metForExercise(exerciseUuid: String, isStretch: Boolean): Triple<Double, Double, Double> {
        val defaults = metDefaults.value
        val type = if (isStretch) "flexibility" else "strength_moderate"
        return defaults[type]
            ?: defaults["strength_moderate"]
            ?: Triple(4.25, 5.0, 5.75)
    }

    private val _draft = MutableStateFlow(repo.draftStore.load())
    val draft: StateFlow<WorkoutDraft?> = _draft.asStateFlow()

    /** 界面每秒 tick 一次刷新计时条；StateFlow 存时间戳，不存累计秒数。 */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()
    fun tick() { _now.value = System.currentTimeMillis() }

    private val _unit = MutableStateFlow(repo.draftStore.unit())
    val unit: StateFlow<String> = _unit.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    /** QJ 训练联动：初始化状态，界面据此显示进度 / 失败重试。 */
    private val _initState = MutableStateFlow(TrainingInitState.Idle)
    val initState: StateFlow<TrainingInitState> = _initState.asStateFlow()

    init {
        // 首次进入时灌最小内置动作库
        viewModelScope.launch { repo.ensureSeed() }
        // 健康体系：历史训练场次补算进消耗账本（幂等，重复跑不会写重）
        viewModelScope.launch {
            runCatching { repo.health().ensureLedgerBackfill(repo.observeSettleWeight().first()) }
        }
    }

    fun setUnit(unit: String) {
        draftStore.setUnit(unit)
        _unit.value = draftStore.unit()
    }

    /**
     * 统一训练启动入口（QJ 训练联动 P0）。
     * 所有「正常新建训练」入口最终都由 TrainingActiveScreen 调到这里，
     * 计划识别只此一处，不允许各页面自己拼装动作。
     *
     * 优先级：
     * 1. 已有有效草稿 → 原样恢复，不重载计划、不重复加动作、不覆盖用户改过的参数；
     * 2. 有「动作详情页带过来的指定动作」→ 建空白草稿，由 Screen 随后 addExercise（不载计划）；
     * 3. 当天有启用的训练计划安排 → 按 PlanToDraftMapper 转成草稿动作（目标/实际分离）；
     * 4. 都没有 → 空白训练，不阻止用户开练。
     *
     * 编辑历史 / 补记是独立入口（editSession / startBackfill），不经过本方法，
     * 因此不会错误套用今日计划。
     */
    suspend fun startNew() {
        // 1. 已有草稿：恢复，什么都不做
        if (_draft.value != null) {
            _initState.value = TrainingInitState.Restored
            return
        }
        val t = System.currentTimeMillis()
        val base = WorkoutDraft(
            sessionUuid = UUID.randomUUID().toString(),
            startedAt = t,
            pausedAt = 0L,
            pausedMs = 0L
        )
        // 2. 指定动作来源：留空白草稿，动作由 Screen consumePendingExercise 后加入
        if (pendingExercise != null) {
            persist(base)
            _initState.value = TrainingInitState.Blank
            return
        }
        // 3. 尝试读取当天计划（已启用 + 非休息日 + 按 orderInDay）
        _initState.value = TrainingInitState.Loading
        val planResult = runCatching { repo.todayPlanExercises() }
        val planItems = planResult.getOrElse {
            say("读取当天训练计划失败，已按空白训练开始")
            emptyList()
        }
        if (planResult.isFailure) {
            persist(base)
            _initState.value = TrainingInitState.Failed
            return
        }
        if (planItems.isEmpty()) {
            // 4. 没有计划：空白训练
            persist(base)
            _initState.value = TrainingInitState.Blank
            return
        }
        val mapped = planItems.map { PlanToDraftMapper.map(it) }
        // QJ 来源信息：记下计划名快照，保存时写进 WorkoutSession.source
        val planName = runCatching { repo.observeActivePlan().first()?.name.orEmpty() }.getOrDefault("")
        persist(
            base.copy(
                sourcePlan = planName,
                exercises = mapped.map { m ->
                    DraftExercise(
                        key = UUID.randomUUID().toString(),
                        exerciseUuid = m.exerciseUuid,
                        name = m.name,
                        sets = m.sets,
                        // 统一体系：有氧/HIIT 动作带 recordType + 目标配置，保存时分流
                        recordType = m.recordType.key,
                        targetConfigJson = m.cardioConfig?.let {
                            com.qingji.app.domain.TargetConfigJson.encode(it)
                        }.orEmpty()
                    )
                }
            )
        )
        _initState.value = TrainingInitState.PlanLoaded
    }

    /** QJ 初始化失败重试：丢掉刚才兜底的空白草稿，重新走一次启动流程。 */
    suspend fun retryInit() {
        discard()
        _initState.value = TrainingInitState.Idle
        startNew()
    }

    /**
     * 2026-11-18：补记一次力量训练（从「训练记录」页进来）。
     * 计时器对过去没意义，所以补记稿直接带指定时长（默认 60 分钟，可改）
     * 和补记日期（默认今天，可在进行中页点日期改过去任何一天）。
     */
    fun startBackfill() {
        if (_draft.value != null) return
        val t = System.currentTimeMillis()
        persist(
            WorkoutDraft(
                sessionUuid = UUID.randomUUID().toString(),
                startedAt = t,
                pausedAt = t,               // 补记不跑计时
                pausedMs = 0L,
                durationOverrideMin = 60,
                backfillDate = AppClock.today().toString()
            )
        )
    }

    /** 手动指定训练时长（分钟）；0 = 恢复按计时算。 */
    fun setDurationOverride(min: Int) {
        val d = _draft.value ?: return
        persist(d.copy(durationOverrideMin = min.coerceIn(0, 600)))
    }

    /** 补记模式下改这场训练算哪一天的。 */
    fun setBackfillDate(date: LocalDate) {
        val d = _draft.value ?: return
        persist(d.copy(backfillDate = date.toString()))
    }

    /** 当天已保存的会话可以继续改明细；时间与时长保留原值。 */
    fun editSession(uuid: String, onLoaded: () -> Unit = {}) {
        viewModelScope.launch {
            val (session, items) = repo.sessionDetail(uuid)
            if (session == null) {
                say("这次训练已经不在了")
                return@launch
            }
            val t = System.currentTimeMillis()
            val draftExercises = items.map { item ->
                DraftExercise(
                    key = UUID.randomUUID().toString(),
                    exerciseUuid = item.exerciseUuid,
                    name = item.exerciseNameSnapshot,
                    sets = TrainingMath.decodeSets(item.setsJson),
                    note = item.note
                )
            }
            persist(
                WorkoutDraft(
                    sessionUuid = session.uuid,
                    startedAt = t,
                    pausedAt = t,                 // 编辑模式不跑计时
                    note = session.note,
                    exercises = draftExercises,
                    editingUuid = session.uuid,
                    originalDate = session.date.toString(),
                    fixedStartedAt = session.startedAt,
                    fixedEndedAt = session.endedAt,
                    fixedDurationMinutes = session.durationMinutes,
                    fixedCreatedAt = session.createdAt,
                    // QJ：编辑历史时保留原来源，保存后不丢 plan:<计划名> 标记
                    sourcePlan = session.source.removePrefix("plan:")
                        .takeIf { session.source.startsWith("plan:") } ?: ""
                )
            )
            onLoaded()
        }
    }

    /** 从动作库选一个动作：自动带出上次成绩，没有历史就给一组空的。 */
    fun addExercise(item: ExerciseEntity) {
        val d = _draft.value ?: return
        if (d.exercises.any { it.name == item.name }) {
            say("「${item.name}」已经在这次训练里了")
            return
        }
        viewModelScope.launch {
            val last = repo.lastSets(item.uuid, item.name)
            val sets = if (last.isNotEmpty()) last else listOf(WorkoutSet())
            persist(
                d.copy(
                    exercises = d.exercises + DraftExercise(
                        key = UUID.randomUUID().toString(),
                        exerciseUuid = item.uuid,
                        name = item.name,
                        sets = sets,
                        fromLast = last.isNotEmpty()
                    )
                )
            )
        }
    }

    /** P0 允许用户自建动作（名字唯一即可），补上动作库没收录的情况。 */
    fun addCustomExercise(name: String, category: String) {
        viewModelScope.launch {
            val created = repo.addCustomExercise(name, category)
            if (created == null) say("动作名不能为空") else addExercise(created)
        }
    }

    fun updateSet(key: String, index: Int, set: WorkoutSet) {
        val d = _draft.value ?: return
        persist(d.copy(exercises = d.exercises.map { e ->
            if (e.key != key) e
            else e.copy(sets = e.sets.mapIndexed { i, s -> if (i == index) set else s })
        }))
    }

    /**
     * v1.4 健康体系：标记 / 取消某组完成。
     * 勾选完成时，如果这场训练还有下一组要练，就按默认时长自动开始组间休息；
     * 取消勾选只改标记，不动正在走的计时。
     */
    fun toggleSetComplete(key: String, index: Int) {
        val d = _draft.value ?: return
        val st = _restTimer.value
        var wasLast = true
        persist(d.copy(exercises = d.exercises.map { e ->
            if (e.key != key) e
            else e.copy(sets = e.sets.mapIndexed { i, s ->
                if (i == index) {
                    // 同动作内后面还有没完成的组，就不算最后一组
                    wasLast = e.sets.drop(i + 1).none { !it.completed }
                    s.copy(completed = !s.completed)
                } else s
            })
        }))
        val justCompleted = d.exercises
            .firstOrNull { it.key == key }?.sets?.getOrNull(index)?.completed != true
        if (justCompleted && st.totalMs <= 0L) {
            startRest()
        }
    }

    /** 加一组：默认复制上一组的重量/次数，连续记录时少敲几下。 */
    fun addSet(key: String) {
        val d = _draft.value ?: return
        persist(d.copy(exercises = d.exercises.map { e ->
            if (e.key != key) e else e.copy(sets = e.sets + (e.sets.lastOrNull() ?: WorkoutSet()))
        }))
    }

    fun removeSet(key: String, index: Int) {
        val d = _draft.value ?: return
        persist(d.copy(exercises = d.exercises.map { e ->
            if (e.key != key) e else e.copy(sets = e.sets.filterIndexed { i, _ -> i != index })
        }))
    }

    fun removeExercise(key: String) {
        val d = _draft.value ?: return
        persist(d.copy(exercises = d.exercises.filter { it.key != key }))
    }

    fun setNote(text: String) {
        val d = _draft.value ?: return
        persist(d.copy(note = text))
    }

    /**
     * 训练日记：给历史会话补/改标题速记，只更新 workout_session.note 一列。
     * 刻意不走 editSession→save，避免重算消耗与账本、覆盖动作/时长/来源。
     */
    fun updateSessionNote(uuid: String, note: String) {
        viewModelScope.launch {
            runCatching { repo.updateSessionNote(uuid, note) }
                .onSuccess { say("备注已保存") }
                .onFailure { say("备注保存失败，请重试") }
        }
    }

    /** 暂停 / 继续：把暂停时长累加起来，恢复后计时从冻结处接着走。 */
    fun togglePause() {
        val d = _draft.value ?: return
        if (d.editingUuid.isNotEmpty()) return
        val t = System.currentTimeMillis()
        val next = if (d.pausedAt > 0L) {
            d.copy(pausedMs = d.pausedMs + (t - d.pausedAt), pausedAt = 0L)
        } else {
            d.copy(pausedAt = t)
        }
        persist(next)
    }

    fun discard() {
        draftStore.clear()
        _draft.value = null
    }

    // ---------- P1：从「动作详情 → 用这个动作开始训练」带一个动作过来 ----------
    // 动作详情页在另一个路由，不能等页面建好再传参数，所以先寄存在 ViewModel 里，
    // 进行中训练页第一帧取走。ViewModel 挂在 Activity 作用域，跨页面不会丢。
    private var pendingExercise: ExerciseEntity? = null
    fun queueExercise(item: ExerciseEntity) { pendingExercise = item }
    fun consumePendingExercise(): ExerciseEntity? {
        val item = pendingExercise
        pendingExercise = null
        return item
    }

    /** 保存：整包写库，成功后草稿作废。返回 false 表示没达到「至少 1 组」的门槛。 */
    fun save(onDone: (Boolean) -> Unit) {
        val d = _draft.value ?: run { onDone(false); return }
        // 统一体系 V2.1：力量项按"至少一组有效"；有氧/HIIT 项按 recordType 分流，
        // 它们没有 sets，不能沿用力量门槛（这是之前有氧计划保存不进去的根因之一）。
        val cardioExercises = d.exercises.filter { it.recordType.isNotBlank() }
        val cleaned = d.exercises.filter { it.recordType.isBlank() }.map { e ->
            e.copy(sets = e.sets.filter { it.reps > 0 || it.weightKg > 0.0 })
        }.filter { it.sets.isNotEmpty() }
        if (cleaned.isEmpty() && cardioExercises.isEmpty()) {
            say("至少要记一组或一个有氧项目才能保存")
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

        val items = cleaned.mapIndexed { i, e ->
            WorkoutExercise(
                sessionUuid = d.sessionUuid,
                exerciseUuid = e.exerciseUuid,
                exerciseNameSnapshot = e.name,
                orderInSession = i,
                setsJson = TrainingMath.encodeSets(e.sets),
                note = e.note
            )
        }
        // 有氧/HIIT 项目：目标时长/距离作为实际值预填（无目标时留 0，由结算判不可算）。
        val cardioDetails = cardioExercises.mapIndexed { i, e ->
            val cfg = com.qingji.app.domain.TargetConfigJson.decode(e.targetConfigJson)
            com.qingji.app.data.local.entity.WorkoutCardioDetail(
                exerciseUuid = e.exerciseUuid,
                exerciseNameSnapshot = e.name,
                orderInSession = cleaned.size + i,
                durationSeconds = cfg?.durationSec ?: 0L,
                distanceMeters = cfg?.distanceMeters,
                rawInputJson = cfg?.intensity?.let { intensity ->
                    org.json.JSONObject().put("intensity", intensity).toString()
                }.orEmpty(),
                createdAt = System.currentTimeMillis()
            )
        }

        viewModelScope.launch {
            val weight = settleWeight.value
            // 统一入口：有氧明细与力量明细同场落库，整场只写一条账本，不双结算。
            val result = repo.saveUnifiedSession(session, items, cardioDetails, weight)
            draftStore.clear()
            _draft.value = null
            val kcalText = if (result.totalKcal > 0.0)
                "，预计消耗约 ${com.qingji.app.domain.SessionCalorieCalculator.displayKcal(result.totalKcal)} 千卡（估算）"
            else ""
            say(
                when {
                    result.hasSkipped -> "训练已保存${kcalText}；${result.notComputableItems} 项有氧缺少时长/距离未计入消耗"
                    !editing -> "训练已保存${kcalText}"
                    else -> "已更新这次训练，消耗已重新结算${kcalText}"
                }
            )
            onDone(true)
        }
    }

    fun deleteSession(uuid: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.deleteSession(uuid)
            say("已删除这次训练")
            onDone()
        }
    }

    /** 每次改动都同步落盘，进程被杀也能恢复。 */
    private fun persist(d: WorkoutDraft) {
        _draft.value = d
        draftStore.save(d)
    }
}
