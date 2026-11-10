package com.qingji.app.domain

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

    private fun isFinite(v: Double): Boolean = !v.isNaN() && !v.isInfinite()

    private fun round1(v: Double): Double = round(v * 10 + 1e-9) / 10.0
}
