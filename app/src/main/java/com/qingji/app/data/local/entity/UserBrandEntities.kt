package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * 用户自建外卖品牌 + 品牌菜品（v1.5 食物创建体系重构 · 阶段四）
 * ------------------------------------------------------------
 * 为什么放在用户库 qingji.db，而不是标准库 food_catalog.db：
 *   标准库是 APK 预置的只读库，CATALOG_VERSION 变化时整库删掉重拷，
 *   用户自建的品牌写进去会被升级冲掉。这里和 user_food 一样可写、可迁移。
 *
 * 只读标准库已有的 brand / brand_dish_* 4+8 张表完全不碰，
 * 这里另起两张 user_ 前缀的表，互不干扰。
 *
 * 历史饮食记录 food_log 是「快照式」（存名字 + 当时的营养数字），
 * 所以删品牌/改菜品都不会让历史统计漂移——这条红线不动。
 * 品牌删除时关联菜品在 Repository 里用事务一起删，避免孤立数据。
 * ============================================================
 */

/** 用户自建外卖品牌。品牌名加唯一索引，重名不给建第二条。 */
@Entity(
    tableName = "user_brand",
    indices = [Index(value = ["name"], unique = true)]
)
data class UserBrand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 品牌标识：沿用轻记 emoji 图标体系，默认 🏪。 */
    @ColumnInfo(defaultValue = "🏪") val icon: String = "🏪",
    /** 品牌备注（选填）。 */
    @ColumnInfo(defaultValue = "") val note: String = "",
    /** 数据来源说明（选填，自由文本）。 */
    @ColumnInfo(defaultValue = "") val sourceLabel: String = "",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis()
)

/** 品牌下的菜品。营养字段与 user_food 对齐，复用统一折算工具。 */
@Entity(
    tableName = "user_brand_dish",
    indices = [Index("brandId"), Index("name")]
)
data class UserBrandDish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属品牌 id（对应 UserBrand.id）。删除品牌时按此列级联清菜品。 */
    val brandId: Long,
    val name: String,
    /** 菜品分类（如主食/凉拌/饮品），用于横排胶囊，动态 distinct 生成。 */
    @ColumnInfo(defaultValue = "其他") val category: String = "其他",
    /** 热量：按 nutritionBasis 计（每100g/每100ml/每份）。 */
    @ColumnInfo(defaultValue = "0.0") val kcal: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val protein: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val fat: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val carb: Double = 0.0,
    /** 计量基准：PER_100G / PER_100ML / PER_SERVING（常量见 FoodNutritionCalculator）。 */
    @ColumnInfo(defaultValue = "PER_100G") val nutritionBasis: String = "PER_100G",
    /** 单份/基准重量克数（PER_SERVING 时必填，其他基准可留 100）。 */
    @ColumnInfo(defaultValue = "100.0") val servingGrams: Double = 100.0,
    /** 数据来源：OFFICIAL/PUBLIC/MANUAL/USER_BRAND（常量见 FoodDataSource）。 */
    @ColumnInfo(defaultValue = "USER_BRAND") val source: String = "USER_BRAND",
    /** 备注（选填）。 */
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis()
)
