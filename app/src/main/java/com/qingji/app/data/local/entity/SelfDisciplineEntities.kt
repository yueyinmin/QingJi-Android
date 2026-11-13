package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 自律管理模块（SD-013）的 Room 实体
 * ------------------------------------------------------------
 * 五张 P0 表 + 两张 P1 预留表，全部走现有的 Converters（LocalDate ↔ "2026-11-07"）。
 * 不碰任何已有表，新增迁移 10 → 11 只做 CREATE，零 ALTER / DROP。
 * ============================================================
 */

/**
 * 表 1：用户目标配置。
 * 支持多个目标并存；首页只突出显示一个「主要目标」。
 */
@Entity(
    tableName = "self_discipline_goals",
    indices = [Index("isEnabled")]
)
data class SelfDisciplineGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 目标名称（自定义目标时由用户填；预置类型也可改名） */
    val title: String,
    /** 管理范围：porn=减少色情内容 / behavior=管理个人行为 / custom=自定义 */
    val goalType: String,
    /** 是否启用（暂停的目标 isEnabled=false，但历史数据保留） */
    val isEnabled: Boolean = true,
    /** 目标开始日期，也是第一个周期的起点 */
    val startDate: LocalDate,
    /** 目标周期天数；0 = 不设上限，一直持续 */
    val targetDays: Int = 0,
    /** 是否开启打卡提醒 */
    val reminderEnabled: Boolean = false,
    /** 提醒时间：当天第几分钟；-1 = 未设置 */
    val reminderMinute: Int = -1,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 2：目标周期记录。
 * 一次「坚持」从 startAt 到 endAt（进行中为 null）。
 * 只有用户确认目标偏离 / 手动结束才写 endAt；未打卡绝不自动结束周期。
 */
@Entity(
    tableName = "self_discipline_periods",
    indices = [Index("goalId"), Index("status")]
)
data class SelfDisciplinePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val startAt: LocalDate,
    /** 进行中 = null；结束后写实际结束日 */
    val endAt: LocalDate? = null,
    /** 结束原因：deviation=目标偏离 / completed=达成 / manual=手动 */
    val endReason: String? = null,
    /** active=进行中 / ended=已结束 */
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 3：每日打卡。
 * 同一目标同一天只保留一条（goalId + localDate 唯一），编辑即覆盖。
 * 非必要字段全部可空 / 默认空，支持快速打卡。
 */
@Entity(
    tableName = "self_discipline_checkins",
    indices = [Index(value = ["goalId", "localDate"], unique = true)]
)
data class SelfDisciplineCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val localDate: LocalDate,
    /** on_target=符合目标 / off_target=目标偏离 / skip=暂不记录 */
    val status: String,
    /** 情绪 token（见 SelfDisciplineContracts.MOODS），空 = 未填 */
    val mood: String = "",
    /** 冲动程度 1~5，0 = 未填 */
    val urgeLevel: Int = 0,
    /** 诱因，逗号分隔的 token（见 TRIGGERS） */
    val triggers: String = "",
    /** 应对方式，逗号分隔的 token（见 COPING_METHODS） */
    val copingMethods: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 表 4：冲动记录。
 * 一天可记多次；冲动记录 ≠ 目标偏离，不自动重置计时器。
 */
@Entity(
    tableName = "self_discipline_urge_logs",
    indices = [Index("goalId")]
)
data class SelfDisciplineUrgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** 发生时间（时间戳） */
    val occurredAt: Long,
    /** 冲动程度 1~5 */
    val intensity: Int = 0,
    val trigger: String = "",
    val copingMethod: String = "",
    /** 应对后的冲动程度 1~5，0 = 未填 */
    val intensityAfter: Int = 0,
    /** 是否缓解 */
    val resolved: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 5：模块设置（key-value）。
 * 存放：模块是否启用、主要目标 id、隐私显示开关等。
 */
@Entity(tableName = "self_discipline_settings")
data class SelfDisciplineSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

/**
 * 表 6（P1 预留）：里程碑达成记录。
 * 现在只建表，逻辑第二阶段再写，避免以后再迁移。
 */
@Entity(
    tableName = "self_discipline_milestones",
    indices = [Index("goalId")]
)
data class SelfDisciplineMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** 里程碑天数：1 / 3 / 7 / 14 / 30 / 60 / 90 */
    val milestoneDays: Int,
    /** 达成日期；未达成 = null */
    val achievedAt: LocalDate? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 表 7（P1 预留）：应对工具使用记录。
 */
@Entity(
    tableName = "self_discipline_interventions",
    indices = [Index("goalId")]
)
data class SelfDisciplineInterventionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    /** 工具类型：breath=呼吸 / exercise=短时运动 / distract=注意力转移 / mood=情绪记录 */
    val type: String,
    val occurredAt: Long,
    val beforeIntensity: Int = 0,
    val afterIntensity: Int = 0,
    /** 是否有效缓解；null = 未评价 */
    val effective: Boolean? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
