package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UserFoodGroup
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 食物库页的数据管家。
 * 三个页签各自一份数据：标准库（预置）、自定义（用户加的）、吃过的（历史聚合）。
 */
class FoodLibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val allFoods: StateFlow<List<com.qingji.app.data.local.entity.Food>> =
        repo.observeAllFoods()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userFoods: StateFlow<List<com.qingji.app.data.local.entity.Food>> =
        repo.observeUserFoods()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** v1.6：标准库只显示预置食物，不掺自定义（TASK-05 数据隔离）。 */
    val catalogFoods: StateFlow<List<com.qingji.app.data.local.entity.Food>> =
        repo.observeCatalogFoods()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ==================== v1.6 自定义食物组 ====================
    /** 全部食物组（按排序）。 */
    val foodGroups: StateFlow<List<UserFoodGroup>> =
        repo.observeFoodGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前展开查看的组 id；0 = 没展开。 */
    private val _expandedGroupId = MutableStateFlow(0L)
    val expandedGroupId: StateFlow<Long> = _expandedGroupId

    fun expandGroup(id: Long) { _expandedGroupId.value = id }

    /** 组内食物 id（展开某组时用；用 flatMapLatest 跟随展开的组切换）。 */
    val expandedGroupFoodIds: StateFlow<List<Long>> =
        _expandedGroupId.flatMapLatest { id ->
            if (id == 0L) kotlinx.coroutines.flow.flowOf(emptyList())
            else repo.observeGroupFoodIds(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 每个组的成员数，界面「N 个食物」显示。用 combine 汇总，组增删自动刷新。 */
    val groupCounts: StateFlow<Map<Long, Int>> = foodGroups.flatMapLatest { groups ->
        if (groups.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyMap())
        else combine(groups.map { g -> repo.observeGroupFoodCount(g.id).map { g.id to it } }) { pairs ->
            pairs.toMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 每个食物的组数（食物卡片「已归入 N 组」）。只对自定义食物订阅。 */
    val foodGroupCounts: StateFlow<Map<Long, Int>> = userFoods.flatMapLatest { foods ->
        if (foods.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyMap())
        else combine(foods.map { f -> repo.observeGroupsOfFood(f.id).map { f.id to it.size } }) { pairs ->
            pairs.toMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 新建组。重名返回 -1。 */
    suspend fun createFoodGroup(name: String, coverUri: String = "") = repo.createFoodGroup(name, coverUri)

    suspend fun updateFoodGroup(group: UserFoodGroup) = repo.updateFoodGroup(group)

    suspend fun deleteFoodGroup(groupId: Long) {
        repo.deleteFoodGroup(groupId)
        if (_expandedGroupId.value == groupId) _expandedGroupId.value = 0L
    }

    suspend fun addFoodToGroup(groupId: Long, foodId: Long) = repo.addFoodToGroup(groupId, foodId)

    suspend fun removeFoodFromGroup(groupId: Long, foodId: Long) = repo.removeFoodFromGroup(groupId, foodId)

    /** 一次性查某食物所在组（打开「加入组」弹窗时用）。 */
    suspend fun groupsOfFoodOnce(foodId: Long): List<Long> = repo.groupsOfFoodOnce(foodId)

    /** 一次性查某组的食物 id（打开「加成员」弹窗时用）。 */
    suspend fun groupFoodIds(groupId: Long): List<Long> = repo.groupFoodIds(groupId)

    /** 近 90 天吃过的食物，按最近一次时间倒序 */
    val eatenFoods: StateFlow<List<EatenFood>> = repo
        .observeFoodRange(AppClock.today().minusDays(90), AppClock.today())
        .map { logs ->
            logs.groupBy { it.foodName }
                .map { (name, list) ->
                    val last = list.maxByOrNull { it.date }
                    val per100 = if (last != null && last.grams > 0) {
                        last.kcal / last.grams * 100.0
                    } else 0.0
                    EatenFood(
                        name = name,
                        count = list.size,
                        lastDate = last?.date ?: AppClock.today(),
                        kcalPer100g = per100
                    )
                }
                .sortedByDescending { it.lastDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ==================== 品牌食物库（外卖快餐分支）====================
    /** 已启用品牌，数据库动态驱动；空 = 没有品牌（首期老乡鸡 1 个）。 */
    val brands: StateFlow<List<com.qingji.app.data.local.entity.BrandEntity>> =
        repo.observeBrands()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前选中的品牌 ID；切换只换菜单，不动收藏和历史。 */
    private val _selectedBrandId = MutableStateFlow("")
    val selectedBrandId: StateFlow<String> = _selectedBrandId

    /** 当前品牌菜单（堂食菜当菜单）。品牌变了自动换数据。 */
    val brandMenu: StateFlow<List<com.qingji.app.data.local.entity.Food>> =
        _selectedBrandId.flatMapLatest { id ->
            if (id.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
            else repo.observeBrandMenu(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectBrand(brandId: String) {
        _selectedBrandId.value = brandId
    }

    /** 打开外卖快餐时，默认选首个品牌（页面只负责调用，不写死品牌名）。 */
    fun ensureBrandSelected() {
        if (_selectedBrandId.value.isBlank() && brands.value.isNotEmpty()) {
            _selectedBrandId.value = brands.value.first().brandId
        }
    }

    suspend fun brandCategories(): List<String> =
        repo.brandCategories(_selectedBrandId.value)

    suspend fun brandDishDetail(food: com.qingji.app.data.local.entity.Food) =
        repo.brandDishDetail(food)

    fun toggleFavorite(food: com.qingji.app.data.local.entity.Food) {
        viewModelScope.launch { repo.setFoodFavorite(food.id, !food.favorite) }
    }

    /** 删除自定义食物（预置库不允许删） */
    fun deleteCustomFood(food: com.qingji.app.data.local.entity.Food) {
        viewModelScope.launch { repo.deleteFood(food.id) }
    }

    /**
     * 把食物加到某一餐（dayOffset：0 今天 / 1 昨天 / 2 前天）。
     * AI 专项修真 Bug：以前只按每 100g 换算了热量，蛋白 / 碳水 / 脂肪
     * 全部走 FoodLog 默认值 0，导致从食物库页记的一笔在首页、进展页、
     * 小组件里三大营养素都少算。现在四个数值统一乘 grams/100。
     */
    fun addToMeal(
        foodName: String,
        per100gKcal: Double,
        grams: Double,
        mealType: String,
        dayOffset: Int = 0,
        per100gProtein: Double = 0.0,
        per100gCarb: Double = 0.0,
        per100gFat: Double = 0.0,
        basis: String = com.qingji.app.domain.FoodNutritionCalculator.PER_100G,
        servingGrams: Double = 100.0
    ) {
        viewModelScope.launch {
            // v1.5：统一走公共折算工具，按计量基准换算；热量+三宏量用同一倍率。
            // 蛋白粉每100g、实际吃40g → ratio=0.4，四项一起乘 0.4。
            val consumed = com.qingji.app.domain.FoodNutritionCalculator.compute(
                kcalPerBasis = per100gKcal,
                proteinPerBasis = per100gProtein,
                carbPerBasis = per100gCarb,
                fatPerBasis = per100gFat,
                basis = basis,
                servingGrams = servingGrams,
                grams = grams
            )
            repo.addFoodLog(
                com.qingji.app.data.local.entity.FoodLog(
                    date = AppClock.today().minusDays(dayOffset.toLong()),
                    mealType = mealType,
                    foodName = foodName,
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

    /**
     * QJ-RECORD-INPUT-UNIFIED-V3 / 8.1：按「日期 + 具体时刻」加到某一餐。
     * 与 addToMeal 只差时间参数来源（绝对日期/时刻 vs 天数偏移），
     * 营养折算与校验完全共用，不产生第二套口径。
     */
    fun addToMealAt(
        foodName: String,
        perBasisKcal: Double,
        grams: Double,
        mealType: String,
        date: java.time.LocalDate,
        time: java.time.LocalTime,
        perBasisProtein: Double = 0.0,
        perBasisCarb: Double = 0.0,
        perBasisFat: Double = 0.0,
        basis: String = com.qingji.app.domain.FoodNutritionCalculator.PER_100G,
        servingGrams: Double = 100.0
    ) {
        viewModelScope.launch {
            val consumed = com.qingji.app.domain.FoodNutritionCalculator.compute(
                kcalPerBasis = perBasisKcal,
                proteinPerBasis = perBasisProtein,
                carbPerBasis = perBasisCarb,
                fatPerBasis = perBasisFat,
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
