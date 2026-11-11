package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * 用户库里和「食物」有关的三张表（v1.0.4 拆库后新增）
 * ------------------------------------------------------------
 * 标准食物全部搬去了只读的 food_catalog.db，这里只留用户自己的东西：
 *   ① user_food            用户手录 / AI 新增 / 扫码查到的食物
 *   ② food_favorite        收藏（按名字记，标准库和用户库通用）
 *   ③ food_search_history  最近搜过的词，用来做搜索页的快捷入口
 *
 * user_food 的字段刻意和旧版 food 表一模一样，MIGRATION_5_6 可以整行搬，
 * 不会丢任何一条用户自建数据。
 * ============================================================
 */
@Entity(
    tableName = "user_food",
    indices = [Index("name"), Index("barcode")]
)
data class UserFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinyin: String = "",
    val barcode: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double = 0.0,
    val carbPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val fiberPer100g: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val sodiumMgPer100g: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val calciumMgPer100g: Double = 0.0,
    val defaultGrams: Double = 100.0,
    /** user / api / openfoodfacts；不允许写成 preset，preset 只存在于只读标准库 */
    val source: String = "user",
    val category: String = "其他",
    val brand: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 收藏表。主键用名字而不是 id：
 * 标准库的 id 是随预置库一起发的，以后扩库会变；名字才稳定。
 * 用户自建食物同名时也一起命中，符合「收藏的是这个食物」的直觉。
 */
@Entity(tableName = "food_favorite")
data class FoodFavorite(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** 搜索历史。keyword 加唯一索引，重复搜同一个词只更新时间、不新增行。 */
@Entity(
    tableName = "food_search_history",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class FoodSearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val lastUsedAt: Long = System.currentTimeMillis()
)

/** 转成界面层统一的 Food；favorite 会被 Repository 的收藏表结果再覆盖一次。 */
fun UserFood.toFood(favorite: Boolean = this.favorite): Food =
    Food(
        id = id,
        name = name,
        pinyin = pinyin,
        barcode = barcode,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbPer100g = carbPer100g,
        fatPer100g = fatPer100g,
        fiberPer100g = fiberPer100g,
        sodiumMgPer100g = sodiumMgPer100g,
        calciumMgPer100g = calciumMgPer100g,
        defaultGrams = defaultGrams,
        source = source,
        category = category,
        brand = brand,
        favorite = favorite,
        createdAt = createdAt,
        catalogVersion = ""
    )

/** 界面层的 Food 写回用户库（备份导入、扫码缓存用）。 */
fun Food.toUserFood(): UserFood =
    UserFood(
        // 标准库的 id 从 100001 起，不能带进用户库；让它自增重新分配
        id = if (id >= 100001L) 0 else id,
        name = name,
        pinyin = pinyin,
        barcode = barcode,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbPer100g = carbPer100g,
        fatPer100g = fatPer100g,
        // 用户库这四列还是非空 0.0（这一版没动用户表结构），标准库里「未收录」的 null
        // 落到用户库只能变 0；界面上用户食物按「自己录的数据」处理，不标未收录
        fiberPer100g = fiberPer100g ?: 0.0,
        sodiumMgPer100g = sodiumMgPer100g ?: 0.0,
        calciumMgPer100g = calciumMgPer100g ?: 0.0,
        defaultGrams = defaultGrams,
        // 标准库那条绝不能写进用户库；万一传进来也归为 user
        source = if (source == "preset") "user" else source,
        category = category,
        brand = brand,
        favorite = favorite,
        createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt
    )
