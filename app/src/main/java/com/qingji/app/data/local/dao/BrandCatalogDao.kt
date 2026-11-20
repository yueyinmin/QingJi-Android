package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingji.app.data.local.entity.BrandDishIngredientEntity
import com.qingji.app.data.local.entity.BrandDishProcessingEntity
import com.qingji.app.data.local.entity.BrandDishTraceEntity
import com.qingji.app.data.local.entity.BrandEntity
import kotlinx.coroutines.flow.Flow

/**
 * 品牌目录只读查询入口（老乡鸡接入 · 标准库 Room v4）。
 * 品牌列表必须从这里动态读（工单红线 5），UI 不写死品牌。
 */
@Dao
interface BrandCatalogDao {

    /** 已启用品牌，按 sortOrder 排序——品牌选择栏的数据源。 */
    @Query("SELECT * FROM brand WHERE isEnabled = 1 ORDER BY sortOrder ASC, brandId ASC")
    fun observeBrands(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brand WHERE isEnabled = 1 ORDER BY sortOrder ASC, brandId ASC")
    suspend fun findEnabledBrands(): List<BrandEntity>

    @Query("SELECT * FROM brand WHERE brandId = :brandId LIMIT 1")
    suspend fun findById(brandId: String): BrandEntity?

    @Query("SELECT COUNT(*) FROM brand WHERE isEnabled = 1")
    suspend fun enabledBrandCount(): Int

    /** 某道堂食菜的溯源档案（详情页「菜品溯源」入口）。 */
    @Query("SELECT * FROM brand_dish_trace WHERE dishFoodId = :dishFoodId LIMIT 1")
    suspend fun traceForDish(dishFoodId: String): BrandDishTraceEntity?

    @Query("SELECT * FROM brand_dish_trace WHERE dishFoodId = :dishFoodId LIMIT 1")
    fun observeTraceForDish(dishFoodId: String): Flow<BrandDishTraceEntity?>

    /** 一道菜的原料来源（原料名 + 供应商）。 */
    @Query("SELECT * FROM brand_dish_ingredient WHERE dishFoodId = :dishFoodId ORDER BY id ASC")
    suspend fun ingredientsForDish(dishFoodId: String): List<BrandDishIngredientEntity>

    /** 一道菜的原料加工流程（中央厨房做了什么）。 */
    @Query("SELECT * FROM brand_dish_processing WHERE dishFoodId = :dishFoodId ORDER BY id ASC")
    suspend fun processingForDish(dishFoodId: String): List<BrandDishProcessingEntity>

    @Query("SELECT COUNT(*) FROM brand_dish_trace")
    suspend fun traceCount(): Int

    @Query("SELECT COUNT(*) FROM brand_dish_ingredient")
    suspend fun ingredientCount(): Int

    @Query("SELECT COUNT(*) FROM brand_dish_processing")
    suspend fun processingCount(): Int
}
