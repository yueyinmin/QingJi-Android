package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.TrainingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ============================================================
 * 运动模块专用 ViewModel（v3.3.0）
 * ------------------------------------------------------------
 * 「记录运动」和「确认记录」两级页面共用它：
 * 运动库、收藏、最近使用、当前体重、新建自定义运动、保存记录。
 * 之前运动逻辑散在 ProgressScreen 的私有弹窗里，现在抽出来给独立页面用。
 * ============================================================
 */
class ExerciseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = QingJiRepository(app)
    /** 2026-11-18：「记一次 → 选择训练」用，训练场次列表 + 调整账本消耗 */
    private val trainingRepo = TrainingRepository(app)
    private val healthRepo = HealthRepository(app)

    /** 运动库（预置 30 条 + 用户新建的，DAO 已按收藏置顶、名称排序）。 */
    val presets: StateFlow<List<com.qingji.app.data.local.entity.ExercisePreset>> =
        repo.observeExercisePresets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 最近用过的运动名（按写入时间倒序、去重），给「最近」页签排序用。 */
    val recentNames: StateFlow<List<String>> = repo.observeRecentExercises()
        .map { rows -> rows.map { it.type }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 换算消耗用当前体重；没填档案时沿用旧运动弹窗的 70kg 兜底。 */
    val weightKg: StateFlow<Double> = repo.observeProfile()
        .map { profile -> profile?.startWeight?.takeIf { it > 0 } ?: 70.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 70.0)

    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setExerciseFavorite(id, favorite) }
    }

    /** 右上角「新建」：名称去重交给界面校验，这里只管落库。 */
    fun addCustomExercise(name: String, met: Double, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.addCustomExercise(name, met)
            onDone(id)
        }
    }

    /**
     * 保存防重（工单 §8）：同一表单连续点击只提交一次。
     * 用 AtomicBoolean 在 launch 之前就置位——协程调度有窗口，光靠 StateFlow 或
     * 按钮禁用挡不住「两次点击都进了 launch」的竞态。onDone 导航后释放，
     * 失败也释放（runCatching），保证一次失败后仍可重试，不做全局时间窗口拦截。
     */
    private val saveGuard = AtomicBoolean(false)
    private val _isSaving = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    /** 保存一条运动记录；repo 里会顺手把「使用情况」的体型计数 +1。 */
    fun save(log: ExerciseLog, onDone: () -> Unit = {}) {
        if (!saveGuard.compareAndSet(false, true)) return
        _isSaving.value = true
        viewModelScope.launch {
            runCatching { repo.addExercise(log) }
                .onSuccess { onDone() }
                .onFailure { saveGuard.set(false); _isSaving.value = false }
        }
    }

    // ---------- v1.3.7：有氧记录「改」（工单 4.4 增查改删） ----------
    /** 编辑前先把那条记录读出来，确认页拿它回填当时的输入方式和原始数字。 */
    suspend fun findForEdit(id: Long): ExerciseLog? = repo.findExercise(id)

    /** 修改一条（保留原 id），账本镜像在 repo 里先删后重插。防重策略同 save。 */
    fun update(log: ExerciseLog, onDone: () -> Unit = {}) {
        if (!saveGuard.compareAndSet(false, true)) return
        _isSaving.value = true
        viewModelScope.launch {
            runCatching { repo.updateExercise(log) }
                .onSuccess { onDone() }
                .onFailure { saveGuard.set(false); _isSaving.value = false }
        }
    }

    // ---------- 2026-11-18：「记一次 → 选择训练」 ----------
    /** 训练场次（近的在前），给选择页列出用户真正做过的训练。 */
    val sessions: StateFlow<List<com.qingji.app.data.local.entity.WorkoutSession>> =
        trainingRepo.observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 账本行（查某场训练当前已计入的消耗），UI 按 sourceId 匹配。 */
    val ledger: StateFlow<List<com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity>> =
        healthRepo.observeAllLedger()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 取某场训练当前已计入的消耗（选弹层回填当前值）。 */
    suspend fun trainingCalorie(sessionUuid: String): Double? =
        healthRepo.trainingCalorie(sessionUuid)

    /**
     * 调整某场训练的消耗：upsert 覆盖原账本行，不新建运动记录、不双计。
     * @return 是否成功（false = 那场训练没有账本行，UI 如实提示）
     */
    fun adjustTrainingCalorie(sessionUuid: String, kcal: Double, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = healthRepo.adjustTrainingCalorie(sessionUuid, kcal)
            onDone(ok)
        }
    }
}
