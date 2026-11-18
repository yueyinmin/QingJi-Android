package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * 外卖快餐独立数据体系（老乡鸡接入 · 标准库 Room v5）
 * ------------------------------------------------------------
 * 工单第六节的 8 张表。首期没有任何真实外卖商品数据，全部建空表：
 * 红线 15「没真实数据就留空态，不造假」，红线 3「堂食菜不直接当外卖商品」。
 * 后续拿到真实外卖商品（官方渠道 / 用户手动提供）只往这些表导，不改结构。
 *
 * 设计规矩（和品牌溯源表一致）：
 * - 堂食菜只作「关联」（linkedFoodId），复制/继承营养一律禁止（红线 9）；
 * - 缺失的重量、营养、价格用 null，绝不写 0（0 = 真测到 0，null = 没数据）；
 * - 不建外键，靠 ID 逻辑关联，避免 Room 跨表 cascade 误删历史；
 * - 价格绑定平台 + 门店 + 地区 + 有效期，不做「全国统一价」。
 * ============================================================
 */

/** 6.1 外卖商品。productType：SINGLE 单点 / COMBO 套餐 / ADDON 加料。 */
@Entity(
    tableName = "takeaway_product",
    indices = [Index("brandId"), Index("categoryId"), Index("status"), Index("linkedFoodId")]
)
data class TakeawayProductEntity(
    /** 稳定 ID，如 lxj:p:0001；收藏/历史快照只认它，不认商品名 */
    @PrimaryKey val productId: String,
    val brandId: String,
    val productName: String,
    @ColumnInfo(defaultValue = "") val aliases: String = "",
    @ColumnInfo(defaultValue = "") val categoryId: String = "",
    @ColumnInfo(defaultValue = "SINGLE") val productType: String = "SINGLE",
    /** 关联的堂食菜 food_catalog.food_id（仅关联，不继承营养），空串 = 无 */
    @ColumnInfo(defaultValue = "") val linkedFoodId: String = "",
    @ColumnInfo(defaultValue = "") val imageSourceId: String = "",
    @ColumnInfo(defaultValue = "") val sourceId: String = "",
    /** VERIFIED / REVIEWED / PENDING / INCOMPLETE / EXPIRED / DISABLED */
    @ColumnInfo(defaultValue = "PENDING") val status: String = "PENDING",
    @ColumnInfo(defaultValue = "needs_verification") val reviewStatus: String = "needs_verification"
)

/** 6.2 商品规格。价格/重量按门店、地区、平台、有效期分别存，不合并全国价。 */
@Entity(
    tableName = "takeaway_variant",
    indices = [Index("productId")]
)
data class TakeawayVariantEntity(
    @PrimaryKey val variantId: String,
    val productId: String,
    @ColumnInfo(defaultValue = "") val variantName: String = "",
    /** null = 未收录；0 才有「确实为 0」的含义 */
    val servingWeightG: Double? = null,
    val edibleWeightG: Double? = null,
    /** 饮品用 ml，固体用 g */
    val servingVolumeMl: Double? = null,
    val quantity: Int? = null,
    val price: Double? = null,
    @ColumnInfo(defaultValue = "") val currency: String = "",
    @ColumnInfo(defaultValue = "") val region: String = "",
    @ColumnInfo(defaultValue = "") val storeId: String = "",
    @ColumnInfo(defaultValue = "") val platformId: String = "",
    @ColumnInfo(defaultValue = "") val validFrom: String = "",
    @ColumnInfo(defaultValue = "") val validTo: String = "",
    @ColumnInfo(defaultValue = "PENDING") val status: String = "PENDING"
)

/** 6.3 套餐。 */
@Entity(
    tableName = "takeaway_combo",
    indices = [Index("productId")]
)
data class TakeawayComboEntity(
    @PrimaryKey val comboId: String,
    val productId: String,
    val comboName: String,
    @ColumnInfo(defaultValue = "") val comboType: String = "",
    @ColumnInfo(defaultValue = "") val applicablePeople: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "") val sourceId: String = "",
    @ColumnInfo(defaultValue = "PENDING") val reviewStatus: String = "PENDING"
)

/** 6.4 套餐组成。一次组成 = 一行，可逐项勾选/改量。 */
@Entity(
    tableName = "takeaway_combo_item",
    indices = [Index("comboId"), Index("productId")]
)
data class TakeawayComboItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comboId: String,
    val productId: String,
    @ColumnInfo(defaultValue = "") val variantId: String = "",
    val quantity: Int = 1,
    val servingWeightG: Double? = null,
    @ColumnInfo(defaultValue = "0") val isOptional: Boolean = false,
    @ColumnInfo(defaultValue = "1") val defaultIncluded: Boolean = true,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0
)

/** 6.5 外卖商品营养。nutritionBasis：PER_100G / PER_100ML / PER_SERVING。 */
@Entity(
    tableName = "takeaway_nutrition",
    indices = [Index("productId"), Index("variantId")]
)
data class TakeawayNutritionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String,
    @ColumnInfo(defaultValue = "") val variantId: String = "",
    /** null = 未收录，禁止把缺失填 0 或按相似菜推测（红线 8/9） */
    val energy: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbohydrate: Double? = null,
    @ColumnInfo(defaultValue = "") val nutritionBasis: String = "",
    @ColumnInfo(defaultValue = "") val sourceId: String = "",
    @ColumnInfo(defaultValue = "INCOMPLETE") val reviewStatus: String = "INCOMPLETE"
)

/** 6.6 外卖平台。 */
@Entity(tableName = "takeaway_platform")
data class TakeawayPlatformEntity(
    @PrimaryKey val platformId: String,
    val name: String,
    @ColumnInfo(defaultValue = "") val attribution: String = "",
    @ColumnInfo(defaultValue = "") val dataVersion: String = ""
)

/** 6.7 门店/地区适用范围（价格、规格的真实作用域）。 */
@Entity(
    tableName = "takeaway_store_scope",
    indices = [Index("platformId")]
)
data class TakeawayStoreScopeEntity(
    @PrimaryKey val scopeId: String,
    @ColumnInfo(defaultValue = "") val platformId: String = "",
    @ColumnInfo(defaultValue = "") val region: String = "",
    @ColumnInfo(defaultValue = "") val storeId: String = "",
    @ColumnInfo(defaultValue = "") val validFrom: String = "",
    @ColumnInfo(defaultValue = "") val validTo: String = "",
    @ColumnInfo(defaultValue = "") val sourceId: String = ""
)

/** 6.8 外卖来源档案：每个字段都能追到平台/门店/采集时间与授权。 */
@Entity(tableName = "takeaway_data_source")
data class TakeawayDataSourceEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    @ColumnInfo(defaultValue = "") val url: String = "",
    @ColumnInfo(defaultValue = "") val filePath: String = "",
    @ColumnInfo(defaultValue = "") val reportVersion: String = "",
    @ColumnInfo(defaultValue = "") val page: String = "",
    @ColumnInfo(defaultValue = "") val commitSha: String = "",
    /** unknown / 已授权 / 仅内部参考，未授权素材不进 APK */
    @ColumnInfo(defaultValue = "unknown") val licenseStatus: String = "unknown",
    @ColumnInfo(defaultValue = "") val importedAt: String = "",
    @ColumnInfo(defaultValue = "") val cleaningNote: String = "",
    @ColumnInfo(defaultValue = "PENDING") val reviewStatus: String = "PENDING"
)
