package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.FoodNutritionCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * v1.5 阶段二：「新增/编辑自定义食物」表单的数据管家。
 * 用无参构造 + startEdit(id) 回填，兼容 Compose 默认的 viewModel()。
 * editId = 0 新建；>0 编辑已有自定义食物。
 */
class CustomFoodEditViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    private var currentEditId: Long = 0L

    private val _loaded = MutableStateFlow(false)
    /** 表单是否已回填完成（新建 startEdit(0) 后为 true） */
    val loaded: StateFlow<Boolean> = _loaded

    private val _name = MutableStateFlow("")
    private val _brand = MutableStateFlow("")
    private val _kcal = MutableStateFlow("")
    private val _protein = MutableStateFlow("")
    private val _carb = MutableStateFlow("")
    private val _fat = MutableStateFlow("")
    private val _fiber = MutableStateFlow("")
    private val _sodium = MutableStateFlow("")
    private val _barcode = MutableStateFlow("")
    private val _category = MutableStateFlow(com.qingji.app.domain.FoodCategory.OTHER)
    private val _note = MutableStateFlow("")
    private val _basis = MutableStateFlow(FoodNutritionCalculator.PER_100G)
    private val _servingGrams = MutableStateFlow("100")

    val name: StateFlow<String> = _name
    val brand: StateFlow<String> = _brand
    val kcal: StateFlow<String> = _kcal
    val protein: StateFlow<String> = _protein
    val carb: StateFlow<String> = _carb
    val fat: StateFlow<String> = _fat
    val fiber: StateFlow<String> = _fiber
    val sodium: StateFlow<String> = _sodium
    val barcode: StateFlow<String> = _barcode
    val category: StateFlow<String> = _category
    val note: StateFlow<String> = _note
    val basis: StateFlow<String> = _basis
    val servingGrams: StateFlow<String> = _servingGrams

    private val _saved = MutableStateFlow<Long?>(null)
    /** 保存成功后的食物 id；界面据此返回。null = 还没保存成功 */
    val saved: StateFlow<Long?> = _saved

    /** 进入页面时调用一次；id=0 新建，id>0 编辑回填。 */
    fun startEdit(id: Long) {
        if (_loaded.value && currentEditId == id) return
        currentEditId = id
        if (id > 0) {
            _loaded.value = false
            viewModelScope.launch {
                repo.findCustomFood(id)?.let { fill(it) }
                _loaded.value = true
            }
        } else {
            _loaded.value = true
        }
    }

    private fun fill(f: Food) {
        _name.value = f.name
        _brand.value = f.brand
        _kcal.value = fmt(f.kcalPer100g)
        _protein.value = fmt(f.proteinPer100g)
        _carb.value = fmt(f.carbPer100g)
        _fat.value = fmt(f.fatPer100g)
        _fiber.value = f.fiberPer100g?.let { fmt(it) } ?: ""
        _sodium.value = f.sodiumMgPer100g?.let { fmt(it) } ?: ""
        _barcode.value = f.barcode ?: ""
        _category.value = f.category
        _note.value = f.note
        _basis.value = FoodNutritionCalculator.normalizeBasis(f.nutritionBasis)
        _servingGrams.value = fmt(f.defaultGrams)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    fun setName(v: String) { _name.value = v }
    fun setBrand(v: String) { _brand.value = v }
    fun setKcal(v: String) { _kcal.value = v }
    fun setProtein(v: String) { _protein.value = v }
    fun setCarb(v: String) { _carb.value = v }
    fun setFat(v: String) { _fat.value = v }
    fun setFiber(v: String) { _fiber.value = v }
    fun setSodium(v: String) { _sodium.value = v }
    fun setBarcode(v: String) { _barcode.value = v }
    fun setCategory(v: String) { _category.value = v }
    fun setNote(v: String) { _note.value = v }
    fun setBasis(v: String) { if (v in FoodNutritionCalculator.ALL_BASIS) _basis.value = v }
    fun setServingGrams(v: String) { _servingGrams.value = v }

    /** 热量/三大营养素一致性提示（只提示、不覆盖用户填写）。 */
    fun consistencyNote(): String? = FoodNutritionCalculator.consistencyNote(
        kcal = num(_kcal.value),
        protein = num(_protein.value),
        carb = num(_carb.value),
        fat = num(_fat.value)
    )

    /** 表单校验：名称必填、营养数字合法（>=0、可空留空、非 NaN）。 */
    val nameError: String?
        get() = if (_name.value.isBlank()) "请填写食物名称" else null

    fun numberError(field: String): String? {
        val raw = when (field) {
            "kcal" -> _kcal.value
            "protein" -> _protein.value
            "carb" -> _carb.value
            "fat" -> _fat.value
            "fiber" -> _fiber.value
            "sodium" -> _sodium.value
            "serving" -> _servingGrams.value
            else -> ""
        }
        if (raw.isBlank() && field != "kcal" && field != "serving") return null
        if (raw.isBlank()) return "不能为空"
        val v = raw.toDoubleOrNull() ?: return "请填数字（支持小数）"
        if (!v.isFinite() || v < 0) return "不能是负数"
        return null
    }

    fun save() {
        if (nameError != null) return
        if (listOf("kcal", "protein", "carb", "fat", "fiber", "sodium", "serving")
            .any { numberError(it) != null }
        ) return
        viewModelScope.launch {
            val id = repo.saveCustomFood(
                id = currentEditId,
                name = _name.value,
                brand = _brand.value,
                kcalPerBasis = num(_kcal.value),
                protein = num(_protein.value),
                carb = num(_carb.value),
                fat = num(_fat.value),
                fiber = _fiber.value.toDoubleOrNull(),
                sodiumMg = _sodium.value.toDoubleOrNull(),
                basis = _basis.value,
                servingGrams = _servingGrams.value.toDoubleOrNull() ?: 100.0,
                barcode = _barcode.value,
                category = _category.value,
                note = _note.value
            )
            if (id >= 0) _saved.value = id
        }
    }

    private fun num(raw: String): Double = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
}
