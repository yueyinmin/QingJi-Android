package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.0 · P0 两张新表（Room 12 → 13）
 * ------------------------------------------------------------
 * 规矩沿用训练中心六张表那套（血泪教训原文照抄）：
 * 1. 全部是全新表，MIGRATION_12_13 只有 CREATE TABLE + 种子数据，一行旧数据都不碰；
 * 2. 每个非主键列都写 @ColumnInfo(defaultValue = ...)，
 *    迁移 SQL 里的 DEFAULT 必须和这里逐字一致，否则 Room 打开时校验不过；
 * 3. 日期统一用 LocalDate，AppDatabase 已有的 Converters 负责转成 "2026-11-17"。
 *
 * 消耗账本是「可重算的缓存」：删掉也能由 workout_session + exercise_log
 * + exercise_calorie_config 重新补算出来（HealthRepository.ensureLedgerBackfill），
 * 所以不进备份文件，备份 schema 保持稳定。
 * ============================================================
 */

/** 表 29：运动热量参数库。MET 三档按运动类型预置，以后支持按动作 exerciseId 覆盖。 */
@Entity(
    tableName = "exercise_calorie_config",
    indices = [Index("activityType"), Index("exerciseId")]
)
data class ExerciseCalorieConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 动作库 UUID；空串 = 类型级默认参数（对所有该类型动作生效） */
    @ColumnInfo(defaultValue = "") val exerciseId: String = "",
    /**
     * 运动类型：strength_light / strength_moderate / strength_heavy / bodyweight_circuit /
     * hiit / flexibility / walking_stroll / walking_brisk / jogging / running / cycling / swimming
     */
    val activityType: String,
    @ColumnInfo(defaultValue = "0.0") val metLow: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val metModerate: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val metHigh: Double = 0.0,
    /** 数据来源说明（如 Compendium of Physical Activities 近似值） */
    @ColumnInfo(defaultValue = "") val source: String = "",
    /** 参数版本，改参数时 +1 */
    @ColumnInfo(defaultValue = "") val version: String = "",
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true
)

/** 表 30：统一运动消耗账本。一场训练 / 一条运动记录 = 一行，按来源幂等，绝不重复记。 */
@Entity(
    tableName = "exercise_session_calorie",
    indices = [Index("date"), Index(value = ["sourceType", "sourceId"], unique = true)]
)
data class ExerciseSessionCalorieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * 来源类型：
     * STRENGTH_TRAINING（训练中心场次）/ CARDIO（有氧）/ WALKING（步行）/
     * MANUAL_EXERCISE（手动/其他）/ DEVICE_IMPORTED（设备导入，预留）
     */
    val sourceType: String,
    /**
     * 来源 ID：训练场次 = workout_session.uuid；
     * 独立运动记录 = "exlog:<exercise_log.id>"。唯一索引靠它去重。
     */
    val sourceId: String,
    /** 归属日期（运动发生当天，与 ExerciseLog.date / WorkoutSession.date 同口径） */
    val date: LocalDate,
    @ColumnInfo(defaultValue = "0") val durationSeconds: Int = 0,
    /** 总消耗（现有口径是毛消耗，MET 公式本身含基础代谢部分；补偿折算在额度层做） */
    @ColumnInfo(defaultValue = "0.0") val caloriesGross: Double = 0.0,
    /** 计算时用的体重快照（kg）；0 = 未记录 */
    @ColumnInfo(defaultValue = "0.0") val weightSnapshot: Double = 0.0,
    /**
     * 计算方式：met_segmented / estimation_history / estimation_history_duration /
     * user_direct / exercise_log（值见 SessionCalorieCalculator.METHOD_*）
     */
    @ColumnInfo(defaultValue = "") val calculationMethod: String = "",
    /** 计算口径版本（SessionCalorieCalculator.VERSION） */
    @ColumnInfo(defaultValue = "") val calculationVersion: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0
)
