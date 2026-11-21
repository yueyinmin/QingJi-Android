package com.qingji.app.domain

import com.qingji.app.data.local.entity.WeightLog

/**
 * 身体数据展示计算（身体数据仪表盘工单 P0）。
 *
 * 目标：把散落在 Composable 里的公式收敛到 domain 纯函数，并处理全部边界。
 * 注意：输入必须是【不受体重曲线时间范围过滤】的完整体重记录。
 */
object BodyMetricsCalculator {

    /** 体脂率有效范围，与录入校验 WeightScreen.kt:93-94 的 3..70 一致。 */
    const val BODY_FAT_MIN = 3.0
    const val BODY_FAT_MAX = 70.0

    /**
     * 从完整记录（date DESC）取最新一条有效体重。
     * 必须传完整集合：若传了曲线范围过滤后的集合，会污染身体数据。
     */
    fun latestWeightKg(allLogs: List<WeightLog>, fallbackStartWeight: Double = 0.0): Double =
        allLogs.firstOrNull { it.weightKg > 0.0 && it.weightKg.isFinite() }?.weightKg
            ?: fallbackStartWeight

    /**
     * 最近一次有效体脂率（可能与最新体重不在同一天，分别读取）。
     * 传 date DESC 集合时 firstOrNull 即最近一条。
     */
    data class BodyFatRecord(val value: Double, val date: java.time.LocalDate)

    fun latestBodyFat(allLogsDesc: List<WeightLog>): BodyFatRecord? =
        allLogsDesc.firstOrNull { log ->
            val f = log.bodyFat
            f != null && f.isFinite() && f in BODY_FAT_MIN..BODY_FAT_MAX
        }?.let { BodyFatRecord(it.bodyFat!!, it.date) }

    /**
     * 减重进度百分比。
     * - start == target → 0（不除零）
     * - 当前 == 起始 → 0
     * - 当前高于起始 → 负值（UI 自行处理展示）
     * - 当前达到/低于目标 → 100（不超过）
     */
    fun lossProgressPercent(startWeight: Double, currentWeight: Double, targetWeight: Double): Double {
        if (!startWeight.isFinite() || !currentWeight.isFinite() || !targetWeight.isFinite()) return 0.0
        val span = startWeight - targetWeight
        if (span == 0.0) return 0.0 // 起止相同，不除零
        val raw = (startWeight - currentWeight) / span * 100.0
        return if (targetWeight <= startWeight) raw.coerceAtMost(100.0) else raw
    }

    /** 距离目标还差多少 kg（非负值，已达成显示 0）。 */
    fun remainingToTarget(currentWeight: Double, targetWeight: Double): Double {
        if (!currentWeight.isFinite() || !targetWeight.isFinite()) return 0.0
        return (currentWeight - targetWeight).coerceAtLeast(0.0)
    }

    /** 目标是否已达成（当前 <= 目标）。 */
    fun isGoalReached(currentWeight: Double, targetWeight: Double): Boolean =
        currentWeight.isFinite() && targetWeight.isFinite() && currentWeight <= targetWeight
}
