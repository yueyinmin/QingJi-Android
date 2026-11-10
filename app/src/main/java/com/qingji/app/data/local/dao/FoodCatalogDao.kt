package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingji.app.data.local.entity.CatalogFood
import kotlinx.coroutines.flow.Flow

/**
 * 标准食物库的查询入口。
 *
 * 这张表只读：整个文件里没有 @Insert / @Update / @Delete，
 * 想加食物只能加到用户库的 user_food 表，标准库永远不会被写脏。
 *
 * 搜索覆盖四种输入：中文、别名、拼音首字母、拼音全拼。
 * 排序把「完全命中」放最前，避免搜「米饭」时先蹦出一堆「××米饭××」。
 */
@Dao
interface FoodCatalogDao {

    @Query("SELECT * FROM food_catalog ORDER BY name")
    fun observeAll(): Flow<List<CatalogFood>>

    @Query("SELECT * FROM food_catalog ORDER BY name")
    suspend fun findAll(): List<CatalogFood>

    @Query("SELECT * FROM food_catalog WHERE food_id = :id LIMIT 1")
    suspend fun findById(id: Long): CatalogFood?

    @Query(
        """
        SELECT * FROM food_catalog
        WHERE name LIKE '%' || :keyword || '%'
           OR aliases LIKE '%' || :keyword || '%'
           OR pinyin LIKE :keyword || '%'
           OR pinyin_full LIKE :keyword || '%'
        ORDER BY
            CASE
                WHEN name = :keyword THEN 0
                WHEN pinyin_full = :keyword THEN 1
                WHEN name LIKE :keyword || '%' THEN 2
                WHEN pinyin LIKE :keyword THEN 3
                ELSE 4
            END,
            name
        LIMIT 50
        """
    )
    suspend fun search(keyword: String): List<CatalogFood>

    @Query("SELECT * FROM food_catalog WHERE category = :category ORDER BY name")
    fun observeByCategory(category: String): Flow<List<CatalogFood>>

    /** 按名字精确取一条：AI 识别结果校正营养值、备份导入查重用。 */
    @Query("SELECT * FROM food_catalog WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CatalogFood?

    @Query("SELECT COUNT(*) FROM food_catalog")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM food_catalog")
    fun observeCount(): Flow<Int>
}
