package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingji.app.data.local.entity.ExerciseCalorieConfigEntity
import com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 健康体系 V1.0 · P0 数据入口。只读配置、只写账本；界面层不许直接碰这两个 DAO。
 */

@Dao
interface ExerciseCalorieConfigDao {

    /** 类型级默认参数（exerciseId 为空串的行）。 */
    @Query("SELECT * FROM exercise_calorie_config WHERE exerciseId = '' AND isEnabled = 1")
    suspend fun findTypeDefaults(): List<ExerciseCalorieConfigEntity>

    /** 按动作覆盖参数（有就用它，没有回退类型默认）。 */
    @Query("SELECT * FROM exercise_calorie_config WHERE exerciseId = :exerciseId AND isEnabled = 1 LIMIT 1")
    suspend fun findByExerciseId(exerciseId: String): ExerciseCalorieConfigEntity?

    @Query("SELECT COUNT(*) FROM exercise_calorie_config WHERE exerciseId = ''")
    suspend fun typeDefaultCount(): Int

    /** 种子导入：主键冲突时跳过，保证幂等可重跑。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(items: List<ExerciseCalorieConfigEntity>)
}

/** 每日消耗合计（SUM 一天一条，可空 = 当天没有账本记录）。 */
data class DailyExerciseCalories(
    val date: LocalDate,
    val totalGross: Double?
)

@Dao
interface ExerciseSessionCalorieDao {

    /** 当天全部账本记录（首页消耗卡想看明细时用）。 */
    @Query("SELECT * FROM exercise_session_calorie WHERE date = :date ORDER BY id ASC")
    fun observeByDate(date: LocalDate): Flow<List<ExerciseSessionCalorieEntity>>

    /** 当天总消耗（首页卡片/额度联动的主数据源）。 */
    @Query("SELECT SUM(caloriesGross) FROM exercise_session_calorie WHERE date = :date")
    fun observeDailyTotal(date: LocalDate): Flow<Double?>

    /** 区间逐日合计（周小结 / 以后月统计用），没有记录的日期不在结果里。 */
    @Query(
        """
        SELECT date AS date, SUM(caloriesGross) AS totalGross
        FROM exercise_session_calorie WHERE date BETWEEN :from AND :to
        GROUP BY date ORDER BY date ASC
        """
    )
    fun observeRangeTotals(from: LocalDate, to: LocalDate): Flow<List<DailyExerciseCalories>>

    @Query("SELECT * FROM exercise_session_calorie WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(sourceType: String, sourceId: String): ExerciseSessionCalorieEntity?

    @Query("SELECT COUNT(*) FROM exercise_session_calorie WHERE sourceType = :sourceType")
    suspend fun countByType(sourceType: String): Int

    @Query("SELECT sourceId FROM exercise_session_calorie WHERE sourceType = :sourceType")
    suspend fun findSourceIds(sourceType: String): List<String>

    /** 健康体系 V1.1：当日消耗账本按来源聚合（每来源条数+毛热量和），供每日汇总共用。 */
    @Query(
        "SELECT sourceType AS sourceType, COUNT(*) AS count, " +
        "COALESCE(SUM(caloriesGross),0.0) AS kcalSum " +
        "FROM exercise_session_calorie WHERE date = :date GROUP BY sourceType"
    )
    suspend fun dayAggregate(date: String): List<LedgerDayAgg>

    @Query("SELECT * FROM exercise_session_calorie ORDER BY date ASC")
    suspend fun findAll(): List<ExerciseSessionCalorieEntity>

    /**
     * 幂等写入：唯一索引 (sourceType, sourceId) 冲突时整行替换。
     * 训练保存/编辑重算、镜像同步都走这里，同一场永远只有一行。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ExerciseSessionCalorieEntity)

    /** 历史补算用：已存在的行不动（不覆盖人工结算结果）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(item: ExerciseSessionCalorieEntity)

    @Query("DELETE FROM exercise_session_calorie WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteBySource(sourceType: String, sourceId: String)

    /** 删独立运动记录时联动删镜像：来源类型可能是 CARDIO/WALKING/MANUAL，按 sourceId 前缀匹配删。 */
    @Query(
        """
        DELETE FROM exercise_session_calorie
        WHERE sourceId = :sourceId
          AND sourceType IN ('CARDIO','WALKING','MANUAL_EXERCISE','DEVICE_IMPORTED')
        """
    )
    suspend fun deleteExerciseLogMirrorBySourceId(sourceId: String)

    /** 镜像自愈：来源记录已经不存在了（备份恢复/清记录），对应账本行一起删。 */
    @Query(
        """
        DELETE FROM exercise_session_calorie
        WHERE sourceType IN ('CARDIO','WALKING','MANUAL_EXERCISE','DEVICE_IMPORTED')
          AND sourceId LIKE 'exlog:%'
          AND CAST(substr(sourceId, 7) AS INTEGER) NOT IN (SELECT id FROM exercise_log)
        """
    )
    suspend fun deleteOrphanExerciseLogMirrors()

    /** 强度账本自愈：场次已经不存在了（备份恢复），对应账本行一起删。 */
    @Query(
        """
        DELETE FROM exercise_session_calorie
        WHERE sourceType = 'STRENGTH_TRAINING'
          AND sourceId NOT IN (SELECT uuid FROM workout_session)
        """
    )
    suspend fun deleteOrphanStrengthRows()
}

/** 每日汇总：消耗账本按来源当日聚合（健康体系 V1.1）。 */
data class LedgerDayAgg(
    val sourceType: String,
    val count: Int,
    val kcalSum: Double
)
