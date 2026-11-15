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

    /** 手动录入一条自定义食物，同样缓存进食物表。 */
    suspend fun saveManual(name: String, kcal: Double, code: String): Food {
        val food = Food(
            name = name.trim(),
            barcode = BarcodeRules.normalize(code).ifBlank { null },
            kcalPer100g = kcal,
            defaultGrams = 100.0,
            source = "user"
        )
        repo.saveFood(food)
        return food
    }

    /** v3.5.0：日期可选（默认今天），其余行为不变。 */
    suspend fun add(food: Food, grams: Double, mealType: String, date: LocalDate = AppClock.today()) {
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
                source = "barcode"
            )
        )
    }
}
