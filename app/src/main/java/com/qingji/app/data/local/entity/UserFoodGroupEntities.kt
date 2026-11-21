package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * 自定义食物组（v1.6 食物库整体重构 · Room 18→19）
 * ------------------------------------------------------------
 * 用户把自己录的自定义食物（user_food）分门别类装进「组」里，
 * 一个食物可以同时属于多个组（多对多），删组不删食物、只清关联。
 *
 * 为什么放在用户库 qingji.db：和标准库 food_catalog.db 完全隔离，
 * 标准库升级删库重拷不会冲掉用户建的组。
 *
 * 四件套纪律：每个新列都写 @ColumnInfo(defaultValue=...)，
 * 且默认值与 MIGRATION_18_19 的建表 SQL 逐字一致，否则 Room schema 校验不过、真机打不开。
 *
 * 历史饮食记录 food_log 是快照式（存名字 + 当时营养数字），
 * 所以改组、删组、移出组都不会让历史统计漂移——这条红线不动。
 * ============================================================
 */

/** 一个自定义食物分组。组名唯一索引，重名不给建第二条。 */
@Entity(
    tableName = "user_food_group",
    indices = [Index(value = ["name"], unique = true)]
)
data class UserFoodGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 可选封面：存应用私有目录下的文件 URI 字符串，避免临时 URI 失效。空串=无封面。 */
    @ColumnInfo(defaultValue = "") val coverUri: String = "",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 组与自定义食物的关联（多对多中间表）。
 * (groupId, foodId) 唯一索引：同一食物在同一组里只会出现一次；
 * 另外单独给 groupId 建索引，按组查成员走索引。
 */
@Entity(
    tableName = "user_food_group_item",
    indices = [
        Index(value = ["groupId", "foodId"], unique = true),
        Index(value = ["foodId"])
    ]
)
data class UserFoodGroupItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属组 id（对应 UserFoodGroup.id）。删组时按此列清关联。 */
    val groupId: Long,
    /** 组内食物 id（对应 UserFood.id，自定义食物）。删食物时按此列清关联。 */
    val foodId: Long,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis()
)
