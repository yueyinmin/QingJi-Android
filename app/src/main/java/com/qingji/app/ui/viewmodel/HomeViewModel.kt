package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    /** 当前看的是哪天。点日期条会改它，下面所有数据自动跟着换。 */
    private val _date = MutableStateFlow(AppClock.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /**
     * flatMapLatest 的意思：日期一变，就把旧的数据库订阅取消、换成新日期的。
     * combine 把「档案 / 当天汇总 / 当天明细 / 当天饮水」四股数据合成一包。
     */
    val uiState: StateFlow<HomeUiState> = _date.flatMapLatest { d ->
        combine(
            repo.observeProfile(),
            repo.observeDailySummary(d),
            repo.observeFoodLogs(d),
            repo.observeWaterMl(d),
            repo.observeExerciseLogs(d)
        ) { profile, summary, logs, water, exercises ->
            HomeUiState(
                date = d,
                profile = profile,
                totalKcal = summary?.totalKcal ?: 0.0,
                totalProtein = summary?.totalProtein ?: 0.0,
                totalCarb = summary?.totalCarb ?: 0.0,
                totalFat = summary?.totalFat ?: 0.0,
                waterMl = water ?: 0,
                // 当天运动消耗：计入「预计热量缺口」（动得多缺口大）
                exerciseKcal = exercises.sumOf { it.kcal },
                logs = logs
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * 近 7 天汇总：首页「本周小结」卡片用（v3.4.0 · R2）。
     * 五股原始 Flow 在仓库层取区间，聚合放到纯逻辑类里算，界面只拿结论。
     * 全部本地计算，不调 AI、不联网。
     */
    private val weekFrom: java.time.LocalDate = AppClock.today().minusDays(6)
    private val weekTo: java.time.LocalDate = AppClock.today()
    val weeklySummary: StateFlow<com.qingji.app.domain.WeeklySummaryCalculator.Summary> = combine(
        repo.observeFoodRange(weekFrom, weekTo),
        repo.observeWaterRange(weekFrom, weekTo),
        repo.observeExerciseRange(weekFrom, weekTo),
        repo.observeWeightLogs(),
        repo.observeProfile()
    ) { foods, waters, exercises, weights, profile ->
        val list = (0..6).map { i ->
            val d = weekFrom.plusDays(i.toLong())
            com.qingji.app.domain.WeeklySummaryCalculator.Day(
                date = d,
                kcal = foods.filter { it.date == d }.sumOf { it.kcal },
                exerciseKcal = exercises.filter { it.date == d }.sumOf { it.kcal },
                waterMl = waters.filter { it.date == d }.sumOf { it.ml },
                hasLog = foods.any { it.date == d }
            )
        }
        val goal = profile?.dailyKcalGoal?.takeIf { it > 0 } ?: 2000
        val waterGoal = profile?.waterGoalMl?.takeIf { it > 0 } ?: 2000
        val inWeek = weights.filter { it.date >= weekFrom }
        com.qingji.app.domain.WeeklySummaryCalculator.summarize(
            days = list,
            dailyGoal = goal,
            waterGoal = waterGoal,
            startWeight = inWeek.minByOrNull { it.date }?.weightKg,
            endWeight = inWeek.maxByOrNull { it.date }?.weightKg
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.qingji.app.domain.WeeklySummaryCalculator.summarize(emptyList(), 2000, 2000, null, null)
    )

    fun selectDate(d: LocalDate) {
        _date.value = d
    }

    fun backToToday() {
        _date.value = AppClock.today()
    }

    fun addWater(ml: Int) {
        viewModelScope.launch { repo.addWater(ml, _date.value) }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch { repo.deleteFoodLog(id) }
    }

    /** 首页点水杯 → 首页设置里能直接调气泡量。 */
    fun setWaterBubbles(level: Int) {
        viewModelScope.launch {
            com.qingji.app.data.prefs.SettingsStore.set(
                getApplication(),
                com.qingji.app.data.prefs.SettingsKeys.WATER_BUBBLES,
                level.coerceIn(0, 3)
            )
        }
    }

    /** 首页每餐卡片是否显示营养素比例条。 */
    fun setShowMealMacro(show: Boolean) {
        viewModelScope.launch {
            com.qingji.app.data.prefs.SettingsStore.set(
                getApplication(),
                com.qingji.app.data.prefs.SettingsKeys.SHOW_MEAL_MACRO,
                show
            )
        }
    }
}
