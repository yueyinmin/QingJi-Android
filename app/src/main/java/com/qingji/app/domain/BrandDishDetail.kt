package com.qingji.app.domain

import com.qingji.app.data.local.entity.BrandDishIngredientEntity
import com.qingji.app.data.local.entity.BrandDishProcessingEntity
import com.qingji.app.data.local.entity.BrandDishTraceEntity

/**
 * 老乡鸡堂食菜一道菜的「菜品溯源」聚合（工单第九节折叠详情页）。
 * 数据链路（全在 food_catalog.db 只读库）：
 *   food_catalog.food_id → brand_dish_trace.dishFoodId
 *     → brand_dish_ingredient（原料 + 供应商）
 *     → brand_dish_processing（中央厨房/门店加工流程）
 *
 * 溯源来自《老乡鸡菜品溯源报告 2.0》，每条带报告页码。
 * 查不到 trace 时界面显示「暂无公开资料」，绝不推测加工方式或供应商（红线）。
 */
data class BrandDishDetail(
    val trace: BrandDishTraceEntity,
    val ingredients: List<BrandDishIngredientEntity>,
    val processing: List<BrandDishProcessingEntity>
) {
    /** 加工等级中文标签，四枚举如实映射；UNKNOWN 就说未知，不猜。 */
    val processingLabel: String
        get() = when (trace.processingLevel) {
            "RESTAURANT_FRESH" -> "餐厅现做"
            "SEMI_PREPARED" -> "半预制"
            "REHEAT_PREPARED" -> "复热预制"
            else -> "未知"
        }
}
