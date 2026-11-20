package com.qingji.app.domain

import com.qingji.app.data.local.entity.WorkoutCardioDetail

/**
 * ============================================================
 * 统一训练体系（Room 20）：整场热量的薄分发层
 * ------------------------------------------------------------
 * 职责只有一件：把一场训练里【力量】和【有氧】两类项目已有的热量结果
 * 汇总成一条整场总消耗，供统一账本幂等结算。不重写任何成熟算法：
 *   - 力量：调用方先用 SessionCalorieCalculator 结算好（按组数据分段），
 *     本类只接收结果值，不再算一遍；
 *   - 有氧：直接用录入流程已确认的项目热量（WorkoutCardioDetail.calculatedKcal），
 *     录入时由 ExerciseCalculator / 用户直填算出，这里绝不二次计算，避免双计。
 *
 * 为什么单独放一个薄层：工单 §5.4 要求「按项目类型分发、不重写成熟算法、
 * 缺参数不虚构」。把分发和汇总集中在这里，力量算法、有氧算法各自保持原样，
 * 未来加新项目类别只改这一处，结算链路（TrainingRepository）不用动。
 *
 * 红线：
 *   - 不自动重算历史：本类只处理「本次保存传入的项目结果」，不读库、不回溯旧数据；
 *   - 缺参数时返回 notComputable=true 且 kcal=0，不猜速度/坡度/功率；
 *   - 项目级热量留在各自明细，整场只产出一条汇总，供一条账本记录使用。
 * ============================================================
 */
object WorkoutCalorieCalculator {

    /** 统一分发口径版本。汇总规则变了就 +1，账本 calculationVersion 跟着写。 */
    const val VERSION = "workout-calorie-v1"

    /** 整场结算方式标记（写进账本 calculationMethod）。 */
    const val METHOD_STRENGTH_ONLY = "met_segmented"      // 与力量既有口径一致，纯力量场次可直接复用
    const val METHOD_MIXED = "workout_mixed"              // 力量+有氧混合
    const val METHOD_CARDIO_ONLY = "workout_cardio"       // 纯有氧场次

    /**
     * 整场热量汇总结果。
     * @param totalKcal 整场总消耗（力量 + 所有可计算有氧），账本只写这一条
     * @param strengthKcal 力量部分（已由 SessionCalorieCalculator 结算）
     * @param cardioKcal 有氧部分（录入已确认值之和）
     * @param cardioItems 有效有氧项目数（参与了汇总的）
     * @param notComputableItems 缺参数/负值的有氧项目数（被跳过、不虚构）
     * @param method 结算方式，见 METHOD_*
     * @param version 口径版本
     */
    data class Result(
        val totalKcal: Double,
        val strengthKcal: Double,
        val cardioKcal: Double,
        val cardioItems: Int,
        val notComputableItems: Int,
        val method: String,
        val version: String = VERSION
    ) {
        /** 是否存在被跳过的项目（UI 提示「N 项缺少参数未计入」，不阻断保存）。 */
        val hasSkipped: Boolean get() = notComputableItems > 0
    }

    /**
     * 汇总一场训练的热量。
     *
     * @param strengthKcal 力量结算值（<=0 视为无有效力量项目）
     * @param cardioDetails 本场所有有氧明细（录入已确认的热量在 calculatedKcal）
     *
     * 有氧项目计入条件：calculatedKcal > 0。缺参数导致没有有效热量时，
     * 该条计入 notComputableItems 并跳过——不补 0 冒充、不猜值。
     * 全部项目都无有效热量时 totalKcal=0，调用方据此在账本里标记「不可计算」，
     * 而不是写一条 0 千卡的假结算。
     */
    fun combine(
        strengthKcal: Double,
        cardioDetails: List<WorkoutCardioDetail>
    ): Result {
        val safeStrength = if (strengthKcal > 0.0) strengthKcal else 0.0
        var cardioSum = 0.0
        var computable = 0
        var skipped = 0
        cardioDetails.forEach { item ->
            if (item.calculatedKcal > 0.0 && item.calculatedKcal.isFinite()) {
                cardioSum += item.calculatedKcal
                computable++
            } else {
                skipped++
            }
        }
        val method = when {
            safeStrength > 0.0 && cardioSum > 0.0 -> METHOD_MIXED
            safeStrength > 0.0 -> METHOD_STRENGTH_ONLY
            cardioSum > 0.0 -> METHOD_CARDIO_ONLY
            else -> METHOD_CARDIO_ONLY // 全不可计算：方法名无实际意义，UI 按 totalKcal=0 提示
        }
        return Result(
            totalKcal = safeStrength + cardioSum,
            strengthKcal = safeStrength,
            cardioKcal = cardioSum,
            cardioItems = computable,
            notComputableItems = skipped,
            method = method
        )
    }

    /**
     * 判断一条有氧明细是否带有「录入已确认」的热量结果。
     * 供录入/展示层判断该项是否可计入整场汇总；与 combine 的计入条件保持单一口径。
     */
    fun isComputable(item: WorkoutCardioDetail): Boolean =
        item.calculatedKcal > 0.0 && item.calculatedKcal.isFinite()
}
