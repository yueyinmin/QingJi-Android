package com.qingji.app.widget

import java.time.LocalDate

/**
 * 桌面小组件一次读取的展示数据（阶段 1）。
 *
 * 与旧版私有 WidgetData 的区别：新增 dataDate / zoneId / snapshotAt，
 * 让「最近一次成功快照」能判断是不是今天、是不是同一记录时区——
 * 跨日或改时区后旧快照不再冒充当天数据（第二轮审计 P1-02）。
 *
 * 默认值只用于「确实没有记录」的展示，不再作为读库失败的伪数据：
 * 读失败且无有效快照时由 [WidgetSnapshotRepo] 返回 hasSnapshot=false，UI 显示空态。
 */
data class WidgetData(
    val goalKcal: Int = 2000,
    val eatenKcal: Int = 0,
    /** 今日剩余（含运动补偿），与首页 remainingWithBonus 同口径。 */
    val remainingKcal: Int = 2000,
    val protein: Int = 0,
    val proteinGoal: Int = 0,
    val carb: Int = 0,
    val carbGoal: Int = 0,
    val fat: Int = 0,
    val fatGoal: Int = 0,
    val water: Int = 0,
    val waterGoal: Int = 3200,
    /** 读库失败：展示的是快照或空态，不是刚查到的真实数据。 */
    val stale: Boolean = false,
    /** 是否真的拿到过一次成功快照（false = 首次失败，UI 应显示空态而非伪数值）。 */
    val hasSnapshot: Boolean = false,
    val latestWeight: Double? = null,
    val unit: String = "kg",
    /** 4×4 专属指标：今日训练时长（分钟）/ 最近有效睡眠时长（分钟），来自 DailyHealthRepository 同一口径。 */
    val workoutMinutes: Int = 0,
    val sleepMinutes: Int = 0,
    /** 数据归属业务日期（AppClock 记录时区下的今天）。 */
    val dataDate: LocalDate? = null,
    /** 生成快照时的记录时区 ID（ZoneId.getId()）。 */
    val zoneId: String = "",
    /** 快照生成时间（epoch millis），用于区分「数据时间」与「刷新时间」。 */
    val snapshotAt: Long = 0L
)
