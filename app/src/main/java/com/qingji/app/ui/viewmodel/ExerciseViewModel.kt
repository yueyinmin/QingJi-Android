package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.repository.QingJiRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** 保存一条运动记录；repo 里会顺手把「使用情况」的体型计数 +1。 */
    fun save(log: ExerciseLog, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.addExercise(log)
            onDone()
        }
    }
}
