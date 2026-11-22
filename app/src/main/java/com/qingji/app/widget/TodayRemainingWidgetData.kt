package com.qingji.app.widget

import java.time.LocalDate

/**
 * 阶段 C：今日剩余小组件的独立数据投影。
 *
 * 与身体数据投影完全隔离：独立 Key / schemaVersion / snapshotAt / stale / 失败状态。
 * 所有数值来自 domain 纯函数（ExerciseCompensation / WidgetProgress），
 * 本类只负责承载与序列化，不写任何业务公式。
 */
data class TodayRemainingWidgetData(
    val date: LocalDate? = null,
    val zoneId: String = "",
    val goalKcal: Int = 0,
    val eatenKcal: Int = 0,
    val exerciseKcal: Int = 0,
    val bonusKcal: Int = 0,
    val remainingKcal: Int = 0,
    val remainingFoodOnly: Int = 0,
    val goalMode: String = "lose",
    val proteinCurrent: Int = 0,
    val proteinGoal: Int = 0,
    val carbCurrent: Int = 0,
    val carbGoal: Int = 0,
    val fatCurrent: Int = 0,
    val fatGoal: Int = 0,
    /** V2.3 水杯：当日饮水 ml / 目标 ml / 完成度 0..1（与首页 WaterCup 同口径） */
    val waterMl: Int = 0,
    val waterGoalMl: Int = 0,
    val waterPercent: Float = 0f,
    val waterState: ItemState = ItemState.FAILED,
    val profileState: ItemState = ItemState.FAILED,
    val dietState: ItemState = ItemState.FAILED,
    val exerciseState: ItemState = ItemState.FAILED,
    val goalConfigured: Boolean = true,
    val snapshotAt: Long = 0L,
    val stale: Boolean = false,
    val hasSnapshot: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    /** 计算依赖齐全才允许展示剩余数值，否则 UI 显示 --。 */
    val canShowRemaining: Boolean get() =
        !stale && goalConfigured && profileState != ItemState.FAILED &&
            dietState != ItemState.FAILED && exerciseState != ItemState.FAILED

    companion object {
        const val SCHEMA_VERSION = 2 // V2.3 加水杯字段
    }
}
