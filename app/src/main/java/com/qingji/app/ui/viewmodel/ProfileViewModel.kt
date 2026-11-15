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
import kotlinx.coroutines.flow.stateIn

/** 个人资料页。保存时顺手按公式重算每日热量与营养目标。 */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun save(profile: UserProfile, mode: String) {
        val age = profile.birthday?.let {
            java.time.Period.between(it, AppClock.today()).years
        } ?: 25
        val currentWeight = repo.latestWeight()?.weightKg
            ?: profile.startWeight.takeIf { it > 0 }
            ?: 70.0

        val goal = NutritionCalculator.compute(
            gender = profile.gender,
            age = age,
            heightCm = profile.heightCm,
            weightKg = currentWeight,
            activityLevel = profile.activityLevel,
            mode = mode
        )

        repo.saveProfile(
            profile.copy(
                dailyKcalGoal = goal.kcal,
                proteinGoalG = goal.proteinG,
                carbGoalG = goal.carbG,
                fatGoalG = goal.fatG
            )
        )

        // 目标模式同时写回 DataStore。
        // 以前只有首次引导会写这个键，用户在资料页把「减重」改成「增肌」后，
        // DataStore 里还是旧的，别的读 goalMode 的地方（首页目标状态、引导重进）
        // 就会和档案里的目标对不上。这里补上，让两处永远一致。
        SettingsStore.set(getApplication(), SettingsKeys.GOAL_MODE, mode)
    }
}
