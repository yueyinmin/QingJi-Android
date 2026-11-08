package com.qingji.app.domain

import kotlin.math.max

/**
 * ============================================================
 * 热量与三大营养素目标的计算（需求文档 M1）
 * ------------------------------------------------------------
 * BMR（基础代谢）用 Mifflin-St Jeor 公式：
 *   男 = 10×体重kg + 6.25×身高cm − 5×年龄 + 5
 *   女 = 10×体重kg + 6.25×身高cm − 5×年龄 − 161
 * TDEE（一天总消耗） = BMR × 活动系数
 * 减重目标 = TDEE − 500 千卡（约每周减 0.5 kg）
 *
 * 安全下限：不低于 BMR×1.1，女性不低于 1200 千卡/天，
 * 男性按 1500 千卡/天兜底。这是自用工具，不是医疗建议。
 * ============================================================
 */
object NutritionCalculator {

    /** 算出来的每日目标，单位全部是整数，方便直接存进 user_profile 表。 */
    data class Goal(
        val bmr: Int,
        val tdee: Int,
        val kcal: Int,
        val proteinG: Int,
        val carbG: Int,
        val fatG: Int
    )

    /** 目标模式：减重 / 维持 / 增重。 */
    const val MODE_LOSE = "lose"
    const val MODE_KEEP = "keep"
    const val MODE_GAIN = "gain"

    private fun activityFactor(level: Int): Double = when (level) {
        1 -> 1.2        // 久坐
        2 -> 1.375      // 轻度活动
        3 -> 1.55       // 中度活动
        4 -> 1.725      // 高强度
        else -> 1.375
    }

    fun activityLabel(level: Int): String = when (level) {
        1 -> "久坐（几乎不运动）"
        2 -> "轻度（每周 1-3 次）"
        3 -> "中度（每周 3-5 次）"
        4 -> "高度（每周 6-7 次）"
        else -> "轻度（每周 1-3 次）"
    }

    fun compute(
        gender: String,
        age: Int,
        heightCm: Double,
        weightKg: Double,
        activityLevel: Int,
        mode: String
    ): Goal {
        // 防呆：用户没填完时给个合理值，别算出负数
        val safeWeight = if (weightKg > 0) weightKg else 70.0
        val safeHeight = if (heightCm > 0) heightCm else 170.0
        val safeAge = age.coerceIn(10, 100)

        val genderBonus = if (gender == "female") -161.0 else 5.0
        val bmr = 10 * safeWeight + 6.25 * safeHeight - 5 * safeAge + genderBonus
        val tdee = bmr * activityFactor(activityLevel)

        var kcal = when (mode) {
            MODE_LOSE -> tdee - 500.0
            MODE_GAIN -> tdee + 300.0
            else -> tdee
        }

        // 安全下限：吃得再少也不能低于基础代谢附近
        val floor = max(
            bmr * 1.1,
            if (gender == "female") 1200.0 else 1500.0
        )
        if (kcal < floor) kcal = floor

        // 宏量分配：蛋白 1.6g/kg，脂肪占 25% 热量，剩下给碳水
        val proteinG = (1.6 * safeWeight).toInt()
        val fatG = (kcal * 0.25 / 9).toInt()
        val carbG = ((kcal - proteinG * 4 - fatG * 9) / 4).toInt().coerceAtLeast(0)

        return Goal(
            bmr = bmr.toInt(),
            tdee = tdee.toInt(),
            kcal = kcal.toInt(),
            proteinG = proteinG,
            carbG = carbG,
            fatG = fatG
        )
    }

    /** BMI 用中国标准分级（不是 WHO 标准）。只返回文字，颜色由界面决定。 */
    fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "偏瘦"
        bmi < 24 -> "正常"
        bmi < 28 -> "超重"
        else -> "肥胖"
    }

    fun bmi(weightKg: Double, heightCm: Double): Double {
        if (heightCm <= 0) return 0.0
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }
}
