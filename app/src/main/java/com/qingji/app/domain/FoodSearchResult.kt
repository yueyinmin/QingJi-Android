package com.qingji.app.domain

import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.UserBrand
import com.qingji.app.data.local.entity.UserBrandDish

/**
 * v1.5 阶段四：全局搜索的统一结果项。
 * 工单要求一次搜索覆盖四类数据，这里用 sealed 分层，界面一个 LazyColumn 渲染到底。
 */
sealed class FoodSearchResult {
    /** 标准食物 / 自定义食物 */
    data class FoodItem(val food: Food) : FoodSearchResult()
    /** 自建外卖品牌 */
    data class BrandItem(val brand: UserBrand) : FoodSearchResult()
    /** 自建品牌下的菜品 */
    data class DishItem(val dish: UserBrandDish, val brandName: String) : FoodSearchResult()

    /** 搜索范围：全部 / 家庭厨房 / 外卖快餐 / 自定义 */
    enum class Scope(val label: String) {
        ALL("全部"),
        KITCHEN("家庭厨房"),
        TAKEAWAY("外卖快餐"),
        CUSTOM("自定义")
    }
}
