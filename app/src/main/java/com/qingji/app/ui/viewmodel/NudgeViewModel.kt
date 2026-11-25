package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.ai.nudge.NudgeManager
import com.qingji.app.ai.nudge.NudgePolicySource
import com.qingji.app.ai.nudge.RepositoryNudgeFactsSource
import com.qingji.app.data.ai.RoomNudgeStore
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.prefs.NudgePrefs
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.nudge.NudgeKind
import com.qingji.app.domain.nudge.NudgePolicy
import com.qingji.app.domain.nudge.NudgeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「管家提醒」设置与历史页（阶段 J）。
 *
 * 页面只做三件事：开/关、调节打扰程度、回看发过什么。
 * 真正的触发判断在 [com.qingji.app.domain.nudge.NudgeRuleEngine]，
 * 这里不参与任何规则，避免开关逻辑和判定逻辑两处各写一套。
 */
class NudgeViewModel private constructor(
    app: Application,
    private val manager: NudgeManager,
    marker: Unit = Unit
) : AndroidViewModel(app) {

    data class UiState(
        val policy: NudgePolicy = NudgePolicy(),
        val history: List<NudgeRecord> = emptyList(),
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    constructor(app: Application) : this(
        app,
        NudgeManager(
            store = RoomNudgeStore(AiDatabase.get(app)),
            factsSource = RepositoryNudgeFactsSource(
                repo = QingJiRepository(app),
                training = TrainingRepository(app)
            ),
            policySource = NudgePolicySource { NudgePrefs.policy(app) }
        ),
        Unit
    )

    /** 测试注入点。 */
    constructor(app: Application, testManager: NudgeManager) : this(app, testManager, Unit)

    init {
        viewModelScope.launch {
            NudgePrefs.observe(getApplication()).collect { policy ->
                _uiState.update { it.copy(policy = policy) }
            }
        }
        viewModelScope.launch {
            manager.observeHistory(HISTORY_LIMIT).collect { rows ->
                _uiState.update { it.copy(history = rows) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            NudgePrefs.setEnabled(getApplication(), enabled)
            _uiState.update {
                it.copy(message = if (enabled) "已开启管家提醒" else "已关闭，之后不会再出现提醒卡片")
            }
        }
    }

    fun setKindEnabled(kind: NudgeKind, enabled: Boolean) {
        viewModelScope.launch {
            NudgePrefs.setKindEnabled(getApplication(), kind, enabled)
        }
    }

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch { NudgePrefs.setDailyLimit(getApplication(), limit) }
    }

    /** 静默时段三档；start == end 视为关闭静默。 */
    fun setSilentWindow(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            NudgePrefs.setSilentWindow(getApplication(), startMinute, endMinute)
            _uiState.update { it.copy(message = "静默时段已更新") }
        }
    }

    fun deleteRecord(record: NudgeRecord) {
        viewModelScope.launch {
            runCatching { manager.delete(record.id) }
            _uiState.update { it.copy(message = "已删除这条记录") }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { manager.clearHistory() }
            _uiState.update { it.copy(message = "历史已清空，提醒设置不变") }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        const val HISTORY_LIMIT = 30
    }
}
