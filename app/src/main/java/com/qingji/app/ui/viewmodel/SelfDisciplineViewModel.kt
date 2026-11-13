package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import com.qingji.app.data.repository.SelfDisciplineRepository
import com.qingji.app.data.repository.SelfDisciplineStats
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.domain.SelfDisciplineEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 自律管理模块统一 ViewModel（SD-002 ~ SD-008 / SD-012）
 * ------------------------------------------------------------
 * 一个模块共用一个 VM，直接持有 SelfDisciplineRepository（构造拿 Application，
 * 和训练中心一致，不引 Hilt）。复杂业务都下沉到 Repository / Engine，VM 只做
 * 状态转发和简单的界面态（当前主要目标、统计、轻提示）。
 * ============================================================
 */
class SelfDisciplineViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SelfDisciplineRepository(app)

    /** 全部目标（目标管理页用）。 */
    val goals: Flow<List<SelfDisciplineGoalEntity>> = repo.observeGoals()

    private val _primary = MutableStateFlow<SelfDisciplineGoalEntity?>(null)
    val primaryGoal: StateFlow<SelfDisciplineGoalEntity?> = _primary

    private val _stats = MutableStateFlow(
        SelfDisciplineStats(false, 0, 0, 0, 0, 0, 0, 0)
    )
    val stats: StateFlow<SelfDisciplineStats> = _stats

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refreshPrimary()
    }

    /** 重新算主要目标 + 它的统计。 */
    fun refreshPrimary() {
        viewModelScope.launch {
            val goal = repo.getPrimaryGoal()
            _primary.value = goal
            _stats.value = if (goal != null) repo.computeStats(goal.id) else SelfDisciplineStats(
                false, 0, 0, 0, 0, 0, 0, 0
            )
        }
    }

    // ---------------- 目标管理（SD-003） ----------------

    /** 新建目标：title 必填，startDate 默认今天。 */
    suspend fun createGoal(
        title: String,
        goalType: String,
        startDate: LocalDate = LocalDate.now(),
        targetDays: Int = 0,
        reminderEnabled: Boolean = false,
        reminderMinute: Int = -1,
        note: String = ""
    ): Long {
        val id = repo.createGoal(
            SelfDisciplineGoalEntity(
                title = title.trim(),
                goalType = goalType,
                startDate = startDate,
                targetDays = targetDays,
                reminderEnabled = reminderEnabled,
                reminderMinute = reminderMinute,
                note = note
            )
        )
        // 第一次建目标就把它设成主要目标
        if (repo.getSetting(SelfDisciplineContracts.SETTING_DEFAULT_GOAL).isNullOrEmpty()) {
            repo.setSetting(SelfDisciplineContracts.SETTING_DEFAULT_GOAL, id.toString())
        }
        refreshPrimary()
        return id
    }

    suspend fun updateGoal(goal: SelfDisciplineGoalEntity) {
        repo.updateGoal(goal)
        refreshPrimary()
    }

    suspend fun setGoalEnabled(id: Long, enabled: Boolean) {
        repo.setGoalEnabled(id, enabled)
        refreshPrimary()
    }

    suspend fun deleteGoal(id: Long) {
        repo.deleteGoal(id)
        refreshPrimary()
    }

    /** 读单个目标（编辑页回填用）。 */
    suspend fun getGoal(id: Long) = repo.getGoal(id)

    /** 把某个目标设为首页突出显示的主要目标。 */
    suspend fun setPrimaryGoal(id: Long) {
        repo.setSetting(SelfDisciplineContracts.SETTING_DEFAULT_GOAL, id.toString())
        refreshPrimary()
    }

    // ---------------- 打卡 / 冲动 / 偏离（SD-005/006/007） ----------------

    /** 保存（同天覆盖）一条打卡。 */
    suspend fun saveCheckin(
        goalId: Long,
        date: LocalDate,
        status: String,
        mood: String = "",
        urgeLevel: Int = 0,
        triggers: List<String> = emptyList(),
        copingMethods: List<String> = emptyList(),
        note: String = ""
    ) {
        val existing = repo.getCheckin(goalId, date)
        repo.saveCheckin(
            SelfDisciplineCheckinEntity(
                id = existing?.id ?: 0,
                goalId = goalId,
                localDate = date,
                status = status,
                mood = mood,
                urgeLevel = urgeLevel,
                triggers = SelfDisciplineContracts.joinCsv(triggers),
                copingMethods = SelfDisciplineContracts.joinCsv(copingMethods),
                note = note,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        refreshPrimary()
    }

    suspend fun getCheckin(goalId: Long, date: LocalDate) = repo.getCheckin(goalId, date)

    suspend fun deleteCheckin(id: Long) {
        repo.deleteCheckin(id)
        refreshPrimary()
    }

    fun observeCheckins(goalId: Long): Flow<List<SelfDisciplineCheckinEntity>> = repo.observeCheckins(goalId)
    fun observeCheckinRange(goalId: Long, from: LocalDate, to: LocalDate) =
        repo.observeCheckinRange(goalId, from, to)

    fun observeUrges(goalId: Long): Flow<List<SelfDisciplineUrgeEntity>> = repo.observeUrges(goalId)
    fun observePeriods(goalId: Long) = repo.observePeriods(goalId)

    suspend fun addUrge(
        goalId: Long,
        occurredAt: Long = System.currentTimeMillis(),
        intensity: Int,
        trigger: String = "",
        copingMethod: String = "",
        intensityAfter: Int = 0,
        resolved: Boolean = false,
        note: String = ""
    ) {
        repo.addUrge(
            SelfDisciplineUrgeEntity(
                goalId = goalId,
                occurredAt = occurredAt,
                intensity = intensity,
                trigger = trigger,
                copingMethod = copingMethod,
                intensityAfter = intensityAfter,
                resolved = resolved,
                note = note
            )
        )
        refreshPrimary()
    }

    suspend fun deleteUrge(id: Long) {
        repo.deleteUrge(id)
        refreshPrimary()
    }

    /** 记录目标偏离，返回被结束的周期 id（用于撤销）。 */
    suspend fun recordDeviation(
        goalId: Long,
        date: LocalDate,
        triggers: List<String> = emptyList(),
        mood: String = "",
        note: String = ""
    ): Long {
        val pid = repo.recordDeviation(
            goalId, date,
            SelfDisciplineContracts.joinCsv(triggers), mood, note
        )
        refreshPrimary()
        return pid
    }

    suspend fun undoDeviation(periodId: Long, deviationDate: LocalDate, goalId: Long) {
        repo.undoDeviation(periodId, deviationDate, goalId)
        refreshPrimary()
    }

    /** 重新开始一个周期（从 today 起）。 */
    suspend fun restartPeriod(goalId: Long) {
        repo.restartPeriod(goalId, LocalDate.now())
        refreshPrimary()
    }

    // ---------------- 设置（SD-012，P0 轻量） ----------------

    suspend fun getSetting(key: String): String? = repo.getSetting(key)
    suspend fun setSetting(key: String, value: String) = repo.setSetting(key, value)

    fun consumeMessage() { _message.value = null }
    fun toast(msg: String) { _message.value = msg }
}
