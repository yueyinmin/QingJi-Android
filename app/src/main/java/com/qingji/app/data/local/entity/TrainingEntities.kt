package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 训练中心的六张表（v1.0.9 → Room 10）
 * ------------------------------------------------------------
 * 规矩（照 AppDatabase 里那段「动表结构四件套」的血泪教训）：
 * 1. 全部是全新表，MIGRATION_9_10 只有 CREATE TABLE，一行旧数据都不碰；
 * 2. 每个非主键列都写 @ColumnInfo(defaultValue = ...)，
 *    迁移 SQL 里的 DEFAULT 必须和这里逐字一致，否则 Room 打开时校验不过；
 * 3. 日期统一用 LocalDate，AppDatabase 已有的 Converters 负责转成 "2026-11-13"；
 * 4. 重量一律以 kg 存基准值，lb 只是显示层换算，不落库。
 * ============================================================
 */

/** 表 16：动作库。内置的（isBuiltIn=true）不允许用户删，自建的可以删。 */
@Entity(
    tableName = "exercise",
    indices = [Index("category"), Index(value = ["name"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    @ColumnInfo(defaultValue = "") val category: String = "",          // chest/back/shoulder/arm/leg/core/stretch
    @ColumnInfo(defaultValue = "") val primaryMuscles: String = "",
    @ColumnInfo(defaultValue = "") val secondaryMuscles: String = "",
    @ColumnInfo(defaultValue = "") val equipment: String = "",
    @ColumnInfo(defaultValue = "") val steps: String = "",             // \n 分隔
    @ColumnInfo(defaultValue = "") val commonMistakes: String = "",
    @ColumnInfo(defaultValue = "0") val isBuiltIn: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)

/** 表 17：训练计划。P0 只建表不启用，P1 才接模板与自定义计划。 */
@Entity(tableName = "training_plan")
data class TrainingPlan(
    @PrimaryKey val uuid: String,
    val name: String,
    @ColumnInfo(defaultValue = "") val source: String = "custom",       // template:xxx / custom
    @ColumnInfo(defaultValue = "0") val isActive: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
)

/** 表 18：计划日（周循环，weekday 1=周一 … 7=周日）。 */
@Entity(tableName = "training_plan_day", indices = [Index("planUuid")])
data class TrainingPlanDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val planUuid: String = "",
    @ColumnInfo(defaultValue = "0") val weekday: Int = 1,
    @ColumnInfo(defaultValue = "") val title: String = "",
    @ColumnInfo(defaultValue = "0") val isRest: Boolean = false
)

/** 表 19：计划日里的动作安排。名字存快照，改动作库不会改掉历史。 */
@Entity(tableName = "plan_exercise", indices = [Index("dayId")])
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val dayId: Long = 0,
    @ColumnInfo(defaultValue = "") val exerciseUuid: String = "",
    @ColumnInfo(defaultValue = "") val exerciseNameSnapshot: String = "",
    @ColumnInfo(defaultValue = "0") val targetSets: Int = 0,
    @ColumnInfo(defaultValue = "0") val targetReps: Int = 0,
    @ColumnInfo(defaultValue = "0.0") val targetWeightKg: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val orderInDay: Int = 0
)

/** 表 20：一次训练会话。只有「保存成功」才会有行，进行中的草稿另存 SharedPreferences。 */
@Entity(tableName = "workout_session", indices = [Index("date")])
data class WorkoutSession(
    @PrimaryKey val uuid: String,
    val date: LocalDate,
    @ColumnInfo(defaultValue = "0") val startedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val endedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val durationMinutes: Int = 0,
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(defaultValue = "manual") val source: String = "manual",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
)

/**
 * 表 21：会话里的一个动作 + 它下面所有的组。
 *
 * 组数据为什么不是独立表：P0 的查询全是「按条目取整包」，
 * 把每组打包成一条 JSON（[{w:12.5,r:10,rpe:8,rest:90}]）最省事，
 * 备份、同步也跟着简单；等真有「按组分析」需求再拆表。
 * 名字存快照：以后动作库改名、甚至删掉自建动作，历史记录照常显示。
 */
@Entity(tableName = "workout_exercise", indices = [Index("sessionUuid"), Index("exerciseUuid")])
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val sessionUuid: String = "",
    @ColumnInfo(defaultValue = "") val exerciseUuid: String = "",       // 空串 = 自建动作已被删
    @ColumnInfo(defaultValue = "") val exerciseNameSnapshot: String = "",
    @ColumnInfo(defaultValue = "0") val orderInSession: Int = 0,
    @ColumnInfo(defaultValue = "") val setsJson: String = "",
    @ColumnInfo(defaultValue = "") val note: String = ""
)
