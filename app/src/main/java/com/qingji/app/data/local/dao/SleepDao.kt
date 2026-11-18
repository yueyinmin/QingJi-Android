package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qingji.app.data.local.entity.SleepRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 睡眠记录 DAO（健康体系 V1.1）。
 * 只做增删改查 + 按日期区间统计，不做任何业务计算（计算归 SleepRepository / domain）。
 */
@Dao
interface SleepDao {

    /** 某一天的睡眠（按归属日期 = 起床日期）。 */
    @Query("SELECT * FROM sleep_record WHERE date = :date ORDER BY startTime DESC")
    fun observeByDate(date: String): Flow<List<SleepRecordEntity>>

    /** 日期区间（含两端），周 / 月趋势和日历联动用。 */
    @Query("SELECT * FROM sleep_record WHERE date >= :from AND date <= :to ORDER BY date DESC, startTime DESC")
    fun observeRange(from: String, to: String): Flow<List<SleepRecordEntity>>

    /** 全部记录（历史补录页 / 汇总重建用），新到旧。 */
    @Query("SELECT * FROM sleep_record ORDER BY date DESC, startTime DESC")
    fun observeAll(): Flow<List<SleepRecordEntity>>

    /** 同步取区间（补算 / 迁移校验用，不订阅）。 */
    @Query("SELECT * FROM sleep_record WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun findRange(from: String, to: String): List<SleepRecordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: SleepRecordEntity): Long

    @Update
    suspend fun update(record: SleepRecordEntity)

    @Query("DELETE FROM sleep_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 按外部来源删（设备重新同步前清旧数据用，手动记录不受影响）。 */
    @Query("DELETE FROM sleep_record WHERE sourceType = :sourceType AND externalId = :externalId")
    suspend fun deleteByExternal(sourceType: String, externalId: String)

    @Query("SELECT COUNT(*) FROM sleep_record")
    suspend fun count(): Int
}
