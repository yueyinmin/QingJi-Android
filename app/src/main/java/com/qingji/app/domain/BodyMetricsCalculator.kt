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

    // ---------- 体脂率：实测优先 / Deurenberg 估算回退（身体数据卡片工单 2026-11-21）----------

    /** 体脂展示来源。UI 只能展示这些状态，不自行判断数据来源。 */
    enum class BodyFatSource { MEASURED, ESTIMATED, NONE, UNSUPPORTED }

    /**
     * 体脂展示模型：值 + 来源 + 记录日期（实测时）。
     * value 为 null 时 UI 显示 "--" 或对应不可用文案。
     */
    data class BodyFatDisplay(
        val value: Double?,
        val source: BodyFatSource,
        val measuredDate: java.time.LocalDate? = null
    )

    /**
     * Deurenberg 成人 BMI 体脂率估算：1.2*BMI + 0.23*age - 10.8*S - 5.4。
     * - S：男=1 / 女=0；性别缺失返回 null（不默认按男性算）。
     * - 年龄按周岁；<18 不适用成人公式，返回 null。
     * - 输出必须在 3..70 且有限，否则返回 null（不展示误导值）。
     * 注意：BMI 必须是【未舍入】值，展示精度不得回灌计算。
     */
    fun estimateBodyFatDeurenberg(bmi: Double, ageYears: Int, gender: String?): Double? {
        val s = when (gender?.lowercase()) {
            "male" -> 1.0
            "female" -> 0.0
            else -> return null // 性别缺失/未知：不猜测
        }
        if (ageYears < 18) return null // 成人公式，未成年不适用
        if (!bmi.isFinite() || bmi <= 0.0 || ageYears <= 0) return null
        val raw = 1.2 * bmi + 0.23 * ageYears - 10.8 * s - 5.4
        if (!raw.isFinite() || raw !in BODY_FAT_MIN..BODY_FAT_MAX) return null
        return raw
    }

    /**
     * 实测优先的体脂展示组装：
     * 1) 有有效实测 → MEASURED；
     * 2) 无实测但资料齐全、成人 → ESTIMATED（绝不入库）；
     * 3) 资料齐全但未成年 → UNSUPPORTED；
     * 4) 其余 → NONE。
     */
    fun bodyFatDisplay(
        measured: BodyFatRecord?,
        bmi: Double,
        ageYears: Int?,
        gender: String?
    ): BodyFatDisplay {
        if (measured != null) return BodyFatDisplay(measured.value, BodyFatSource.MEASURED, measured.date)
        if (ageYears == null || gender.isNullOrBlank()) return BodyFatDisplay(null, BodyFatSource.NONE)
        if (ageYears < 18) return BodyFatDisplay(null, BodyFatSource.UNSUPPORTED)
        val estimated = estimateBodyFatDeurenberg(bmi, ageYears, gender)
        return if (estimated != null) BodyFatDisplay(estimated, BodyFatSource.ESTIMATED)
        else BodyFatDisplay(null, BodyFatSource.NONE)
    }
}
