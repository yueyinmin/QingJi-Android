package com.qingji.app.widget

import com.qingji.app.domain.BodyMetricsCalculator.GoalDirection
import java.time.LocalDate

/**
 * 阶段 D：身体数据小组件独立数据投影。
 * 与今日剩余投影完全隔离（独立 Key / schema / 时间 / stale / 失败态）。
 * 所有计算来自 domain 纯函数，本类不写任何 BMI/体脂/进度公式。
 */
data class BodyDataWidgetData(
    val latestWeight: Double = 0.0,
    val unit: String = "kg",
    val heightCm: Double = 0.0,
    val targetWeight: Double = 0.0,
    val startWeight: Double = 0.0,
    val bmi: Double = 0.0,
    val bmiCategory: String = "",
    val bodyFat: Double? = null,
    val bodyFatSource: String = "none",
    val lossProgress: Float = 0f,
    val remainToTarget: Double = 0.0,
    val goalReached: Boolean = false,
    val goalDirection: GoalDirection = GoalDirection.LOSE,
    val goalComputable: Boolean = false,
    val latestRecordDate: LocalDate? = null,
    val recordCount: Int = 0,
    val bodyState: ItemState = ItemState.FAILED,
    val date: LocalDate? = null,
    val zoneId: String = "",
    val snapshotAt: Long = 0L,
    val stale: Boolean = false,
    val hasSnapshot: Boolean = false,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    val canShowBody: Boolean get() = !stale && bodyState != ItemState.FAILED && hasSnapshot

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
