package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.ai.memory.MemoryManager
import com.qingji.app.data.ai.MemoryStore
import com.qingji.app.data.ai.RoomMemoryStore
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.prefs.AiMemoryPrefs
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemoryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 长期记忆管理页（阶段 I）。
 *
 * 只做「看、筛、改、失效、删、关类别」六件事，
 * 不提供任何写入新记忆的入口——新记忆只能来自用户在对话里明确说「记住…」。
 */
class MemoryViewModel private constructor(
    app: Application,
    private val store: MemoryStore,
    private val now: () -> Long
) : AndroidViewModel(app) {

    data class UiState(
        val memories: List<MemoryRecord> = emptyList(),
        val filter: MemoryCategory? = null,
        val disabled: Set<MemoryCategory> = emptySet(),
        val message: String? = null
    ) {
        val visible: List<MemoryRecord>
            get() = if (filter == null) memories else memories.filter { it.category == filter }

        val activeCount: Int get() = memories.count { it.status == MemoryStatus.ACTIVE }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    constructor(app: Application) : this(
        app,
        RoomMemoryStore(AiDatabase.get(app)),
        { System.currentTimeMillis() }
    )

    /** 测试注入点。 */
    constructor(app: Application, testStore: MemoryStore) : this(
        app,
        testStore,
        { System.currentTimeMillis() }
    )

    init {
        viewModelScope.launch {
            store.observeAll().collect { rows ->
                _uiState.update { it.copy(memories = rows) }
            }
        }
        viewModelScope.launch {
            AiMemoryPrefs.observeDisabled(getApplication()).collect { disabled ->
                _uiState.update { it.copy(disabled = disabled) }
            }
        }
        // 打开页面顺手清一次过期记忆；失败不影响浏览
        viewModelScope.launch { runCatching { MemoryManager.expire(store, now()) } }
    }

    fun setFilter(category: MemoryCategory?) {
        _uiState.update { it.copy(filter = category) }
    }

    /** 「不再记住这类内容」开关：只影响以后的新记忆，已经记下的不动。 */
    fun setCategoryEnabled(category: MemoryCategory, enabled: Boolean) {
        viewModelScope.launch {
            AiMemoryPrefs.setEnabled(getApplication(), category, enabled)
            _uiState.update {
                it.copy(message = if (enabled) "以后会记住${category.label}类内容" else "以后不再自动记住${category.label}类内容")
            }
        }
    }

    /** 用户确认 AI 推断：只有 INFERRED 能升级，其它情况提示无法确认。 */
    fun confirm(record: MemoryRecord) {
        viewModelScope.launch {
            val ok = runCatching { MemoryManager.confirm(store, record, now()) }.getOrDefault(false)
            _uiState.update {
                it.copy(message = if (ok) "已确认为事实" else "这条不是 AI 推断，无法确认")
            }
        }
    }

    fun correct(record: MemoryRecord, newContent: String) {
        viewModelScope.launch {
            val ok = runCatching { MemoryManager.correct(store, record, newContent, now()) }.getOrDefault(false)
            _uiState.update {
                it.copy(
                    message = when {
                        ok -> "已改好"
                        newContent.trim().length < MemoryManager.MIN_CONTENT_CHARS -> "内容太短，至少两个字"
                        else -> "已经有同样内容的记忆了"
                    }
                )
            }
        }
    }

    fun invalidate(record: MemoryRecord) {
        viewModelScope.launch {
            runCatching { MemoryManager.invalidate(store, record, now()) }
            _uiState.update { it.copy(message = "已标记失效，不会再进入回答") }
        }
    }

    fun delete(record: MemoryRecord) {
        viewModelScope.launch {
            runCatching { MemoryManager.delete(store, record) }
            _uiState.update { it.copy(message = "已删除这条记忆，饮食/体重/训练记录不受影响") }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
