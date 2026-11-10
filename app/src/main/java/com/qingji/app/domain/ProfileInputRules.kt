package com.qingji.app.domain

import java.time.LocalDate
import java.time.Period

/**
 * ============================================================
 * 身体数据的输入范围与目标体重规则（纯 Kotlin，可单测）
 * ------------------------------------------------------------
 * 为什么单独抽出来：引导页和个人资料页以前各写一套校验 ——
 * 引导页只判「是不是数字」+ 年龄 10~100，个人资料页判 80~250 / 20~350。
 * 结果同一个身高 60cm，引导页放过去了、资料页又拦下来，用户莫名其妙。
 *
 * 现在两处都从这里取范围、取文案、取解析结果，标准只有一个。
 * 文案也统一放在这里，改一次两边一起变。
 *
 * 几条硬规矩：
 * - 空字符串不算有效数字（"".toDoubleOrNull() 本来就是 null）。
 * - NaN / Infinity / 负数不算有效数字（"NaN".toDoubleOrNull() 能解析出 NaN）。
 * - 维持模式允许不填目标体重，此时按当前体重算，不让用户填一个没意义的值。
 * - 减重必须比现在轻、增肌必须比现在重，否则目标本身就是矛盾的。
 * ============================================================
 */
object ProfileInputRules {

    /** 年龄：太小没有参考意义，太大公式也失真 */
    val AGE_RANGE = 10..100
    /** 身高（厘米） */
    val HEIGHT_RANGE_CM = 80.0..250.0
    /** 体重 / 目标体重（公斤） */
    val WEIGHT_RANGE_KG = 20.0..350.0
    /** 每日饮水目标（毫升），和个人资料页原来的 coerceIn(200, 8000) 保持一致 */
    val WATER_RANGE_ML = 200..8000

    /** 没填过饮水目标时的默认值（引导页不再问这一项） */
    const val DEFAULT_WATER_ML = 2000

    /** 非有限值（NaN / Infinity）会让后面所有计算变成 NaN，必须先挡住。 */
    private fun Double.isUsable(): Boolean = !isNaN() && !isInfinite()

    /** 模式只认这三个，传别的当减重处理，避免脏数据走到 when 的 else 分支。 */
    fun normalizeMode(mode: String): String = when (mode) {
        NutritionCalculator.MODE_GAIN, NutritionCalculator.MODE_KEEP -> mode
        else -> NutritionCalculator.MODE_LOSE
    }

    fun parseAge(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in AGE_RANGE }

    /** 解析一个小数：空串、"abc"、NaN、Infinity 一律返回 null。 */
    private fun parseDecimal(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf { it.isUsable() }

    fun parseHeight(text: String): Double? =
        parseDecimal(text)?.takeIf { it in HEIGHT_RANGE_CM }

    fun parseWeight(text: String): Double? =
        parseDecimal(text)?.takeIf { it in WEIGHT_RANGE_KG }

    /**
     * 目标体重。维持模式下留空是合法的，按当前体重处理；
     * 其余情况必须是一个落在合理范围内的数字。
     */
    fun parseTargetWeight(mode: String, currentWeightKg: Double?, text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && normalizeMode(mode) == NutritionCalculator.MODE_KEEP) {
            return currentWeightKg?.takeIf { it.isUsable() && it in WEIGHT_RANGE_KG }
        }
        return parseDecimal(trimmed)?.takeIf { it in WEIGHT_RANGE_KG }
    }

    // ---------- 校验：返回 null 表示通过，否则是给用户看的一句话 ----------

    fun ageError(text: String): String? =
        if (parseAge(text) == null) "年龄请填 ${AGE_RANGE.first}~${AGE_RANGE.last} 之间的整数" else null

    fun heightError(text: String): String? =
        if (parseHeight(text) == null) "身高请填 ${whole(HEIGHT_RANGE_CM.start)}~${whole(HEIGHT_RANGE_CM.endInclusive)} 之间的数字" else null

    fun weightError(text: String): String? =
        if (parseWeight(text) == null) "体重请填 ${whole(WEIGHT_RANGE_KG.start)}~${whole(WEIGHT_RANGE_KG.endInclusive)} 之间的数字" else null

    /**
     * 目标体重校验：先看数字本身，再看和当前体重的关系。
     * @param currentWeightKg 已经校验过、可以放心用的当前体重；传 null 就只做范围校验。
     */
    fun targetWeightError(mode: String, currentWeightKg: Double?, text: String): String? {
        val target = parseTargetWeight(mode, currentWeightKg, text)
        if (target == null) {
            // 维持模式留空是允许的，但当前体重必须有值才能顶上去
            if (text.trim().isEmpty() && normalizeMode(mode) == NutritionCalculator.MODE_KEEP) {
                return if (currentWeightKg == null) "先把当前体重填上" else null
            }
            return "目标体重请填 ${whole(WEIGHT_RANGE_KG.start)}~${whole(WEIGHT_RANGE_KG.endInclusive)} 之间的数字"
        }
        if (currentWeightKg == null) return null
        return when (normalizeMode(mode)) {
            NutritionCalculator.MODE_LOSE ->
                if (target >= currentWeightKg) "减重模式下，目标体重要比现在轻一点" else null
            NutritionCalculator.MODE_GAIN ->
                if (target <= currentWeightKg) "增肌模式下，目标体重要比现在重一点" else null
            else -> null
        }
    }

    /** 一次填完的四个身体数据（引导第 3 步用）。 */
    data class BodyInput(
        val ageText: String,
        val heightText: String,
        val weightText: String,
        val targetWeightText: String
    )

    /** 校验通过后的干净数值，后面直接拿去算目标，不用再解析一次。 */
    data class BodyValues(
        val age: Int,
        val heightCm: Double,
        val weightKg: Double,
        val targetWeightKg: Double
    )

    /**
     * 按顺序校验四个字段，返回第一条错误；全过返回 null。
     * 顺序固定，用户点「下一步」时看到的永远是最靠前那个问题，不会跳来跳去。
     */
    fun bodyError(mode: String, input: BodyInput): String? {
        ageError(input.ageText)?.let { return it }
        heightError(input.heightText)?.let { return it }
        weightError(input.weightText)?.let { return it }
        val weight = parseWeight(input.weightText)
        targetWeightError(mode, weight, input.targetWeightText)?.let { return it }
        return null
    }

    /** 全过才返回数值；有任何一项不合格返回 null（错误文案用 [bodyError] 取）。 */
    fun parseBody(mode: String, input: BodyInput): BodyValues? {
        if (bodyError(mode, input) != null) return null
        val age = parseAge(input.ageText) ?: return null
        val height = parseHeight(input.heightText) ?: return null
        val weight = parseWeight(input.weightText) ?: return null
        val target = parseTargetWeight(mode, weight, input.targetWeightText) ?: return null
        return BodyValues(age, height, weight, target)
    }

    /** 饮水目标：没填或填坏了都回落到默认 2000，绝不给出 0（那会让进度条除以零）。 */
    fun waterGoal(text: String?): Int =
        text?.trim()?.toIntOrNull()?.takeIf { it in WATER_RANGE_ML } ?: DEFAULT_WATER_ML

    /**
     * 引导页只问年龄，数据库里存的是生日 —— 这两个方向必须能原样转回来。
     *
     * 如果换算错了（比如少减一天），会出现「引导按 30 岁算目标，
     * 进个人资料页反算成 29 岁，一保存目标就变了」。
     * 所以换算只写这一处，并有单测守着往返一致。
     */
    fun birthdayForAge(age: Int, today: LocalDate = LocalDate.now()): LocalDate =
        today.minusYears(age.toLong())

    /** 个人资料页用这个把生日换算回年龄（和原来 Period.between(...).years 等价）。 */
    fun ageFromBirthday(birthday: LocalDate, today: LocalDate = LocalDate.now()): Int =
        Period.between(birthday, today).years

    /** 80.0 → "80"，避免文案里出现「80.0~250.0」这种毛刺。 */
    private fun whole(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
