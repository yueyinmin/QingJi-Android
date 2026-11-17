package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * OmniFood8K 扩展表（food_catalog.db，只读）。
 * 与旧 food_catalog 表并存：旧表继续给现有搜索/饮食记录用，
 * 新表保存来源、审核状态、原始样本和组成关系，供后续逐步接入。
 */

@Entity(
    tableName = "food_entity",
    indices = [Index("name_cn"), Index("source_code"), Index("pinyin"), Index("pinyin_full")]
)
data class OmniFoodEntity(
    @PrimaryKey @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_code") val sourceCode: String,
    @ColumnInfo(name = "name_cn") val nameCn: String,
    @ColumnInfo(name = "name_en") val nameEn: String = "",
    @ColumnInfo(name = "aliases") val aliases: String = "",
    @ColumnInfo(name = "pinyin") val pinyin: String = "",
    @ColumnInfo(name = "pinyin_full") val pinyinFull: String = "",
    @ColumnInfo(name = "food_type") val foodType: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "data_source") val dataSource: String,
    @ColumnInfo(name = "license") val license: String,
    @ColumnInfo(name = "audit_status") val auditStatus: String,
    @ColumnInfo(name = "dataset_version") val datasetVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "food_nutrition", indices = [Index("food_id")])
data class OmniFoodNutrition(
    @PrimaryKey @ColumnInfo(name = "nutrition_id") val nutritionId: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "reference_weight_g") val referenceWeightG: Double,
    @ColumnInfo(name = "energy_kj_original") val energyKjOriginal: Double,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "carbohydrates_g") val carbohydratesG: Double,
    @ColumnInfo(name = "energy_kcal_per_100g") val energyKcalPer100g: Double,
    @ColumnInfo(name = "protein_per_100g") val proteinPer100g: Double,
    @ColumnInfo(name = "fat_per_100g") val fatPer100g: Double,
    @ColumnInfo(name = "carbohydrates_per_100g") val carbohydratesPer100g: Double,
    @ColumnInfo(name = "nutrition_basis") val nutritionBasis: String,
    @ColumnInfo(name = "audit_status") val auditStatus: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_sample_id") val sourceSampleId: String
)

@Entity(tableName = "nutrition_sample")
data class NutritionSample(
    @PrimaryKey @ColumnInfo(name = "sample_id") val sampleId: String,
    @ColumnInfo(name = "sample_type") val sampleType: String,
    @ColumnInfo(name = "total_weight_g") val totalWeightG: Double,
    @ColumnInfo(name = "total_energy_kj") val totalEnergyKj: Double,
    @ColumnInfo(name = "total_energy_kcal") val totalEnergyKcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "carbohydrates_g") val carbohydratesG: Double,
    @ColumnInfo(name = "component_count") val componentCount: Int,
    @ColumnInfo(name = "quality_status") val qualityStatus: String,
    @ColumnInfo(name = "source_dataset") val sourceDataset: String
)

@Entity(tableName = "food_composition", indices = [Index("parent_sample_id"), Index("ingredient_code")])
data class FoodComposition(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "composition_id") val compositionId: Long = 0,
    @ColumnInfo(name = "parent_sample_id") val parentSampleId: String,
    @ColumnInfo(name = "parent_food_id") val parentFoodId: String = "",
    @ColumnInfo(name = "ingredient_name") val ingredientName: String,
    @ColumnInfo(name = "ingredient_code") val ingredientCode: String = "",
    @ColumnInfo(name = "ingredient_weight_g") val ingredientWeightG: Double,
    @ColumnInfo(name = "ingredient_type") val ingredientType: String,
    @ColumnInfo(name = "source_sample_id") val sourceSampleId: String
)

@Entity(tableName = "food_data_source")
data class FoodDataSource(
    @PrimaryKey @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "source_version") val sourceVersion: String,
    @ColumnInfo(name = "license") val license: String,
    @ColumnInfo(name = "attribution") val attribution: String,
    @ColumnInfo(name = "original_file") val originalFile: String,
    @ColumnInfo(name = "imported_at") val importedAt: String
)

@Entity(tableName = "quality_issue", indices = [Index("sample_id")])
data class FoodQualityIssue(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "issue_id") val issueId: Long = 0,
    @ColumnInfo(name = "sample_id") val sampleId: String,
    @ColumnInfo(name = "issue_flags") val issueFlags: String,
    @ColumnInfo(name = "weight_g") val weightG: Double,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Double,
    @ColumnInfo(name = "energy_kcal_per_100g") val energyKcalPer100g: Double,
    @ColumnInfo(name = "protein_g_per_100g") val proteinGPer100g: Double,
    @ColumnInfo(name = "fat_g_per_100g") val fatGPer100g: Double,
    @ColumnInfo(name = "carbohydrates_g_per_100g") val carbohydratesGPer100g: Double,
    @ColumnInfo(name = "component_weight_sum_g") val componentWeightSumG: Double,
    @ColumnInfo(name = "component_weight_diff_g") val componentWeightDiffG: Double,
    @ColumnInfo(name = "energy_relative_error_4_9_4") val energyRelativeError: Double,
    @ColumnInfo(name = "source_dataset") val sourceDataset: String
)
