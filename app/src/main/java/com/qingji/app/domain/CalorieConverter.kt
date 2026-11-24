package com.qingji.app.domain

/**
 * 卡路里换算（2026-11-24）：食品标签上的「千焦 / 100g」+ 实际重量 → 总大卡。
 *
 * 换算口径（与界面公式说明一致）：
 *   1 大卡 (kcal) = 4.184 千焦 (kJ)
 *   总大卡 = (重量(g) × 千焦/100g) ÷ 100 ÷ 4.184
 */
object CalorieConverter {
    const val KJ_PER_KCAL = 4.184
    private const val GRAMS_PER_LABEL = 100.0

    /**
     * @param weightG 实际吃下的重量（克）
     * @param kjPer100g 标签上每 100g 的千焦值
     * @return 总大卡；任一输入 <=0 时返回 null（界面显示空状态）
     */
    fun kcal(weightG: Double, kjPer100g: Double): Double? {
        if (weightG <= 0.0 || kjPer100g <= 0.0) return null
        return weightG * kjPer100g / GRAMS_PER_LABEL / KJ_PER_KCAL
    }

    /** 展示用：保留 1 位小数，去掉无意义的 .0。 */
    fun format(kcal: Double): String {
        val rounded = Math.round(kcal * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else String.format("%.1f", rounded)
    }
}
