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
 *
 * v1.0.6：fiber / sodium / sugar / calcium 四列改成可空。
 *   以前它们是非空 0.0，靠注释说明「0 表示暂无数据」——但数据库里 0 就是 0，
 *   界面分不出「这条食物不含纤维」和「这条食物没录纤维」，只能两件事一起装死。
 *   现在 NULL = 未收录，0 = 真的测得是 0，界面上前者显示「—」。
 *
 * v1.0.6：删掉 state（生/熟/干）列。
 *   654 条里 631 条是 unknown（96.5%），只有 23 条有值，且这 23 条的值是从
 *   菜名括号里猜的。留着一列 96.5% 空的枚举，以后每个人都要问「这个字段到底有没有用」，
 *   不如删掉。真要做生熟区分时，应该做成「同一道菜两条记录」（米饭/蒸米饭），
 *   而不是一列 96.5% 未知的属性。
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

    @ColumnInfo(name = "energy_kcal") val energyKcal: Double,
    @ColumnInfo(name = "protein") val protein: Double,
    @ColumnInfo(name = "fat") val fat: Double,
    @ColumnInfo(name = "carbs") val carbs: Double,
    /** NULL = 未收录（清单里本来就没这一列数据）；0.0 = 确实测得为 0 */
    @ColumnInfo(name = "fiber") val fiber: Double? = null,
    @ColumnInfo(name = "sodium") val sodium: Double? = null,
    @ColumnInfo(name = "sugar") val sugar: Double? = null,
    @ColumnInfo(name = "calcium") val calcium: Double? = null,

    /** 文档规定营养基准必须是 100g，写进表里方便以后核对 */
    @ColumnInfo(name = "basis_grams") val basisGrams: Double = 100.0,
    /** 点一下食物自动带出来的默认份量（克） */
    @ColumnInfo(name = "default_grams") val defaultGrams: Double = 100.0,
    @ColumnInfo(name = "portion_note") val portionNote: String = "",

    /** 来源编号，可追溯；当前全部是 CNFCT-APPROX（《中国食物成分表》近似值） */
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "source_note") val sourceNote: String = "",
    /** 0 未审核 / 1 已核对 / 2 有权威出处，见 AuditStatus */
    @ColumnInfo(name = "audit_status") val auditStatus: Int = AuditStatus.UNCHECKED,
    @ColumnInfo(name = "catalog_version") val catalogVersion: String = "0.3"
)

/**
 * 审核状态分级（v1.0.6 起）。
 *
 * 以前只有「0 未审核 / 1 已审核」两档，而 654 条全是 0，等于这一列没有信息量。
 * 现在拆三档，是给以后补数据留的位置：
 *   · 灌进来的值如果是按菜系/品类套用的近似值 → UNCHECKED
 *   · 逐条对过《中国食物成分表》 → CHECKED
 *   · 有明确到具体版本的出处编号 → SOURCED
 * 当前 654 条仍然全部是 UNCHECKED，界面上会明说「估算值，未核对」。
 */
object AuditStatus {
    const val UNCHECKED = 0
    const val CHECKED = 1
    const val SOURCED = 2

    /** 界面上一句话说明这一条的可信度。 */
    fun label(status: Int): String = when (status) {
        CHECKED -> "已核对"
        SOURCED -> "有出处"
        else -> "估算，未核对"
    }
}

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
        portionNote = portionNote,
        sourceId = sourceId,
        auditStatus = auditStatus,
        catalogVersion = catalogVersion,
        favorite = favorite,
        createdAt = 0L
    )
