package com.qingji.app.domain

import kotlin.math.abs
import kotlin.math.round

/**
 * ============================================================
 * AI 识别结果的数值清洗规则（纯 Kotlin，不依赖 Android，可单测）
 * ------------------------------------------------------------
 * 模型偶尔会返回 0 克、负数、NaN、Infinity、每 100 克几千千卡、
 * 或者 confidence = 8。这些值一旦直接进 Room，首页热量和数字就会变成
 * 「NaN」「∞」，而且很难查是哪一步坏的。所以在解析层统一钳一次。
 *
 * 口径刻意和 NutritionMatch（仓库层二次校正）保持一致：
 * 克数 0.5～3000g、异常回落 100g；每 100g 热量上限 900 千卡。
 * 两处不打架，不会因为先后顺序不同算出两个数。
 *
 * ------------------------------------------------------------
 * 第二层：一致性校验（auditNutrition）
 * ------------------------------------------------------------
 * 只把单个数字钳进区间是不够的 —— 「100 克食物、蛋白 500 克」
 * 每个字段都在合法范围内，凑在一起却是物理上不可能的，
 * 而且钳完看起来还挺像真的。所以补一层交叉校验：
 *
 *   ① 三大营养素加起来不能超过食物本身的重量；
 *   ② 热量必须约等于 4×蛋白 + 4×碳水 + 9×脂肪（Atwater 系数）。
 *
 * 对不上的**不偷偷改数**（改了反而更难发现），而是返回一条人话说明，
 * 由调用方把这条的置信度压到 [SUSPECT_CONFIDENCE]，
 * 界面上就会亮红字「请重点核对」，advice 里再写清哪儿对不上。
 *
 * 容差为什么这么宽：
 *   热量按 4-9-4 反推只是估算值，真实食物里的膳食纤维、有机酸、糖醇、
 *   酒精都算不进去；小份量还有四舍五入误差。所以给 30% 相对容差 +
 *   20 kcal 绝对兜底，宁可漏报也别把正常数据全打成异常 ——
 *   每条都报警等于没报警。
 * ============================================================
 */
object AiResultRules {

    const val MIN_GRAMS = 0.5
    const val MAX_GRAMS = 3000.0
    const val DEFAULT_GRAMS = 100.0
    /** 纯食用油约 900 kcal/100g，超过就是模型算飞了 */
    const val MAX_KCAL_PER_100G = 900.0
    /** 单条三大营养素（克）的合理上限 */
    const val MAX_MACRO_G = 1000.0

    /** 被判定「数据前后对不上」的条目，置信度统一压到这个值（低于界面的 0.6 提示线）。 */
    const val SUSPECT_CONFIDENCE = 0.3
    /** 热量与三大营养素反推值的相对容差 */
    const val KCAL_TOLERANCE_RATIO = 0.30
    /** 热量比对的绝对容差：小份量食物几卡的误差不该报警 */
    const val KCAL_TOLERANCE_MIN = 20.0
    /** 三大营养素总重允许超过食物重量的比例（留 5% 给四舍五入） */
    const val MACRO_MASS_TOLERANCE = 1.05

    /** Atwater 系数：每克蛋白 / 碳水 / 脂肪提供的热量 */
    const val ATWATER_PROTEIN = 4.0
    const val ATWATER_CARB = 4.0
    const val ATWATER_FAT = 9.0

    /**
     * 份量：NaN / Infinity / 负数 / 0 → 回落 100g（不丢条目，界面仍可编辑）；
     * 离谱的大数 → 压到 3000g。
     */
    fun saneGrams(value: Double): Double = when {
        !isFinite(value) || value < MIN_GRAMS -> DEFAULT_GRAMS
        value > MAX_GRAMS -> MAX_GRAMS
        else -> value
    }

    /** 单条热量：按克数换算成每 100g，超上限就按上限重算，保留 1 位小数。 */
    fun saneKcal(value: Double, grams: Double): Double {
        if (!isFinite(value) || value < 0.0 || grams <= 0.0) return 0.0
        val per100 = value / grams * 100.0
        return if (per100 > MAX_KCAL_PER_100G) {
            round1(MAX_KCAL_PER_100G * grams / 100.0)
        } else {
            round1(value)
        }
    }

    /** 蛋白 / 碳水 / 脂肪：负数和非有限值归 0，离谱大数压到上限。 */
    fun saneMacro(value: Double): Double = when {
        !isFinite(value) || value < 0.0 -> 0.0
        value > MAX_MACRO_G -> MAX_MACRO_G
        else -> value
    }

    /** 置信度：钳到 0～1；非法值按 0，界面会当低置信度提示核对。 */
    fun saneConfidence(value: Double): Double =
        if (!isFinite(value)) 0.0 else value.coerceIn(0.0, 1.0)

    /**
     * 空结果是不是「合法追问」。
     * 只有模型一个条目都没给、并且明确说 need_clarify = true 才算；
     * 给了条目但清洗后全废（名字都空、数据全坏）属于识别失败，不能当成成功保存。
     */
    fun isLegalEmptyResult(rawItemCount: Int, needClarify: Boolean): Boolean =
        rawItemCount == 0 && needClarify

    // ---------- 第二层：一致性校验 ----------

    /** 一致性校验的结论。[ok] = false 时 [note] 是一句能直接给用户看的中文说明。 */
    data class NutritionAudit(val ok: Boolean, val note: String = "")

    /**
     * 一条食物的「克数 / 热量 / 三大营养素」互相对不对得上。
     *
     * 只在克数有效时才判断（克数无效的话，比例本身就无从谈起）。
     *
     * @return ok = false 表示需要人工核对，调用方应把置信度压到 [SUSPECT_CONFIDENCE]，
     *         并把 [NutritionAudit.note] 贴到提示语里，**不要**擅自改数字。
     */
    fun auditNutrition(
        grams: Double,
        kcal: Double,
        proteinG: Double,
        carbG: Double,
        fatG: Double
    ): NutritionAudit {
        if (!isFinite(grams) || grams < MIN_GRAMS) return NutritionAudit(true)

        val protein = safeMacro(proteinG)
        val carb = safeMacro(carbG)
        val fat = safeMacro(fatG)

        // ① 三大营养素加起来不能超过整份食物的重量。
        //    真实食物里还有水、纤维、灰分，所以「刚好等于重量」已经是极限（纯油、纯糖）。
        //    超过 5% 或者超过 1 克，就是模型把某一样放大了十倍。
        val macroSum = protein + carb + fat
        if (macroSum > grams * MACRO_MASS_TOLERANCE && macroSum - grams > 1.0) {
            return NutritionAudit(
                ok = false,
                note = "三大营养素加起来 ${g(macroSum)} g，比整份食物 ${g(grams)} g 还多"
            )
        }

        // ② 热量要能用三大营养素反推出来。
        //    模型压根没给营养素（全是 0）时无从比对，不算异常 —— 这很常见，不该报警。
        val estimated = protein * ATWATER_PROTEIN + carb * ATWATER_CARB + fat * ATWATER_FAT
        if (estimated <= 0.0) return NutritionAudit(true)

        val safeKcal = if (isFinite(kcal) && kcal >= 0.0) kcal else 0.0
        val diff = abs(safeKcal - estimated)
        val tolerance = maxOf(KCAL_TOLERANCE_MIN, KCAL_TOLERANCE_RATIO * maxOf(safeKcal, estimated))
        if (diff > tolerance) {
            // 有营养素却热量为 0，也走到这里：会提示「热量 0 kcal，按三大营养素算约 xx kcal」
            return NutritionAudit(
                ok = false,
                note = "热量 ${g(safeKcal)} kcal，按三大营养素算约 ${g(estimated)} kcal"
            )
        }

        return NutritionAudit(true)
    }

    /** 三大营养素按 4-9-4 反推的热量，仅用于提示文案和单测。 */
    fun estimatedKcal(proteinG: Double, carbG: Double, fatG: Double): Double =
        safeMacro(proteinG) * ATWATER_PROTEIN +
            safeMacro(carbG) * ATWATER_CARB +
            safeMacro(fatG) * ATWATER_FAT

    private fun safeMacro(v: Double): Double =
        if (!isFinite(v) || v < 0.0) 0.0 else minOf(v, MAX_MACRO_G)

    private fun isFinite(v: Double): Boolean = !v.isNaN() && !v.isInfinite()

    private fun round1(v: Double): Double = round(v * 10 + 1e-9) / 10.0

    /** 提示文案里的数字：四舍五入到整数，别给用户看 499.99999。 */
    private fun g(v: Double): String = round(v).toInt().toString()
}
