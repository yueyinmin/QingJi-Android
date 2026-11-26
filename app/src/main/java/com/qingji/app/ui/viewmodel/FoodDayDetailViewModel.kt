package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 饮食日详情页的数据管家（QJ-RECORD-INPUT-UNIFIED-V3 / B5）。
 *
 * 只做三件事：绑定日期、按 date 查当天全部饮食记录、删除单条。
 * 分组与汇总交给 [com.qingji.app.domain.FoodDayGrouping]（纯函数、可单测），
 * 页面只负责把结果画出来 —— 与首页同一套分组/汇总口径。
 *
 * 与单餐详情 [MealDetailViewModel] 的关系：
 * 那个是「某天某一餐」，这个是「某天全天」；两者都走 observeFoodLogs(date)，
 * 同源数据，不存在第二套查询口径。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodDayDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private val _date = MutableStateFlow(AppClock.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val logs: StateFlow<List<FoodLog>> = _date
        .flatMapLatest { day -> repo.observeFoodLogs(day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 导航参数喂进来（AndroidViewModel 拿不到路由参数）。 */
    fun bind(day: LocalDate) {
        _date.value = day
    }

    fun setDate(day: LocalDate) {
        _date.value = day
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { repo.deleteFoodLog(id) }
    }
}
