package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.NutritionCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 我的页。把 usage_stat 的流水按类型累加成八个数字。 */
class MineViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val totals: StateFlow<Map<String, Int>> = repo.observeUsageAll()
        .map { rows -> rows.groupBy { it.type }.mapValues { (_, list) -> list.sumOf { it.count } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 本月 AI 调用次数（M6 的「调用统计卡」）。
     * 不用单独查库：usage_stat 的明细本来就在订阅里，按月过滤求和即可。
     */
    val monthAi: StateFlow<Int> = repo.observeUsageAll()
        .map { rows ->
            val today = com.qingji.app.domain.AppClock.today()
            val from = today.withDayOfMonth(1)
            rows.filter { it.type == "ai" && !it.date.isBefore(from) && !it.date.isAfter(today) }
                .sumOf { it.count }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 成就清单。usage_stat 一变就重算（记一笔饮食、写完日记都会触发）。 */
    val achievements: StateFlow<List<com.qingji.app.domain.Achievement>> =
        repo.observeUsageAll()
            .map { com.qingji.app.domain.Achievements.list(repo.achievementStats()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 「我的目标」改完重算热量预算。
     * 用的是最近一次体重（没有就用起始体重），并沿用个人资料里的活动量。
     */
    suspend fun saveGoal(targetWeightKg: Double, activityLevel: Int, mode: String) {
        val current = repo.observeProfile().first() ?: return
        val weight = repo.latestWeight()?.weightKg
            ?: current.startWeight.takeIf { it > 0 }
            ?: 70.0
        val age = current.birthday?.let {
            java.time.Period.between(it, com.qingji.app.domain.AppClock.today()).years
        } ?: 25
        val goal = NutritionCalculator.compute(
            gender = current.gender,
            age = age,
            heightCm = current.heightCm,
            weightKg = weight,
            activityLevel = activityLevel,
            mode = mode
        )
        repo.saveProfile(
            current.copy(
                targetWeight = targetWeightKg,
                activityLevel = activityLevel,
                dailyKcalGoal = goal.kcal,
                proteinGoalG = goal.proteinG,
                carbGoalG = goal.carbG,
                fatGoalG = goal.fatG
            )
        )

        // 和 ProfileViewModel.save 一样：目标模式要同步回 DataStore，
        // 否则首页顶部「减肥/增肌/保持」的标签还停在上一个模式。
        SettingsStore.set(getApplication(), SettingsKeys.GOAL_MODE, mode)
    }
}
