package com.qingji.app.domain

/**
 * ============================================================
 * 运动补偿口径（健康体系 V1.0 · P0 统一下沉）
 * ------------------------------------------------------------
 * 历史问题：补偿计算原来写在首页卡片（HomeCards）里，周小结却按全额计入缺口，
 * 两处口径不一致，不同页面会算出不同的「缺口」。
 * 现在统一到这里：额度/缺口里的运动加成 = 消耗 × 补偿比例（开关关闭时为 0）。
 *
 * 规矩（工单 3.7 原文要求）：
 * - 用户关闭运动补偿时，运动消耗统计照常保留，只是不加进饮食额度；
 * - 补偿比例修改不影响历史运动原始数据（这里只做展示层折算，不回写记录）。
 * ============================================================
 */
object ExerciseCompensation {

    /**
     * 运动消耗中可加进饮食额度/缺口的部分（千卡，取整）。
     * @param exerciseKcal 当日运动总消耗（原始值，不动）
     * @param enabled 设置里的「运动补偿」开关
     * @param pct 补偿比例 0-100（默认 50：设备普遍高估消耗，全额加回会让人越练越多吃）
     */
    fun bonusKcal(exerciseKcal: Double, enabled: Boolean, pct: Int): Int {
        if (!enabled || pct <= 0 || exerciseKcal <= 0.0) return 0
        return (exerciseKcal * pct / 100.0).toInt()
    }

    /**
     * 首页「今日剩余」主数字（2026-11-18：运动补偿接入主数字）。
     *
     * 补偿开启时，运动消耗按比例加回额度，主数字直接变成
     * 「预算 + 运动加成 − 已吃」；关闭时退回原来的「预算 − 已吃」。
     * 这样主人做完训练，抬头就能看到剩余额度变多，不用再去看小字。
     *
     * @return 今日剩余（可能为负，负的说明反超了预算，UI 会标红）
     */
    fun remainingWithBonus(
        goalKcal: Int,
        eatenKcal: Int,
        exerciseKcal: Double,
        enabled: Boolean,
        pct: Int
    ): Int = goalKcal - eatenKcal + bonusKcal(exerciseKcal, enabled, pct)

    /**
     * 卡片下方小字「纯饮食剩余」：永远不含运动补偿，用来跟主数字对照，
     * 让主人看清这多出来的额度是运动挣的，不是凭空多出来的预算。
     */
    fun remainingFoodOnly(goalKcal: Int, eatenKcal: Int): Int = goalKcal - eatenKcal
}
