package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.UserBrand
import com.qingji.app.data.local.entity.UserBrandDish
import com.qingji.app.data.repository.QingJiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * v1.5 阶段四：用户自建外卖品牌管理页的数据管家。
 * 用无参构造兼容 Compose 默认 viewModel()。
 * 管理：品牌列表 + 当前品牌的菜品 + 新建/编辑/删除。
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class UserBrandViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val brands: StateFlow<List<UserBrand>> = repo.observeUserBrands()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedBrandId = MutableStateFlow(0L)
    val selectedBrandId: StateFlow<Long> = _selectedBrandId

    /** 当前选中品牌的菜品，品牌切换时自动换数据源。 */
    val dishes: StateFlow<List<UserBrandDish>> = _selectedBrandId
        .flatMapLatest { id -> if (id > 0) repo.observeBrandDishes(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    /** 进入页面时选第一个品牌（若尚未选或当前品牌已被删）。 */
    fun ensureSelected(current: List<UserBrand>) {
        if (current.isEmpty()) { _selectedBrandId.value = 0L; return }
        if (_selectedBrandId.value !in current.map { it.id }) {
            _selectedBrandId.value = current.first().id
        }
    }

    fun selectBrand(id: Long) { _selectedBrandId.value = id }

    /** 新建品牌，返回新 id（-1 = 重名失败）。 */
    fun createBrand(name: String, icon: String, note: String, sourceLabel: String, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.createUserBrand(name, icon, note, sourceLabel)
            if (id >= 0) { _selectedBrandId.value = id; _message.value = "品牌已创建" }
            else _message.value = "已有同名品牌，换一个名字"
            onResult(id)
        }
    }

    fun updateBrand(brand: UserBrand) {
        viewModelScope.launch { repo.updateUserBrand(brand); _message.value = "品牌已更新" }
    }

    fun deleteBrand(id: Long) {
        viewModelScope.launch { repo.deleteUserBrand(id); _message.value = "品牌及其菜品已删除" }
    }

    fun saveDish(dish: UserBrandDish, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.saveBrandDish(dish)
            onResult(id)
        }
    }

    fun deleteDish(id: Long) {
        viewModelScope.launch { repo.deleteBrandDish(id); _message.value = "菜品已删除" }
    }

    fun toggleDishFavorite(dish: UserBrandDish) {
        viewModelScope.launch { repo.setBrandDishFavorite(dish.id, !dish.favorite) }
    }

    /** 菜品按实际克数折算后写入饮食记录。 */
    fun addDishToMeal(dish: UserBrandDish, grams: Double, mealType: String, dayOffset: Int = 0) {
        viewModelScope.launch {
            repo.addBrandDishToMeal(dish, grams, mealType, dayOffset)
            _message.value = "已加入${mealLabel(mealType)}"
        }
    }

    private fun mealLabel(m: String) = when (m) {
        "breakfast" -> "早餐"; "lunch" -> "午餐"; "dinner" -> "晚餐"; else -> "加餐"
    }
}
