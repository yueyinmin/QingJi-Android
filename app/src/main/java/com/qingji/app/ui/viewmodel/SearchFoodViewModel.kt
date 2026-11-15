package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.FoodRanking
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchFoodViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    /**
     * 关键词 / 「最近 30 天吃过什么」/ **食物库条数** 任一变化，都会重新查库并重排。
     * 空关键词会列出前 50 条，正好当默认列表。
     *
     * v1.0.4：标准食物库是随 APK 预置的独立数据库，Room 首次打开时自动拷出来，
     * 这里仍然订阅条数，是为了兼容「首次开库稍慢」和用户自己新增食物的实时刷新。
     */
    val results: StateFlow<List<Food>> = combine(
        _keyword,
        repo.observeRecentFoodNames(),
        repo.observeFoodCount()
    ) { keyword, recentNames, _ -> keyword to recentNames }
        .mapLatest { (keyword, recentNames) ->
            val base = repo.searchFood(keyword.trim())
            FoodRanking.rank(base, { it.name }, FoodRanking.countRecent(recentNames))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 食物库里现在有多少条。-1 表示「还没查到」。
     * 界面用它区分三种状态：还在载入 / 一条都没有 / 有数据但没搜到。
     */
    val foodCount: StateFlow<Int> = repo.observeFoodCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1)

    /** 最近搜过的词，搜索框下面一排快捷入口。 */
    val searchHistory: StateFlow<List<String>> = repo.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 标准食物库现在是随 APK 走的只读库，不需要「补数据」。
     * 按钮保留只是给「库没读出来」的情况一个手动重试的机会，返回当前能读到的条数。
     */
    suspend fun restorePresetFoods(): Int = repo.ensurePresetFoods()

    /** 点中一条结果时，把这次搜的词记进历史；下次打开搜索页一点就能用。 */
    fun rememberSearch() {
        val key = _keyword.value.trim()
        if (key.isBlank()) return
        viewModelScope.launch { repo.rememberSearch(key) }
    }

    fun onKeywordChange(value: String) {
        _keyword.value = value
    }

    /**
     * 把食物库的一条换算成一条饮食记录。
     * 表里存的是「每 100 克」的营养，所以要乘 克数/100。
     */
    suspend fun addFood(
        food: Food,
        grams: Double,
        mealType: String,
        date: LocalDate
    ) {
        val ratio = grams / 100.0
        repo.addFoodLog(
            FoodLog(
                date = date,
                mealType = mealType,
                foodName = food.name,
                grams = grams,
                kcal = food.kcalPer100g * ratio,
                proteinG = food.proteinPer100g * ratio,
                carbG = food.carbPer100g * ratio,
                fatG = food.fatPer100g * ratio,
                source = "manual"
            )
        )
    }
}
