package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingji.app.data.local.entity.FoodFavorite
import com.qingji.app.data.local.entity.FoodSearchHistory
import com.qingji.app.data.local.entity.UserFood
import kotlinx.coroutines.flow.Flow

/** 用户自建食物：AI 新增、手动录入、扫码查到缓存下来的，都在这里。 */
@Dao
interface UserFoodDao {

    /** 「自定义」页签：按录入时间倒序。 */
    @Query("SELECT * FROM user_food ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserFood>>

    @Query("SELECT * FROM user_food ORDER BY createdAt DESC")
    suspend fun findAll(): List<UserFood>

    @Query("SELECT * FROM user_food WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserFood?

    @Query("SELECT * FROM user_food WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): UserFood?

    @Query("SELECT name FROM user_food")
    suspend fun allNames(): List<String>

    @Query(
        """
        SELECT * FROM user_food
        WHERE name LIKE '%' || :keyword || '%'
           OR pinyin LIKE :keyword || '%'
        ORDER BY name
        LIMIT 50
        """
    )
    suspend fun search(keyword: String): List<UserFood>

    @Query("SELECT * FROM user_food WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): UserFood?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UserFood): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<UserFood>)

    @Query("UPDATE user_food SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM user_food WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 备份「覆盖恢复」用：只清用户食物，标准库在另一个文件里，不会被碰。 */
    @Query("DELETE FROM user_food")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM user_food")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM user_food")
    fun observeCount(): Flow<Int>
}

/** 收藏。标准库只读，所以收藏不能写在标准库里，单独存一张表按名字记。 */
@Dao
interface FoodFavoriteDao {

    @Query("SELECT * FROM food_favorite")
    fun observeAll(): Flow<List<FoodFavorite>>

    @Query("SELECT name FROM food_favorite")
    suspend fun allNames(): List<String>

    /** 重复收藏同一名字直接忽略，不报错。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(item: FoodFavorite)

    @Query("DELETE FROM food_favorite WHERE name = :name")
    suspend fun remove(name: String)

    @Query("DELETE FROM food_favorite")
    suspend fun clearAll()
}

/** 搜索历史。keyword 上有唯一索引，重复搜同一个词只把时间刷新。 */
@Dao
interface FoodSearchHistoryDao {

    @Query("SELECT * FROM food_search_history ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodSearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: FoodSearchHistory)

    @Query("DELETE FROM food_search_history WHERE keyword = :keyword")
    suspend fun remove(keyword: String)

    @Query("DELETE FROM food_search_history")
    suspend fun clearAll()
}

