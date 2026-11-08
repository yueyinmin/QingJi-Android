package com.qingji.app.domain

/**
 * ============================================================
 * 运动记录换算（v3.3.0 运动模块）
 * ------------------------------------------------------------
 * 「确认记录」页有四种记录方式：时长 / 千卡 / 步数 / 公里，
 * 但数据库里的 ExerciseLog 只存 minutes + kcal 两个值。
 * 所以这里统一把四种输入换算成这两个值，界面不自己算，方便写单测。
 *
 * 主公式沿用 v2.8 运动库：千卡 = MET × 体重kg × 时长(小时)。
 * 步数 / 公里没有直接时长，用固定步频、配速折算成等效分钟，
 * 属于估算口径，不是运动科学结论，界面上也写了「约」。
 * ============================================================
 */
object ExerciseCalculator {

    const val MODE_MINUTES = "minutes"
    const val MODE_KCAL = "kcal"
    const val MODE_STEPS = "steps"
    const val MODE_KM = "km"

    /**
     * 自定义运动的标记：用 category 这一列存「自定义」来区分用户新建的运动。
     * 这样「自定义」页签、卡片标签都能做，**不用改 Room 表结构、不用升数据库版本**，
     * 也就避开了加列要写 Migration + 核对 schemas 的清库风险。
     * 代价是自定义运动不再细分有氧/力量/柔韧；以后要细分再走版本迁移。
     */
    const val CUSTOM_CATEGORY = "自定义"

    const val INTENSITY_LIGHT = "light"
    const val INTENSITY_MEDIUM = "medium"
    const val INTENSITY_HIGH = "high"

    /** 没直接填时长时的折算口径：约 100 步/分钟、10 分钟/公里（6km/h 快走）。 */
    const val STEPS_PER_MINUTE = 100.0
    const val MINUTES_PER_KM = 10.0

    private const val MIN_MET = 0.5
    private const val MIN_WEIGHT = 20.0
    private const val MAX_MINUTES = 600.0

    /** 换算结果：存库用的分钟数 + 估算千卡。 */
    data class Estimate(val minutes: Int, val kcal: Double)

    /** 记录方式 tab 的顺序与文案。 */
    fun modes(): List<Pair<String, String>> = listOf(
        MODE_MINUTES to "时长",
        MODE_KCAL to "千卡",
        MODE_STEPS to "步数",
        MODE_KM to "公里"
    )

    fun unit(mode: String): String = when (mode) {
        MODE_KCAL -> "千卡"
        MODE_STEPS -> "步"
        MODE_KM -> "公里"
        else -> "分钟"
    }

    /** 每种方式下面那排固定默认值（「自定义」由界面另外加）。 */
    fun presets(mode: String): List<Int> = when (mode) {
        MODE_KCAL -> listOf(100, 200, 300, 500)
        MODE_STEPS -> listOf(2000, 5000, 8000, 10000)
        MODE_KM -> listOf(1, 3, 5, 10)
        else -> listOf(10, 20, 30, 45, 60)
    }

    /** 刚进页面时默认选中的档位。 */
    fun defaultAmount(mode: String): Int = when (mode) {
        MODE_KCAL -> 300
        MODE_STEPS -> 5000
        MODE_KM -> 3
        else -> 30
    }

    /** 强度（可选）：不选 / 中 = 1.0。 */
    fun intensityFactor(intensity: String?): Double = when (intensity) {
        INTENSITY_LIGHT -> 0.85
        INTENSITY_HIGH -> 1.15
        else -> 1.0
    }

    /**
     * @param met 运动库里的 MET 值
     * @param weightKg 当前体重（kg）
     * @param mode 四种记录方式之一
     * @param amount 用户在对应方式里填的数（分钟 / 千卡 / 步 / 公里）
     * @param intensity 强度，可空
     */
    fun estimate(
        met: Double,
        weightKg: Double,
        mode: String,
        amount: Double,
        intensity: String? = null
    ): Estimate {
        val safeMet = met.coerceAtLeast(MIN_MET) * intensityFactor(intensity)
        val safeWeight = weightKg.coerceAtLeast(MIN_WEIGHT)
        val safeAmount = amount.coerceAtLeast(0.0)

        // 每分钟大致消耗多少千卡，千卡模式反推分钟时也要用
        val perMinuteKcal = safeMet * safeWeight / 60.0
        val rawMinutes = when (mode) {
            MODE_MINUTES -> safeAmount
            MODE_KCAL -> if (perMinuteKcal > 0) safeAmount / perMinuteKcal else 0.0
            MODE_STEPS -> safeAmount / STEPS_PER_MINUTE
            MODE_KM -> safeAmount * MINUTES_PER_KM
            else -> safeAmount
        }
        val minutes = kotlin.math.round(rawMinutes.coerceIn(0.0, MAX_MINUTES)).toInt()
        val kcal = when (mode) {
            // 千卡模式是用户直接指定消耗，不再用公式重算一遍
            MODE_KCAL -> safeAmount
            else -> perMinuteKcal * minutes
        }
        return Estimate(minutes, kcal)
    }
}
