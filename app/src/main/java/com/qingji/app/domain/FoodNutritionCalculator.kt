package com.qingji.app.domain

import kotlin.math.round
import kotlin.math.roundToInt

/**
 * ============================================================
 * 统一营养折算工具（v1.5 食物创建体系重构 · 阶段二）
 * ------------------------------------------------------------
 * 背景：重构前「按每100g折算这一份实际摄入」的公式在每个页面各写一遍
 *       （FoodLibraryViewModel / SearchFoodViewModel / BarcodeViewModel /
 *        MealDetailViewModel 等 5 处），而且全部写死 `grams / 100.0`。
 *       一旦引入「每份 / 每100ml」基准，各页面必然算错。
 *
 * 本工具把所有折算收敛到一处，并统一处理：
 *   ① 三种计量基准 PER_100G / PER_100ML / PER_SERVING
 *   ② 热量 + 三大营养素用同一个倍率，绝不出现「热量算了、三宏量漏了」
 *   ③ 「未收录(null)」与「真测得 0」分开——调用方决定 null 怎么显示
 *   ④ 4-9-4 热量/宏量一致性校验，只提示、绝不覆盖用户填的数
 *
 * 设计原则：纯函数、无 Android 依赖、无状态，便于单元测试。
 * ============================================================
 */
object FoodNutritionCalculator {

    /** 每 100 克。字段值存的就是每 100g 的量。 */
    const val PER_100G = "PER_100G"
    /** 每 100 毫升。字段值存的是每 100ml 的量。 */
    const val PER_100ML = "PER_100ML"
    /** 每份。字段值存的是每份的量，每份克数看 servingGrams。 */
    const val PER_SERVING = "PER_SERVING"

    val ALL_BASIS = listOf(PER_100G, PER_100ML, PER_SERVING)

    /** 界面上给基准显示的中文标签。 */
    fun basisLabel(basis: String): String = when (basis) {
        PER_100ML -> "每100毫升"
        PER_SERVING -> "每份"
        else -> "每100克"
    }

    /** 解析历史/异常数据里的基准字符串，非法值一律回落到 PER_100G。 */
    fun normalizeBasis(basis: String?): String =
        if (basis in ALL_BASIS) basis!! else PER_100G

    /**
     * 一份实际吃了多少「基准单位」——折算倍率的分母换算。
     *
     * 例：蛋白粉营养是每100g，实际吃 40g → 倍率 40/100 = 0.4
     *     某食物营养是每份、每份 30g，实际吃 60g → 倍率 60/30 = 2.0
     *
     * @param basis       计量基准
     * @param servingGrams 每份克数（PER_SERVING 时必填且 >0；其他基准忽略）
     * @param grams       实际食用克数/毫升数
     * @return 折算倍率；参数非法时返回 0（这一份不计入营养）
     */
    fun ratio(basis: String, servingGrams: Double, grams: Double): Double {
        if (!grams.isFinite() || grams <= 0.0) return 0.0
        val perBasis = when (normalizeBasis(basis)) {
            PER_SERVING -> servingGrams.takeIf { it.isFinite() && it > 0.0 } ?: return 0.0
            else -> 100.0
        }
        return grams / perBasis
    }

    /** 一份食物的实际营养结果。null 表示该营养素「未收录」，不要当成 0。 */
    data class Consumed(
        val kcal: Double,
        val proteinG: Double,
        val carbG: Double,
        val fatG: Double,
        val fiberG: Double? = null,
        val sodiumMg: Double? = null,
        val calciumMg: Double? = null,
        val sugarG: Double? = null
    )

    /**
     * 统一折算：把「每基准」的营养值折算成「实际吃的这一份」。
     * 热量 + 三宏量用同一个 ratio，微量营养素保持 null 语义（未收录仍是 null）。
     *
     * 热量与三大营养素在实体里是非空 Double，null 语义无法表达，
     * 所以这里不做「未知当 0」的兜底——是否可信由 [consistencyNote] 与录入端校验负责。
     */
    fun compute(
        kcalPerBasis: Double,
        proteinPerBasis: Double,
        carbPerBasis: Double,
        fatPerBasis: Double,
        basis: String,
        servingGrams: Double,
        grams: Double,
        fiberPerBasis: Double? = null,
        sodiumPerBasis: Double? = null,
        calciumPerBasis: Double? = null,
        sugarPerBasis: Double? = null
    ): Consumed {
        val r = ratio(basis, servingGrams, grams)
        return Consumed(
            kcal = safeMul(kcalPerBasis, r),
            proteinG = safeMul(proteinPerBasis, r),
            carbG = safeMul(carbPerBasis, r),
            fatG = safeMul(fatPerBasis, r),
            fiberG = fiberPerBasis?.let { safeMul(it, r) },
            sodiumMg = sodiumPerBasis?.let { safeMul(it, r) },
            calciumMg = calciumPerBasis?.let { safeMul(it, r) },
            sugarG = sugarPerBasis?.let { safeMul(it, r) }
        )
    }

    /** 乘法：非有限值按 0，保留 1 位小数。 */
    private fun safeMul(value: Double, ratio: Double): Double {
        if (!value.isFinite() || !ratio.isFinite()) return 0.0
        return round1(value * ratio)
    }

    /**
     * 4-9-4 一致性校验：用 Atwater 公式由三大营养素反推热量，
     * 与填写的热量比较，偏差过大时返回一句中文提示。
     *
     * 关键：**只提示、不改写**。用户填的数据永远优先。
     * 容差沿用 AiResultRules 的口径（绝对 20kcal + 相对 30%）。
     *
     * @return null = 一致（或数据不全，无从校验）；非 null = 提示文案
     */
    fun consistencyNote(
        kcal: Double,
        protein: Double,
        carb: Double,
        fat: Double
    ): String? {
        // 三宏量全 0 = 没填，无从校验；热量也必须在合理区间
        if (!kcal.isFinite() || kcal <= 0.0) return null
        if (protein + carb + fat <= 0.0) return null
        val estimated = protein * AiResultRules.ATWATER_PROTEIN +
            carb * AiResultRules.ATWATER_CARB +
            fat * AiResultRules.ATWATER_FAT
        if (estimated <= 0.0) return null
        val tolerance = maxOf(20.0, 0.30 * maxOf(kcal, estimated))
        return if (kotlin.math.abs(kcal - estimated) > tolerance) {
            "热量与三大营养素不太匹配（按蛋白/碳水/脂肪估算约 ${estimated.roundToInt()} 千卡），请核对；已保留你填写的数值"
        } else {
            null
        }
    }

    /**
     * 反向折算：从「这一餐实际吃到的营养 + 克数」还原「每基准」的营养值。
     * 用餐详情「存进我的食物库」用（log 是快照，存回去要按每100g表示）。
     * 与 [compute] 互为逆运算，同样热量+三宏量用同一倍率。
     * 克数非法时返回 null（调用方跳过这条）。
     */
    fun toPerBasis(
        consumedKcal: Double,
        consumedProtein: Double,
        consumedCarb: Double,
        consumedFat: Double,
        grams: Double,
        basis: String = PER_100G,
        servingGrams: Double = 100.0
    ): PerBasis? {
        if (!grams.isFinite() || grams <= 0.0) return null
        val perBasis = when (normalizeBasis(basis)) {
            PER_SERVING -> servingGrams.takeIf { it.isFinite() && it > 0.0 } ?: return null
            else -> 100.0
        }
        val r = perBasis / grams
        return PerBasis(
            kcal = round1(consumedKcal * r),
            protein = round1(consumedProtein * r),
            carb = round1(consumedCarb * r),
            fat = round1(consumedFat * r)
        )
    }

    data class PerBasis(val kcal: Double, val protein: Double, val carb: Double, val fat: Double)

    /** 保留 1 位小数，带浮点误差补偿（与 NutritionMatch 同一套口径）。 */
    private fun round1(v: Double): Double = round(v * 10 + 1e-9) / 10.0
}
