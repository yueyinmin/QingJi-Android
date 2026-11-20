package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.qingji.app.data.local.entity.UserBrand
import com.qingji.app.data.local.entity.UserBrandDish
import kotlinx.coroutines.flow.Flow

/**
 * 用户自建外卖品牌 + 菜品的 DAO（v1.5 阶段四）。
 * 只读标准库那 4+8 张品牌表用的是 BrandCatalogDao / TakeawayCatalogDao，本 DAO 不碰它们。
 */
@Dao
interface UserBrandDao {

    // ---------- 品牌 ----------
    @Query("SELECT * FROM user_brand ORDER BY sortOrder ASC, createdAt ASC")
    fun observeBrands(): Flow<List<UserBrand>>

    @Query("SELECT * FROM user_brand ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun allBrands(): List<UserBrand>

    @Query("SELECT * FROM user_brand WHERE id = :id LIMIT 1")
    suspend fun brandById(id: Long): UserBrand?

    @Query("SELECT * FROM user_brand WHERE name = :name LIMIT 1")
    suspend fun brandByName(name: String): UserBrand?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBrand(brand: UserBrand): Long

    @Update
    suspend fun updateBrand(brand: UserBrand)

    /** 删品牌：事务里先清该品牌的菜品，再删品牌本身，杜绝孤立菜品。 */
    @Transaction
    suspend fun deleteBrandCascading(brandId: Long) {
        deleteDishesOfBrand(brandId)
        deleteBrandById(brandId)
    }

    @Query("DELETE FROM user_brand WHERE id = :id")
    suspend fun deleteBrandById(id: Long)

    @Query("SELECT COUNT(*) FROM user_brand")
    suspend fun brandCount(): Int

    // ---------- 菜品 ----------
    @Query("SELECT * FROM user_brand_dish WHERE brandId = :brandId ORDER BY category ASC, name ASC")
    fun observeDishes(brandId: Long): Flow<List<UserBrandDish>>

    @Query("SELECT * FROM user_brand_dish WHERE brandId = :brandId ORDER BY category ASC, name ASC")
    suspend fun dishesOfBrand(brandId: Long): List<UserBrandDish>

    @Query("SELECT * FROM user_brand_dish WHERE id = :id LIMIT 1")
    suspend fun dishById(id: Long): UserBrandDish?

    /** 全局搜菜品（搜索页用），空关键词守卫避免 LIKE '%%' 全表命中。 */
    @Query(
        """
        SELECT * FROM user_brand_dish
        WHERE :keyword <> '' AND name LIKE '%' || :keyword || '%'
        ORDER BY name LIMIT 50
        """
    )
    suspend fun searchDishes(keyword: String): List<UserBrandDish>

    @Query("SELECT DISTINCT category FROM user_brand_dish WHERE brandId = :brandId ORDER BY category")
    suspend fun dishCategories(brandId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDish(dish: UserBrandDish): Long

    @Update
    suspend fun updateDish(dish: UserBrandDish)

    @Query("DELETE FROM user_brand_dish WHERE id = :id")
    suspend fun deleteDishById(id: Long)

    @Query("DELETE FROM user_brand_dish WHERE brandId = :brandId")
    suspend fun deleteDishesOfBrand(brandId: Long)

    @Query("UPDATE user_brand_dish SET favorite = :favorite WHERE id = :id")
    suspend fun setDishFavorite(id: Long, favorite: Boolean)

    /** 备份「覆盖恢复」用：先清菜品再清品牌，整段重建。 */
    @Transaction
    suspend fun clearAll() {
        clearAllDishes()
        clearAllBrands()
    }

    @Query("DELETE FROM user_brand_dish")
    suspend fun clearAllDishes()

    @Query("DELETE FROM user_brand")
    suspend fun clearAllBrands()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBrands(items: List<UserBrand>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDishes(items: List<UserBrandDish>)
}
