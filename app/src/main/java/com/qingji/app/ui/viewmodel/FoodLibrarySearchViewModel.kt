package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.FoodSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * v1.5 阶段四：食物库独立全局搜索页的数据管家。
 *
 * 防抖：关键词/范围变化后 200ms 才查库，避免每敲一个字都全库扫描。
 * 历史：复用现有 food_search_history（点中结果时记录）。
 * 页面状态恢复：本页是独立路由，退出时 pop 掉，底下食物库 Composable
 *   一直在返回栈里没销毁，来源/品牌/分类/滚动位置天然保留——这正是独立页方案的好处。
 */
@kotlinx.coroutines.FlowPreview
@kotlinx.coroutines.ExperimentalCoroutinesApi
class FoodLibrarySearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword

    private val _scope = MutableStateFlow(FoodSearchResult.Scope.ALL)
    val scope: StateFlow<FoodSearchResult.Scope> = _scope

    /** 最近搜过的词。 */
    val searchHistory: StateFlow<List<String>> = repo.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 结果：关键词+范围组合后防抖查库。空关键词返回空列表（界面显示"最近搜索/空态"）。 */
    val results: StateFlow<List<FoodSearchResult>> = kotlinx.coroutines.flow.combine(
        _keyword, _scope
    ) { kw, sc -> kw.trim() to sc }
        .debounce(200)
        .flatMapLatest { (kw, sc) ->
            flow {
                if (kw.isEmpty()) emit(emptyList<FoodSearchResult>())
                else emit(repo.searchAllFoods(kw, sc))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setKeyword(v: String) { _keyword.value = v }
    fun clearKeyword() { _keyword.value = "" }
    fun setScope(s: FoodSearchResult.Scope) { _scope.value = s }

    fun useHistory(keyword: String) { _keyword.value = keyword }

    /** 点中结果时记录搜索词。 */
    fun rememberCurrent() {
        val key = _keyword.value.trim()
        if (key.isNotEmpty()) viewModelScope.launch { repo.rememberSearch(key) }
    }

    fun addFoodToMeal(
        food: com.qingji.app.data.local.entity.Food,
        grams: Double,
        mealType: String,
        dayOffset: Int = 0
    ) {
        viewModelScope.launch {
            val consumed = com.qingji.app.domain.FoodNutritionCalculator.compute(
                kcalPerBasis = food.kcalPer100g,
                proteinPerBasis = food.proteinPer100g,
                carbPerBasis = food.carbPer100g,
                fatPerBasis = food.fatPer100g,
                basis = com.qingji.app.domain.FoodNutritionCalculator.normalizeBasis(food.nutritionBasis),
                servingGrams = food.defaultGrams,
                grams = grams
            )
            repo.addFoodLog(
                com.qingji.app.data.local.entity.FoodLog(
                    date = com.qingji.app.domain.AppClock.today().minusDays(dayOffset.toLong()),
                    mealType = mealType,
                    foodName = food.name,
                    grams = grams,
                    kcal = consumed.kcal,
                    proteinG = consumed.proteinG,
                    carbG = consumed.carbG,
                    fatG = consumed.fatG,
                    source = "manual"
                )
            )
        }
    }

    fun addDishToMeal(
        dish: com.qingji.app.data.local.entity.UserBrandDish,
        grams: Double,
        mealType: String,
        dayOffset: Int = 0
    ) {
        viewModelScope.launch { repo.addBrandDishToMeal(dish, grams, mealType, dayOffset) }
    }

    /**
     * QJ-RECORD-INPUT-UNIFIED-V3 / B4：按草稿里的营养参数 + 日期时刻写入。
     *
     * 为什么不再区分「食物」和「品牌菜品」两条方法：
     * 两者落库只差营养参数来源，折算函数完全相同（FoodNutritionCalculator）；
     * 统一成「参数进、记录出」之后，记录弹层只依赖草稿，
     * 从「饮食日详情」返回、甚至进程被回收后都能照原样提交。
     */
    fun addMealEntryAt(
        foodName: String,
        kcalPerBasis: Double,
        proteinPerBasis: Double,
        carbPerBasis: Double,
        fatPerBasis: Double,
        basis: String,
        servingGrams: Double,
        grams: Double,
        mealType: String,
        date: java.time.LocalDate,
        time: java.time.LocalTime
    ) {
        viewModelScope.launch {
            val consumed = com.qingji.app.domain.FoodNutritionCalculator.compute(
                kcalPerBasis = kcalPerBasis,
                proteinPerBasis = proteinPerBasis,
                carbPerBasis = carbPerBasis,
                fatPerBasis = fatPerBasis,
                basis = basis,
                servingGrams = servingGrams,
                grams = grams
            )
            repo.addFoodLogAt(
                date = date,
                time = time,
                mealType = mealType,
                foodName = foodName,
                grams = grams,
                kcal = consumed.kcal,
                proteinG = consumed.proteinG,
                carbG = consumed.carbG,
                fatG = consumed.fatG
            )
        }
    }
}
