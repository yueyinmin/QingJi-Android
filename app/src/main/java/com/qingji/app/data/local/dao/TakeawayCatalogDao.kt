package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingji.app.data.local.entity.TakeawayComboEntity
import com.qingji.app.data.local.entity.TakeawayComboItemEntity
import com.qingji.app.data.local.entity.TakeawayDataSourceEntity
import com.qingji.app.data.local.entity.TakeawayNutritionEntity
import com.qingji.app.data.local.entity.TakeawayPlatformEntity
import com.qingji.app.data.local.entity.TakeawayProductEntity
import com.qingji.app.data.local.entity.TakeawayStoreScopeEntity
import com.qingji.app.data.local.entity.TakeawayVariantEntity
import kotlinx.coroutines.flow.Flow

/**
 * 外卖快餐独立体系只读入口（标准库 Room v5）。
 * 首期 8 张表全空——DAO 先把接口备好，UI 走空态；真实商品导入后无需改界面。
 * 商品按 brandId 严格隔离，绝不混入其他品牌，更不把堂食菜当外卖商品（红线 3/4）。
 */
@Dao
interface TakeawayCatalogDao {

    /** 某品牌已启用的商品（按品牌 + 状态过滤，菜单数据源）。 */
    @Query(
        "SELECT * FROM takeaway_product WHERE brandId = :brandId " +
            "AND status NOT IN ('EXPIRED','DISABLED') ORDER BY categoryId ASC, productId ASC"
    )
    fun observeProducts(brandId: String): Flow<List<TakeawayProductEntity>>

    @Query(
        "SELECT * FROM takeaway_product WHERE brandId = :brandId " +
            "AND (:categoryId = '' OR categoryId = :categoryId) " +
            "AND status NOT IN ('EXPIRED','DISABLED') ORDER BY productId ASC"
    )
    suspend fun products(brandId: String, categoryId: String): List<TakeawayProductEntity>

    /** 商品名/别名/拼音搜当前品牌（预留，首期空表）。 */
    @Query(
        "SELECT * FROM takeaway_product WHERE brandId = :brandId AND " +
            "(productName LIKE '%'||:kw||'%' OR aliases LIKE '%'||:kw||'%') " +
            "AND status NOT IN ('EXPIRED','DISABLED') ORDER BY productId ASC"
    )
    suspend fun searchProducts(brandId: String, kw: String): List<TakeawayProductEntity>

    /** 有真实商品的分类（动态菜单分类：无商品的分类不展示）。 */
    @Query(
        "SELECT DISTINCT categoryId FROM takeaway_product WHERE brandId = :brandId " +
            "AND status NOT IN ('EXPIRED','DISABLED') AND categoryId != '' ORDER BY categoryId ASC"
    )
    suspend fun categoriesWithProducts(brandId: String): List<String>

    @Query("SELECT * FROM takeaway_variant WHERE productId = :productId ORDER BY variantId ASC")
    suspend fun variantsFor(productId: String): List<TakeawayVariantEntity>

    @Query("SELECT * FROM takeaway_combo WHERE productId = :productId")
    suspend fun comboFor(productId: String): TakeawayComboEntity?

    @Query("SELECT * FROM takeaway_combo_item WHERE comboId = :comboId ORDER BY sortOrder ASC, id ASC")
    suspend fun comboItems(comboId: String): List<TakeawayComboItemEntity>

    @Query("SELECT * FROM takeaway_nutrition WHERE productId = :productId ORDER BY id ASC")
    suspend fun nutritionFor(productId: String): List<TakeawayNutritionEntity>

    @Query("SELECT * FROM takeaway_platform ORDER BY platformId ASC")
    suspend fun platforms(): List<TakeawayPlatformEntity>

    @Query("SELECT * FROM takeaway_store_scope WHERE platformId = :platformId ORDER BY scopeId ASC")
    suspend fun storeScopes(platformId: String): List<TakeawayStoreScopeEntity>

    @Query("SELECT * FROM takeaway_data_source WHERE sourceId = :sourceId")
    suspend fun dataSource(sourceId: String): TakeawayDataSourceEntity?

    // ---- 计数：空态与导入自检 ----
    @Query("SELECT COUNT(*) FROM takeaway_product")
    suspend fun productCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_variant")
    suspend fun variantCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_combo")
    suspend fun comboCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_combo_item")
    suspend fun comboItemCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_nutrition")
    suspend fun nutritionCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_platform")
    suspend fun platformCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_store_scope")
    suspend fun storeScopeCount(): Int

    @Query("SELECT COUNT(*) FROM takeaway_data_source")
    suspend fun dataSourceCount(): Int
}
