package com.qingji.app.widget

import java.time.LocalDate

/**
 * 桌面小组件一次读取的展示数据。
 *
 * W-01/W-03/W-07：快照带结构版本、新鲜度时间与训练/睡眠【分项可用状态】——
 * 失败不能伪装成真实的 0 分钟，旧快照不能冒充今天的新数据。
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
    /** 整体读库失败：展示的是快照或空态，不是刚查到的真实数据。 */
    val stale: Boolean = false,
    /** 是否真的拿到过一次成功快照（false = 首次失败，UI 应显示空态而非伪数值）。 */
    val hasSnapshot: Boolean = false,
    val latestWeight: Double? = null,
    val unit: String = "kg",

    // ---- W-07：训练/睡眠分项可用状态（与数值分离） ----
    /** true = 查询成功（数值即使为 0 也是真实 0）；false = 查询失败，UI 必须显示 --。 */
    val workoutAvailable: Boolean = false,
    val sleepAvailable: Boolean = false,
    val workoutMinutes: Int = 0,
    val sleepMinutes: Int = 0,

    /** 数据归属业务日期（AppClock 记录时区下的今天）。 */
    val dataDate: LocalDate? = null,
    /** 生成快照时的记录时区 ID。 */
    val zoneId: String = "",
    /** 快照生成时间（epoch millis），用于新鲜度判断与更新时间展示。 */
    val snapshotAt: Long = 0L
) {
    companion object {
        /** W-01：快照结构版本。不兼容时直接作废重读，不做静默默认值迁移。 */
        const val SCHEMA_VERSION = 1
    }
}
