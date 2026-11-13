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
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutSet
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
class TrainingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TrainingRepository(app)
    val draftStore = repo.draftStore

    val exercises: StateFlow<List<ExerciseEntity>> = repo.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<WorkoutSession>> = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 所有训练明细，按 sessionUuid 分组后给历史列表算组数 / 训练量。 */
    val allWorkoutExercises: StateFlow<List<WorkoutExercise>> = repo.observeAllWorkoutExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    init {
        // 首次进入时灌最小内置动作库
        viewModelScope.launch { repo.ensureSeed() }
    }

    fun setUnit(unit: String) {
        draftStore.setUnit(unit)
        _unit.value = draftStore.unit()
    }

    /** 开一次新训练；已有草稿就原样恢复，绝不覆盖掉没保存的内容。 */
    fun startNew() {
        if (_draft.value != null) return
        val t = System.currentTimeMillis()
        persist(
            WorkoutDraft(
                sessionUuid = UUID.randomUUID().toString(),
                startedAt = t,
                pausedAt = 0L,
                pausedMs = 0L
            )
        )
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
                    fixedCreatedAt = session.createdAt
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
        // 每组至少要有次数或重量，才算一条有效记录
        val cleaned = d.exercises.map { e ->
            e.copy(sets = e.sets.filter { it.reps > 0 || it.weightKg > 0.0 })
        }.filter { it.sets.isNotEmpty() }
        if (cleaned.isEmpty()) {
            say("至少要记一组才能保存")
            onDone(false)
            return
        }

        val t = System.currentTimeMillis()
        val editing = d.editingUuid.isNotEmpty()
        val session = if (editing) {
            WorkoutSession(
                uuid = d.sessionUuid,
                date = runCatching { LocalDate.parse(d.originalDate) }.getOrDefault(AppClock.today()),
                startedAt = d.fixedStartedAt,
                endedAt = d.fixedEndedAt,
                durationMinutes = d.fixedDurationMinutes,
                note = d.note,
                source = "manual",
                createdAt = d.fixedCreatedAt
            )
        } else {
            val seconds = d.elapsedSeconds(t)
            WorkoutSession(
                uuid = d.sessionUuid,
                date = AppClock.today(),
                startedAt = d.startedAt,
                endedAt = t,
                durationMinutes = ((seconds + 59L) / 60L).toInt().coerceAtLeast(1),
                note = d.note,
                source = "manual",
                createdAt = t
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

        viewModelScope.launch {
            repo.saveSession(session, items)
            draftStore.clear()
            _draft.value = null
            say(if (editing) "已更新这次训练" else "训练已保存")
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
