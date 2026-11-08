package com.qingji.app.domain

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

/**
 * ============================================================
 * 用本地食物库校正 AI 的识别结果
 * ------------------------------------------------------------
 * 为什么要做这件事：
 * AI（尤其免费的小模型）最靠谱的是「看懂了吃了什么、大概多少克」，
 * 最不靠谱的是「这 150 克有多少千卡」——它经常现编一个数。
 * 而我们本地食物库里有 100 多条权威营养值。
 *
 * 所以策略改成：
 *   AI 只负责「食物名 + 克数」→ 本地库按名字匹配 → 用权威营养值重算热量。
 * 匹配不到才退回 AI 给的数字。
 *
 * 全是纯函数，方便单元测试。
 * ============================================================
 */

/** 每 100 克的营养值。 */
data class Per100g(
    val kcal: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double
)

/** 匹配结果：命中了本地库里的哪一条、像到什么程度。 */
data class NutritionHit(
    val dbName: String,
    val per100g: Per100g,
    val score: Int
)

object NutritionMatch {

    /** 归一化：去空格、统一括号、转小写，避免「米饭 」和「米饭」匹配不上。 */
    private fun normalize(raw: String): String =
        raw.trim()
            .replace(" ", "")
            .replace("\u3000", "")
            .replace("（", "(")
            .replace("）", ")")
            .replace("·", "")
            .lowercase()

    /** 最长公共子串长度（用来判断「番茄炒蛋」和「番茄炒鸡蛋」这类说法差异）。 */
    private fun commonLength(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var best = 0
        val dp = IntArray(b.length + 1)
        for (i in 1..a.length) {
            var prev = 0
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev + 1 else 0
                if (dp[j] > best) best = dp[j]
                prev = tmp
            }
        }
        return best
    }

    /**
     * 给一对名字打分，0～100。
     * 100 = 完全一样；包含关系给 60～88；部分重合按比例给分。
     */
    fun score(aiName: String, dbName: String): Int {
        val a = normalize(aiName)
        val b = normalize(dbName)
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return 100
        if (a.contains(b) || b.contains(a)) {
            // 「白米饭」和「米饭」：包含关系已经很像了，差几个字扣几分
            val diff = abs(a.length - b.length)
            return (88 - diff * 4).coerceAtLeast(60)
        }
        val common = commonLength(a, b)
        if (common < 2) return 0
        val ratio = common.toDouble() / min(a.length, b.length)
        return (ratio * 70).toInt()
    }

    /**
     * 从本地食物库里挑最像的一条。
     * @param threshold 低于这个分就当没匹配上，避免「西红柿」匹配到「西瓜」。
     */
    fun pick(
        aiName: String,
        candidates: List<Pair<String, Per100g>>,
        threshold: Int = 55
    ): NutritionHit? {
        var bestHit: NutritionHit? = null
        for ((name, per) in candidates) {
            val s = score(aiName, name)
            if (s >= threshold) {
                val current = bestHit
                if (current == null || s > current.score) {
                    bestHit = NutritionHit(name, per, s)
                }
            }
        }
        return bestHit
    }

    /** 按克数把「每 100 克」换算成这一份的实际营养，保留 1 位小数。 */
    fun compute(per100g: Per100g, grams: Double): Per100g {
        if (grams <= 0) return Per100g(0.0, 0.0, 0.0, 0.0)
        val ratio = grams / 100.0
        return Per100g(
            kcal = round1(per100g.kcal * ratio),
            protein = round1(per100g.protein * ratio),
            carb = round1(per100g.carb * ratio),
            fat = round1(per100g.fat * ratio)
        )
    }

    /**
     * 克数兜底：AI 偶尔会给 0、负数、或者 9999 这种离谱值。
     * 合理区间取 0.5～3000 克，异常时按 100 克（一个常规份量）算。
     */
    fun saneGrams(grams: Double): Double = when {
        grams.isNaN() || grams <= 0 -> 100.0
        grams > 3000 -> 3000.0
        grams < 0.5 -> 0.5
        else -> round(grams)
    }

    /**
     * 热量兜底：每 100 克最多 900 千卡（纯食用油差不多就是这个量）。
     * 超过说明模型算飞了，按上限压回来。
     */
    fun saneKcal(kcal: Double, grams: Double): Double {
        if (grams <= 0 || kcal.isNaN() || kcal < 0) return 0.0
        val per100 = kcal / grams * 100.0
        return if (per100 > 900) round1(900.0 * grams / 100.0) else round1(kcal)
    }

    /**
     * 保留 1 位小数。
     * 加 1e-9 是为了对付浮点误差：25.9 × 1.5 在计算机里是 38.849999999999994，
     * 直接四舍五入会变成 38.8，而人类算是 38.85 → 38.9。
     * 这个极小的补偿量不会影响任何正常数值。
     */
    private fun round1(v: Double): Double = round(v * 10 + 1e-9) / 10.0
}
