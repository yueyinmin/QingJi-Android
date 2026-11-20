package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingji.app.data.local.entity.DailyHealthSummaryEntity

/** 每日健康汇总缓存 DAO（工单 10.2）。聚合口径由 DailyHealthAggregator 负责，这里只存取。 */
@Dao
interface DailyHealthSummaryDao {

    @Query("SELECT * FROM daily_health_summary WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): DailyHealthSummaryEntity?

    /** 全部缓存行（统计页按区间取，避免逐日查询）。 */
    @Query("SELECT * FROM daily_health_summary WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun findRange(from: String, to: String): List<DailyHealthSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: DailyHealthSummaryEntity)

    @Query("DELETE FROM daily_health_summary WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM daily_health_summary")
    suspend fun clearAll()
}
