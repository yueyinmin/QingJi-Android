package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * 标准食物库（只读）—— food_catalog.db 里唯一的一张表
 * ------------------------------------------------------------
 * 和 AppDatabase 完全分开：标准食物随 APK 预置在 assets 里，
 * 用户自己录的食物、饮食记录、体重都放在另一个数据库文件。
 * 好处：以后把食物库从 654 条扩到一万条，只要换一个预置库文件，
 *      用户记过的任何一条数据都不会被碰。
 *
 * 列名刻意用下划线，和 tools/food_import/ 产出的 CSV 列一一对应，
 * 这样导出脚本可以直接按列名灌数据，不用再维护一份字段映射。
 *
 * 营养数值全部按「每 100g 可食部分」存（basis_grams 恒为 100）。
 * fiber / sodium / sugar / calcium 目前是 0：清单里没有这几列数据，
 * 0 在这里的含义是「暂无数据」，不是「含量为 0」，界面不要当真值展示。
 * ============================================================
 */
@Entity(
    tableName = "food_catalog",
    indices = [
        Index("name"),
        Index("pinyin"),
        Index("pinyin_full"),
        Index("category")
    ]
)
data class CatalogFood(
    /** 100001 起。用户自建食物的 id 从 1 开始，两边永远不会撞号。 */
    @PrimaryKey
    @ColumnInfo(name = "food_id") val foodId: Long,

    @ColumnInfo(name = "name") val name: String,
    /** 多个别名用 | 分隔；目前只放「去掉括号后的名字」，没有编造叫法 */
    @ColumnInfo(name = "aliases") val aliases: String = "",
    /** 拼音首字母，如 mf */
    @ColumnInfo(name = "pinyin") val pinyin: String = "",
    /** 拼音全拼，如 mifan。机器生成，只用于检索，不展示给用户 */
    @ColumnInfo(name = "pinyin_full") val pinyinFull: String = "",
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "subcategory") val subcategory: String = "",
    /** raw / cooked / steamed / boiled / fried / dried / processed / unknown */
    @ColumnInfo(name = "state") val state: String = "unknown",

    @ColumnInfo(name = "energy_kcal") val energyKcal: Double,
    @ColumnInfo(name = "protein") val protein: Double,
    @ColumnInfo(name = "fat") val fat: Double,
    @ColumnInfo(name = "carbs") val carbs: Double,
    @ColumnInfo(name = "fiber") val fiber: Double = 0.0,
    @ColumnInfo(name = "sodium") val sodium: Double = 0.0,
    @ColumnInfo(name = "sugar") val sugar: Double = 0.0,
    @ColumnInfo(name = "calcium") val calcium: Double = 0.0,

    /** 文档规定营养基准必须是 100g，写进表里方便以后核对 */
    @ColumnInfo(name = "basis_grams") val basisGrams: Double = 100.0,
    /** 点一下食物自动带出来的默认份量（克） */
    @ColumnInfo(name = "default_grams") val defaultGrams: Double = 100.0,
    @ColumnInfo(name = "portion_note") val portionNote: String = "",

    /** 来源编号，可追溯；当前全部是 CNFCT-APPROX（《中国食物成分表》近似值） */
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_note") val sourceNote: String = "",
    /** 0 未审核 / 1 已审核。当前 654 条都是初采估算，全部 0，不冒充已核对数据 */
    @ColumnInfo(name = "audit_status") val auditStatus: Int = 0,
    @ColumnInfo(name = "catalog_version") val catalogVersion: String = "0.2"
)

/** 界面层统一用的食物模型。放在这里是为了让 import 路径保持不变，界面一行都不用改。 */
fun CatalogFood.toFood(favorite: Boolean = false): com.qingji.app.data.local.entity.Food =
    com.qingji.app.data.local.entity.Food(
        id = foodId,
        name = name,
        pinyin = pinyin,
        pinyinFull = pinyinFull,
        aliases = aliases,
        kcalPer100g = energyKcal,
        proteinPer100g = protein,
        carbPer100g = carbs,
        fatPer100g = fat,
        fiberPer100g = fiber,
        sodiumMgPer100g = sodium,
        calciumMgPer100g = calcium,
        sugarPer100g = sugar,
        defaultGrams = defaultGrams,
        source = "preset",
        category = category,
        subcategory = subcategory,
        state = state,
        portionNote = portionNote,
        sourceId = sourceId,
        auditStatus = auditStatus,
        catalogVersion = catalogVersion,
        favorite = favorite,
        createdAt = 0L
    )
