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
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
    // ---- Room 12：Workout Guide 302 条动作库新增元数据 ----
    @ColumnInfo(defaultValue = "") val englishName: String = "",
    @ColumnInfo(defaultValue = "") val slug: String = "",
    @ColumnInfo(defaultValue = "") val exerciseType: String = "",
    @ColumnInfo(defaultValue = "0") val isStretch: Boolean = false,
    @ColumnInfo(defaultValue = "") val assetPath: String = "",
    @ColumnInfo(defaultValue = "") val attribution: String = ""
)

/**
 * 动作库版本标记。只存一行：key = workout_guide_library_version。
 * 目的：避免每次启动都扫描 302 条做无意义写入；版本一致且库完整时直接跳过。
 */
@Entity(tableName = "library_meta")
data class LibraryMeta(
    @PrimaryKey val key: String,
    @ColumnInfo(defaultValue = "") val value: String = ""
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
    /**
     * 数据来源：MANUAL（手动，当前唯一入口）/ HEALTH_CONNECT / DEVICE（设备同步预留）。
     * 现有历史行都是小写 "manual"，解析时两种大小写都认，不强改老数据。
     */
    @ColumnInfo(defaultValue = "manual") val source: String = "manual",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_HEALTH_CONNECT = "HEALTH_CONNECT"
        const val SOURCE_DEVICE = "DEVICE"

        /** 解析来源（兼容老数据的大小写差异）。 */
        fun isManual(source: String): Boolean =
            source.equals(SOURCE_MANUAL, ignoreCase = true) || source.isBlank()
    }
}

/**
 * ============================================================
 * 训练侧设备同步预留（工单 4.2 第 10 项 P2）—— 为什么要「只留约定、不建索引」
 * ------------------------------------------------------------
 * 睡眠侧（SleepRecordEntity）已经是完整预留：sourceType 枚举 + externalId +
 * (sourceType, externalId) 唯一索引。训练侧照搬看似顺理成章，但有个坑：
 *   workout_session 里存量全是手动记录，externalId 若统一填空串 ""，
 *   UNIQUE(source, externalId) 会让所有老行互相冲突 → 升级后 Room 打不开库。
 * 真接设备时按这个顺序做，才不会踩雷：
 *   ① Room 16：ALTER TABLE workout_session ADD COLUMN externalId TEXT NOT NULL DEFAULT '';
 *   ② 建【部分唯一索引】CREATE UNIQUE INDEX ... WHERE externalId <> ''
 *      （只约束真的有外部 ID 的行，空串不参与；Room @Entity 表达不了 partial index，
 *        只能写在 migration 的 execSQL 里，别写进 indices = [...]）;
 *   ③ 同步入口先按 externalId 查重，存在就覆盖同一条，绝不重复导入。
 * 现在这一步只补齐 source 常量和这段约定，不动表结构、不加 Room 版本 —— 
 * 零迁移风险，也不会给真机升级埋雷。
 * ============================================================
 */

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

/**
 * ============================================================
 * 统一训练体系（Room 19 → 20）：有氧项目明细表
 * ------------------------------------------------------------
 * 一场 WorkoutSession 下可以有多个力量项目（workout_exercise）和多个有氧项目
 * （本表），两类明细都挂在同一个 sessionUuid 上 —— 一场综合训练只生成一条会话。
 *
 * 设计纪律（照四件套）：
 * 1. 全新表，MIGRATION_19_20 只有 CREATE TABLE + 索引，一行旧数据都不碰；
 * 2. 每个非主键列都写 @ColumnInfo(defaultValue=...)，与迁移 SQL 的 DEFAULT 逐字一致；
 * 3. 距离/体重快照可空：可空列在 Entity 上不写 defaultValue，迁移 SQL 也不写 DEFAULT
 *    （与 ExerciseEntity.barcode 等既有可空列同一处理方式，避免 schema 校验不一致）；
 * 4. 不建 (sessionUuid, exerciseUuid) 唯一索引 —— 一场训练允许同一动作出现多次
 *    （如热身跑 + 间歇跑），只建 sessionUuid 普通索引供按会话读取。
 *
 * 缓存字段语义：calculatedKcal/calculationMethod/calculationVersion 记录「录入时
 * 已确认」的项目级结果，统一结算时直接汇总，不重算历史；rawInputJson 存坡度、
 * 速度、功率、阻力、楼层、步频、心率、强度等低频/设备差异大的参数，容错解析，
 * 缺字段或未知字段都不能让整条历史打不开。
 * ============================================================
 */
@Entity(tableName = "workout_cardio_detail", indices = [Index("sessionUuid")])
data class WorkoutCardioDetail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val sessionUuid: String = "",
    /** 关联动作库 UUID；空串 = 自定义/非标准项目，仅用名称快照显示。 */
    @ColumnInfo(defaultValue = "") val exerciseUuid: String = "",
    @ColumnInfo(defaultValue = "") val exerciseNameSnapshot: String = "",
    @ColumnInfo(defaultValue = "0") val orderInSession: Int = 0,
    /** 项目实际时长（秒）。整场时长是 workout_session.durationMinutes，两者分开统计。 */
    @ColumnInfo(defaultValue = "0") val durationSeconds: Long = 0L,
    /** 距离（米）；可空 = 该项目不按距离记（如爬楼、跳绳）。 */
    val distanceMeters: Double? = null,
    /** 录入时已确认的项目级热量（估算），统一结算只汇总不重算。 */
    @ColumnInfo(defaultValue = "0.0") val calculatedKcal: Double = 0.0,
    /** 计算方式：user_direct / calculator / imported 等（见 WorkoutCalorieCalculator）。 */
    @ColumnInfo(defaultValue = "") val calculationMethod: String = "",
    /** 计算口径版本；录入时若没有可靠算法可留空，结算不虚构。 */
    @ColumnInfo(defaultValue = "") val calculationVersion: String = "",
    /** 计算时体重快照（kg）；可空 = 未记录。 */
    val weightSnapshot: Double? = null,
    /** 原始参数 JSON（坡度/速度/功率/阻力/楼层/步频/心率/强度等），容错解析。 */
    @ColumnInfo(defaultValue = "") val rawInputJson: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
)
