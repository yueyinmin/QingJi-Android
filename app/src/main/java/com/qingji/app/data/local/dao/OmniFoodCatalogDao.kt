package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.qingji.app.data.local.entity.FoodComposition
import com.qingji.app.data.local.entity.FoodDataSource
import com.qingji.app.data.local.entity.FoodQualityIssue
import com.qingji.app.data.local.entity.NutritionSample
import com.qingji.app.data.local.entity.OmniFoodEntity
import com.qingji.app.data.local.entity.OmniFoodNutrition
import kotlinx.coroutines.flow.Flow

/** OmniFood8K 只读查询入口。样本与组成关系不混进普通食物搜索。 */
@Dao
interface OmniFoodCatalogDao {

    @Query(
        """
        SELECT * FROM food_entity
        WHERE (:keyword = '' OR name_cn LIKE '%' || :keyword || '%'
            OR name_en LIKE '%' || :keyword || '%'
            OR aliases LIKE '%' || :keyword || '%'
            OR pinyin LIKE :keyword || '%'
            OR pinyin_full LIKE :keyword || '%')
          AND (:foodType IS NULL OR food_type = :foodType)
          AND (:sourceId IS NULL OR source_id = :sourceId)
        ORDER BY CASE
            WHEN name_cn = :keyword THEN 0
            WHEN pinyin_full = :keyword THEN 1
            WHEN name_cn LIKE :keyword || '%' THEN 2
            ELSE 3 END, name_cn
        LIMIT :limit
        """
    )
    suspend fun search(
        keyword: String,
        foodType: String? = null,
        sourceId: String? = null,
        limit: Int = 50
    ): List<OmniFoodEntity>

    @Query("SELECT * FROM food_entity WHERE food_id = :foodId LIMIT 1")
    suspend fun findById(foodId: String): OmniFoodEntity?

    @Query("SELECT * FROM food_nutrition WHERE food_id = :foodId LIMIT 1")
    suspend fun nutritionFor(foodId: String): OmniFoodNutrition?

    /** 组成明细按「样本」关联；单菜品样本的 parent_food_id 后续可在 ETL 补齐。 */
    @Query("SELECT * FROM food_composition WHERE parent_sample_id = :sampleId ORDER BY composition_id")
    suspend fun compositionForSample(sampleId: String): List<FoodComposition>

    @Query("SELECT * FROM nutrition_sample WHERE sample_id = :sampleId LIMIT 1")
    suspend fun sampleById(sampleId: String): NutritionSample?

    @Query("SELECT * FROM quality_issue WHERE sample_id = :sampleId")
    suspend fun issuesForSample(sampleId: String): List<FoodQualityIssue>

    @Query("SELECT * FROM food_data_source ORDER BY source_id")
    fun observeSources(): Flow<List<FoodDataSource>>

    @Query("SELECT COUNT(*) FROM food_entity")
    suspend fun foodCount(): Int

    @Query("SELECT COUNT(*) FROM nutrition_sample")
    suspend fun sampleCount(): Int

    @Query("SELECT COUNT(*) FROM food_composition")
    suspend fun compositionCount(): Int
}
