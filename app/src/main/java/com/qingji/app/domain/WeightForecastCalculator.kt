package com.qingji.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * ============================================================
 * 体重趋势预测（v3.4.0 · R3）—— 纯本地算，不调 AI、不联网
 * ------------------------------------------------------------
 * 取近 14 天有效体重，用「相邻记录日变化率的中位数」当速度：
 * 中位数天然抗单点称错 / 水肿这类极值，比直接线性回归稳，也不用引第三方库。
 *
 * 明确不给预测的情况（避免输出离谱数字）：
 *  · 没设目标体重 / 没记体重 / 已达到目标
 *  · 有效样本 < 5 天、相邻点算不出速度
 *  · 最近 7 天没新记录
 *  · 趋势是上升（与目标方向相反）
 *  · 预计超过 2 年
 * ============================================================
 */
object WeightForecastCalculator {

    const val WINDOW_DAYS = 14L
    const val MIN_SAMPLES = 5
    const val STALE_DAYS = 7L
    const val MAX_FORECAST_DAYS = 730L

    data class Point(val date: LocalDate, val weightKg: Double)

    data class Forecast(
        val show: Boolean,
        val message: String,
        val weeks: Int? = null,
        val estimatedDate: LocalDate? = null
    )

    fun forecast(
        points: List<Point>,
        currentWeight: Double,
        targetWeight: Double,
        today: LocalDate
    ): Forecast {
        if (targetWeight <= 0.0) return Forecast(false, "先设一个目标体重，才能估算达成时间")
        if (currentWeight <= 0.0) return Forecast(false, "先记一次体重，才能估算达成时间")
        if (targetWeight >= currentWeight) return Forecast(false, "已经达到目标体重啦")

        val from = today.minusDays(WINDOW_DAYS - 1)
        // 同一天可能有多条（唯一索引其实挡住了，这里仍做一次防御），取最后一条
        val clean = points
            .filter { it.date >= from && it.date <= today && it.weightKg > 0 }
            .sortedBy { it.date }
            .associateBy { it.date }
            .values
            .sortedBy { it.date }

        if (clean.size < MIN_SAMPLES) {
            return Forecast(false, "再记 ${MIN_SAMPLES - clean.size} 天体重，就能看到趋势预测")
        }
        val last = clean.last()
        if (ChronoUnit.DAYS.between(last.date, today) >= STALE_DAYS) {
            return Forecast(false, "最近一周没记体重，暂不预测")
        }

        val rates = mutableListOf<Double>()
        for (i in 1 until clean.size) {
            val span = ChronoUnit.DAYS.between(clean[i - 1].date, clean[i].date)
            if (span > 0) {
                rates.add((clean[i].weightKg - clean[i - 1].weightKg) / span.toDouble())
            }
        }
        if (rates.isEmpty()) return Forecast(false, "体重记录太集中，暂不预测")

        val delta = median(rates)
        // delta >= 0 表示没在下降；目标比当前轻时不该给「达成日期」
        if (delta >= -0.0001) return Forecast(false, "当前是上升趋势，暂不预测")

        val days = (targetWeight - currentWeight) / delta  // 负数 ÷ 负数 = 正数
        if (days > MAX_FORECAST_DAYS) return Forecast(false, "按当前速度暂难估算")

        // 浮点误差会让 44.00000000000006 被 ceil 成 45，先减一个极小量再取整
        val weeks = ceil(days / 7.0 - 1e-9).toInt().coerceAtLeast(1)
        val date = today.plusDays(ceil(days - 1e-9).toLong())
        return Forecast(
            show = true,
            message = "按最近速度，约 $weeks 周后到目标体重（预计 $date）",
            weeks = weeks,
            estimatedDate = date
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else sorted[mid]
    }
}
