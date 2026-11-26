package com.qingji.app.domain

import com.qingji.app.data.local.entity.FoodLog

/** 一天的饮食营养汇总（详情页顶部四项）。 */
data class FoodDayNutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    val itemCount: Int = 0
) {
    /** 三大营养素折算成热量占比用的总热量基底（蛋白/碳水 4 kcal/g，脂肪 9 kcal/g）。 */
    val macroKcal: Double get() = (protein * 4 + carb * 4 + fat * 9).takeIf { it > 0.0 } ?: 1.0
}

/**
 * ============================================================
 * 日级饮食分组与汇总（QJ-RECORD-INPUT-UNIFIED-V3 / B5）
 * ------------------------------------------------------------
 * 为什么单独抽成纯函数：
 * 详情页要保证「首页怎么分组、详情页就怎么分组」，
 * 汇总也必须和首页/单餐详情同一口径 —— 直接对 FoodLog 已有字段求和，
 * 绝不重新套一遍营养折算公式（那会算出第二套热量）。
 *
 * 分组固定按 [MealRules.ALL] 的顺序（早/午/晚/加餐），
 * 即使某一餐没有记录也保留空列表，页面上四张卡片的位置不会跳来跳去。
 * ============================================================
 */
object FoodDayGrouping {

    /** 按餐次分组；顺序固定为 早餐 → 午餐 → 晚餐 → 加餐。 */
    fun group(logs: List<FoodLog>): Map<String, List<FoodLog>> {
        val byMeal = logs.groupBy { it.mealType }
        val result = LinkedHashMap<String, List<FoodLog>>()
        MealRules.ALL.forEach { type ->
            result[type] = byMeal[type].orEmpty().sortedBy { it.createdAt }
        }
        return result
    }

    /** 一天的四项汇总：直接对落库字段求和，不重算。 */
    fun totals(logs: List<FoodLog>): FoodDayNutrition = FoodDayNutrition(
        kcal = logs.sumOf { it.kcal },
        protein = logs.sumOf { it.proteinG },
        carb = logs.sumOf { it.carbG },
        fat = logs.sumOf { it.fatG },
        itemCount = logs.size
    )
}
