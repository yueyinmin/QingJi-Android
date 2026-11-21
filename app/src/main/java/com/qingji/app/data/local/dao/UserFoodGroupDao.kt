package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.qingji.app.data.local.entity.UserFoodGroup
import com.qingji.app.data.local.entity.UserFoodGroupItem
import kotlinx.coroutines.flow.Flow

/**
 * 自定义食物组 DAO（v1.6 食物库整体重构）。
 * 只碰 user_food_group / user_food_group_item 两张新表，其余表一行不动。
 */
@Dao
interface UserFoodGroupDao {

    // ---------- 组 ----------
    /** 按排序序、再按创建时间，界面直接用。 */
    @Query("SELECT * FROM user_food_group ORDER BY sortOrder ASC, createdAt ASC")
    fun observeGroups(): Flow<List<UserFoodGroup>>

    @Query("SELECT * FROM user_food_group ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun allGroups(): List<UserFoodGroup>

    @Query("SELECT * FROM user_food_group WHERE id = :id LIMIT 1")
    suspend fun groupById(id: Long): UserFoodGroup?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: UserFoodGroup): Long

    @Update
    suspend fun updateGroup(group: UserFoodGroup)

    @Query("DELETE FROM user_food_group WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    /** 重建排序：批量写 sortOrder。 */
    @Query("UPDATE user_food_group SET sortOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun updateGroupOrder(id: Long, order: Int, now: Long)

    // ---------- 组成员（多对多） ----------
    /** 组内食物 id 列表（按加入时间）。 */
    @Query("SELECT foodId FROM user_food_group_item WHERE groupId = :groupId ORDER BY createdAt ASC")
    fun observeFoodIdsInGroup(groupId: Long): Flow<List<Long>>

    @Query("SELECT foodId FROM user_food_group_item WHERE groupId = :groupId ORDER BY createdAt ASC")
    suspend fun foodIdsInGroup(groupId: Long): List<Long>

    /** 某组里有多少个食物（界面显示数量用）。 */
    @Query("SELECT COUNT(*) FROM user_food_group_item WHERE groupId = :groupId")
    fun observeGroupCount(groupId: Long): Flow<Int>

    /** 每个食物被加进了几个组（食物卡片上显示「已归入 N 组」）。 */
    @Query("SELECT groupId FROM user_food_group_item WHERE foodId = :foodId")
    fun observeGroupsOfFood(foodId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: UserFoodGroupItem): Long

    /** 从某组移除一个食物，不影响食物本身。 */
    @Query("DELETE FROM user_food_group_item WHERE groupId = :groupId AND foodId = :foodId")
    suspend fun removeItem(groupId: Long, foodId: Long)

    /** 删组：先清关联再删组，一个事务里完成，避免孤立关联行。 */
    @Transaction
    suspend fun deleteGroupCascade(groupId: Long) {
        clearItemsOfGroup(groupId)
        deleteGroupById(groupId)
    }

    @Query("DELETE FROM user_food_group_item WHERE groupId = :groupId")
    suspend fun clearItemsOfGroup(groupId: Long)

    /** 删自定义食物时清掉它在所有组里的关联（工单 TASK-07：删除食物清理关联记录）。 */
    @Query("DELETE FROM user_food_group_item WHERE foodId = :foodId")
    suspend fun clearItemsOfFood(foodId: Long)

    // ---------- 备份专用 ----------
    @Query("SELECT * FROM user_food_group")
    suspend fun backupGroups(): List<UserFoodGroup>

    @Query("SELECT * FROM user_food_group_item")
    suspend fun backupItems(): List<UserFoodGroupItem>

    @Query("DELETE FROM user_food_group")
    suspend fun clearAllGroups()

    @Query("DELETE FROM user_food_group_item")
    suspend fun clearAllItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGroups(items: List<UserFoodGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<UserFoodGroupItem>)
}
