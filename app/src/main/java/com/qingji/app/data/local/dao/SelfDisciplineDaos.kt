package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplineInterventionEntity
import com.qingji.app.data.local.entity.SelfDisciplineMilestoneEntity
import com.qingji.app.data.local.entity.SelfDisciplinePeriodEntity
import com.qingji.app.data.local.entity.SelfDisciplineSettingEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** 自律管理：目标配置 DAO（SD-013）。 */
@Dao
interface SelfDisciplineGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SelfDisciplineGoalEntity): Long

    @Update
    suspend fun update(goal: SelfDisciplineGoalEntity)

    @Query("DELETE FROM self_discipline_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE self_discipline_goals SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM self_discipline_goals ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SelfDisciplineGoalEntity>>

    @Query("SELECT * FROM self_discipline_goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SelfDisciplineGoalEntity?

    /** 首页主要目标：启用中的第一个。 */
    @Query("SELECT * FROM self_discipline_goals WHERE isEnabled = 1 ORDER BY createdAt ASC LIMIT 1")
    suspend fun getDefaultEnabled(): SelfDisciplineGoalEntity?

    /** 删除目标前先数一下它名下的记录数（用于二次确认文案）。 */
    @Query("SELECT COUNT(*) FROM self_discipline_periods WHERE goalId = :goalId")
    suspend fun countPeriods(goalId: Long): Int
}

/** 自律管理：周期记录 DAO。 */
@Dao
interface SelfDisciplinePeriodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(period: SelfDisciplinePeriodEntity): Long

    @Query("SELECT * FROM self_discipline_periods WHERE goalId = :goalId ORDER BY startAt ASC")
    fun observeByGoal(goalId: Long): Flow<List<SelfDisciplinePeriodEntity>>

    @Query("SELECT * FROM self_discipline_periods WHERE goalId = :goalId AND status = 'active' LIMIT 1")
    suspend fun getActive(goalId: Long): SelfDisciplinePeriodEntity?

    /** 结束周期：写结束日、原因、状态。 */
    @Query(
        "UPDATE self_discipline_periods " +
            "SET endAt = :endAt, endReason = :reason, status = 'ended' " +
            "WHERE id = :id"
    )
    suspend fun endPeriod(id: Long, endAt: LocalDate, reason: String)

    @Query("SELECT * FROM self_discipline_periods WHERE goalId = :goalId")
    suspend fun findAllByGoal(goalId: Long): List<SelfDisciplinePeriodEntity>

    /** 删目标时级联清掉它名下的周期。 */
    @Query("DELETE FROM self_discipline_periods WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)

    /** 撤销偏离：把已结束的周期重新打开（清 endAt / endReason、状态改回 active）。 */
    @Query("UPDATE self_discipline_periods SET endAt = NULL, endReason = NULL, status = 'active' WHERE id = :id")
    suspend fun reopen(id: Long)
}

/** 自律管理：每日打卡 DAO。 */
@Dao
interface SelfDisciplineCheckinDao {
    /** goalId + localDate 唯一，REPLACE 即「同天覆盖」= 编辑。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkin: SelfDisciplineCheckinEntity): Long

    @Update
    suspend fun update(checkin: SelfDisciplineCheckinEntity)

    @Query("SELECT * FROM self_discipline_checkins WHERE goalId = :goalId ORDER BY localDate DESC")
    fun observeByGoal(goalId: Long): Flow<List<SelfDisciplineCheckinEntity>>

    @Query("SELECT * FROM self_discipline_checkins WHERE goalId = :goalId AND localDate = :date LIMIT 1")
    suspend fun getByDate(goalId: Long, date: LocalDate): SelfDisciplineCheckinEntity?

    @Query(
        "SELECT * FROM self_discipline_checkins " +
            "WHERE goalId = :goalId AND localDate BETWEEN :from AND :to ORDER BY localDate ASC"
    )
    fun observeRange(goalId: Long, from: LocalDate, to: LocalDate): Flow<List<SelfDisciplineCheckinEntity>>

    @Query("SELECT * FROM self_discipline_checkins")
    suspend fun findAll(): List<SelfDisciplineCheckinEntity>

    @Query("DELETE FROM self_discipline_checkins WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM self_discipline_checkins WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)
}

/** 自律管理：冲动记录 DAO。 */
@Dao
interface SelfDisciplineUrgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(urge: SelfDisciplineUrgeEntity): Long

    @Query("SELECT * FROM self_discipline_urge_logs WHERE goalId = :goalId ORDER BY occurredAt DESC")
    fun observeByGoal(goalId: Long): Flow<List<SelfDisciplineUrgeEntity>>

    @Query("SELECT * FROM self_discipline_urge_logs WHERE goalId = :goalId")
    suspend fun findAllByGoal(goalId: Long): List<SelfDisciplineUrgeEntity>

    @Query("DELETE FROM self_discipline_urge_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM self_discipline_urge_logs WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)
}

/** 自律管理：模块设置 DAO（key-value）。 */
@Dao
interface SelfDisciplineSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SelfDisciplineSettingEntity): Long

    @Query("SELECT * FROM self_discipline_settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SelfDisciplineSettingEntity?

    @Query("SELECT * FROM self_discipline_settings")
    suspend fun getAll(): List<SelfDisciplineSettingEntity>
}

/** 自律管理：里程碑 DAO（P1 预留表，先建好）。 */
@Dao
interface SelfDisciplineMilestoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: SelfDisciplineMilestoneEntity): Long

    @Query("SELECT * FROM self_discipline_milestones WHERE goalId = :goalId")
    fun observeByGoal(goalId: Long): Flow<List<SelfDisciplineMilestoneEntity>>

    @Query("DELETE FROM self_discipline_milestones WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)
}

/** 自律管理：应对工具记录 DAO（P1 预留表，先建好）。 */
@Dao
interface SelfDisciplineInterventionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(intervention: SelfDisciplineInterventionEntity): Long

    @Query("SELECT * FROM self_discipline_interventions WHERE goalId = :goalId")
    fun observeByGoal(goalId: Long): Flow<List<SelfDisciplineInterventionEntity>>

    @Query("DELETE FROM self_discipline_interventions WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Long)
}
