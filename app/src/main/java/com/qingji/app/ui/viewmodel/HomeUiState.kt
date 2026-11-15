package com.qingji.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.domain.AppClock
import java.time.LocalDate

/** 首页一屏要用的全部数据，打包成一个对象，界面只订阅这一个。 */
data class HomeUiState(
    val date: LocalDate = AppClock.today(),
    val profile: UserProfile? = null,
    val totalKcal: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarb: Double = 0.0,
    val totalFat: Double = 0.0,
    val waterMl: Int = 0,
    /** 当天运动消耗（计入热量缺口） */
    val exerciseKcal: Double = 0.0,
    val logs: List<FoodLog> = emptyList()
)
