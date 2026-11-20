package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.BarcodeRules
import com.qingji.app.domain.FoodNutritionCalculator
import java.time.LocalDate

/** 条码（M3.4）。先本地后联网，查到就缓存进食物表，下次断网也能用。 */
class BarcodeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    suspend fun resolve(rawCode: String): Result<Food> {
        val code = BarcodeRules.normalize(rawCode)
        if (code.isEmpty()) return Result.failure(IllegalArgumentException("请先输入或扫出一个条码"))

        repo.findFoodByBarcode(code)?.let { return Result.success(it) }

        return repo.lookupBarcodeRemote(code).fold(
            onSuccess = { product ->
                val food = Food(
                    name = product.name,
                    barcode = code,
                    kcalPer100g = product.kcalPer100g,
                    proteinPer100g = product.proteinPer100g,
                    carbPer100g = product.carbPer100g,
                    fatPer100g = product.fatPer100g,
                    defaultGrams = 100.0,
                    source = "openfoodfacts"
                )
                repo.saveFood(food)
                Result.success(food)
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * v1.5 阶段三：手动录入改为完整营养表单（命中/未命中共用）。
     * 条码保留并与食物关联（下次扫同条码优先命中本地）。
     * 三大营养素独立可选，基准可选，数据缺失时不冒充 0（由 UI 显示「暂无营养数据」）。
     */
    suspend fun saveManual(
        name: String,
        kcal: Double,
        code: String,
        protein: Double = 0.0,
        carb: Double = 0.0,
        fat: Double = 0.0,
        basis: String = FoodNutritionCalculator.PER_100G,
        servingGrams: Double = 100.0,
        category: String = com.qingji.app.domain.FoodCategory.OTHER,
        note: String = ""
    ): Food {
        val id = repo.saveCustomFood(
            id = 0L,
            name = name,
            kcalPerBasis = kcal,
            protein = protein,
            carb = carb,
            fat = fat,
            basis = basis,
            servingGrams = servingGrams,
            barcode = code,
            category = category,
            note = note
        )
        return repo.findCustomFood(id) ?: Food(
            name = name.trim(),
            barcode = BarcodeRules.normalize(code).ifBlank { null },
            kcalPer100g = kcal,
            source = "user"
        )
    }

    /** v1.5 阶段三：扫码命中结果可编辑——把用户改过的营养写回该自定义食物。 */
    suspend fun updateScannedFood(food: Food) {
        if (food.id > 0 && food.id < QingJiRepository.CATALOG_ID_START) {
            repo.saveCustomFood(
                id = food.id,
                name = food.name,
                kcalPerBasis = food.kcalPer100g,
                protein = food.proteinPer100g,
                carb = food.carbPer100g,
                fat = food.fatPer100g,
                basis = FoodNutritionCalculator.normalizeBasis(food.nutritionBasis),
                servingGrams = food.defaultGrams,
                barcode = food.barcode ?: "",
                category = food.category,
                note = food.note
            )
        }
    }

    /** v1.5：加餐折算走统一工具，支持计量基准，热量+三宏量同一倍率。 */
    suspend fun add(food: Food, grams: Double, mealType: String, date: LocalDate = AppClock.today()) {
        val consumed = FoodNutritionCalculator.compute(
            kcalPerBasis = food.kcalPer100g,
            proteinPerBasis = food.proteinPer100g,
            carbPerBasis = food.carbPer100g,
            fatPerBasis = food.fatPer100g,
            basis = FoodNutritionCalculator.normalizeBasis(food.nutritionBasis),
            servingGrams = food.defaultGrams,
            grams = grams
        )
        repo.addFoodLog(
            FoodLog(
                date = date,
                mealType = mealType,
                foodName = food.name,
                grams = grams,
                kcal = consumed.kcal,
                proteinG = consumed.proteinG,
                carbG = consumed.carbG,
                fatG = consumed.fatG,
                source = "barcode"
            )
        )
    }
}
