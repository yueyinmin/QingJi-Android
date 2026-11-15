package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.DiaryRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 日记列表（M5）：排序切换 + 删除，数据一变界面自动刷新。 */
class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private val _sortMode = MutableStateFlow(DiaryRules.SortMode.DATE)
    val sortMode: StateFlow<DiaryRules.SortMode> = _sortMode.asStateFlow()

    val diaries: StateFlow<List<Diary>> = combine(
        repo.observeDiaries(),
        _sortMode
    ) { list, mode ->
        DiaryRules.sort(list, mode, { it.date }, { it.updatedAt }, { it.rating })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 快捷按钮「水」：日记页也能直接记一杯。 */
    fun addWater(ml: Int) {
        viewModelScope.launch { repo.addWater(ml, AppClock.today()) }
    }

    fun setSort(mode: DiaryRules.SortMode) {
        _sortMode.value = mode
    }

    /** 清空全部日记 */
    fun clearAllDiaries() {
        viewModelScope.launch { repo.clearAllDiaries() }
    }

    suspend fun delete(id: Long) = repo.deleteDiary(id)
}
