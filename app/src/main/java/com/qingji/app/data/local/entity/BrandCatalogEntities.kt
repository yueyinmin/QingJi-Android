package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * 品牌目录（老乡鸡接入 · 标准库 Room v4）
 * ------------------------------------------------------------
 * 品牌必须由数据库动态驱动（工单红线 5：不得写死在 UI 里），
 * 后续加肯德基/麦当劳时只插一行 brand，页面自动出现。
 * 溯源数据全部来自《老乡鸡菜品溯源报告 2.0》（lxj-trace 提取），
 * 每条带报告页码；无 LICENSE → 全部 needs_verification，不标已核对。
 * 原文段落与图片不进 APK，只存结构化字段。
 * ============================================================
 */

/** 外卖/连锁品牌。首期只有老乡鸡，后续品牌插行即扩。 */
@Entity(tableName = "brand")
data class BrandEntity(
    /** 稳定 ID：laoxiangji / kfc / mcdonalds … */
    @PrimaryKey val brandId: String,
    /** 展示名：老乡鸡 */
    val name: String,
    @ColumnInfo(defaultValue = "") val displayName: String = "",
    /** 来源说明（官方渠道/报告版本），UI 详情页展示 */
    @ColumnInfo(defaultValue = "") val attribution: String = "",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** 报告版本（laoxiangji = 溯源报告 2.0） */
    @ColumnInfo(defaultValue = "") val dataVersion: String = "",
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true
)

/** 菜品溯源档案（BrandDishTrace）：一道堂食菜的报告溯源页。 */
@Entity(
    tableName = "brand_dish_trace",
    indices = [Index("dishFoodId"), Index("brandId")]
)
data class BrandDishTraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val brandId: String = "",
    /** 关联 food_catalog.food_id（TEXT 形式，如 "200001"）；未入库的菜为空串 */
    @ColumnInfo(defaultValue = "") val dishFoodId: String = "",
    /** 规范化菜名（去空格/去版本后缀），用于与堂食库匹配 */
    @ColumnInfo(defaultValue = "") val dishKey: String = "",
    val dishName: String,
    /** RESTAURANT_FRESH / SEMI_PREPARED / REHEAT_PREPARED / UNKNOWN（工单 5.6 四枚举） */
    @ColumnInfo(defaultValue = "UNKNOWN") val processingLevel: String = "UNKNOWN",
    /** 报告原文标注（含「/」= 报告未逐菜标注），UI 如实展示 */
    @ColumnInfo(defaultValue = "") val processingLevelRaw: String = "",
    @ColumnInfo(defaultValue = "") val flavor: String = "",
    @ColumnInfo(defaultValue = "") val bestFlavorPeriod: String = "",
    /** 报告印刷页码（页脚 - N -）与 PDF 页码，双留痕 */
    @ColumnInfo(defaultValue = "0") val reportPage: Int = 0,
    @ColumnInfo(defaultValue = "0") val pdfPage: Int = 0,
    @ColumnInfo(defaultValue = "") val cookingMethod: String = "",
    @ColumnInfo(defaultValue = "") val deliveryText: String = "",
    @ColumnInfo(defaultValue = "") val storeProcessText: String = "",
    /** 溯源来源版本（lxj-trace commit SHA） */
    @ColumnInfo(defaultValue = "") val sourceVersion: String = "",
    @ColumnInfo(defaultValue = "needs_verification") val reviewStatus: String = "needs_verification"
)

/** 菜品原料来源（BrandDishIngredient）：一菜多原料，原料带供应商。 */
@Entity(
    tableName = "brand_dish_ingredient",
    indices = [Index("dishFoodId"), Index("ingredientName")]
)
data class BrandDishIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val brandId: String = "",
    @ColumnInfo(defaultValue = "") val dishFoodId: String = "",
    val ingredientName: String,
    /** 供应商名；报告没写就空串，绝不推测 */
    @ColumnInfo(defaultValue = "") val supplier: String = "",
    @ColumnInfo(defaultValue = "") val sourceVersion: String = "",
    @ColumnInfo(defaultValue = "needs_verification") val reviewStatus: String = "needs_verification"
)

/** 原料加工流程（中央厨房/供应商端处理了什么）。 */
@Entity(
    tableName = "brand_dish_processing",
    indices = [Index("dishFoodId")]
)
data class BrandDishProcessingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val brandId: String = "",
    @ColumnInfo(defaultValue = "") val dishFoodId: String = "",
    /** 加工对象（如「虎皮扣肉」「梅干菜」） */
    val item: String,
    /** 加工描述（如「老乡鸡中央厨房漂烫、过油、卤制、分切」） */
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "") val sourceVersion: String = "",
    @ColumnInfo(defaultValue = "needs_verification") val reviewStatus: String = "needs_verification"
)
