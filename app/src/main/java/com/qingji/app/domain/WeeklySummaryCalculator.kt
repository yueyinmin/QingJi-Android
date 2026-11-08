package com.qingji.app.domain

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt
import java.time.LocalDate

/**
 * ============================================================
 * 本周小结（v3.4.0 · R2）—— 纯本地算，不调 AI、不联网
 * ------------------------------------------------------------
 * 输入近 7 天的原始记录，输出「一句人话结论 + 一条建议」。
 * 只描述数据、不评价身体，文案里不出现诊断 / 治疗 / 就医这类医疗措辞。
 * 记录不足 3 天时明确说「再记几天」，绝不给一个用 0 拼出来的假结论。
 * ============================================================
 */
object WeeklySummaryCalculator {

    const val MIN_DAYS = 3

    data class Day(
        val date: LocalDate,
        val kcal: Double = 0.0,
        val exerciseKcal: Double = 0.0,
        val waterMl: Int = 0,
        val hasLog: Boolean = false
    )

    data class Summary(
        val ready: Boolean,
        val recordedDays: Int,
        val avgKcal: Int,
        val avgGap: Int,
        val weightChangeKg: Double,
        val waterDays: Int,
        val exerciseDays: Int,
        val conclusion: String,
        val advice: String
    )

    fun summarize(
        days: List<Day>,
        dailyGoal: Int,
        waterGoal: Int,
        startWeight: Double?,
        endWeight: Double?
    ): Summary {
        val recorded = days.filter { it.hasLog }
        val n = recorded.size
        val waterDays = if (waterGoal > 0) days.count { it.waterMl >= waterGoal } else 0
        val exerciseDays = days.count { it.exerciseKcal > 0 }

        if (n < MIN_DAYS) {
            return Summary(
                ready = false,
                recordedDays = n,
                avgKcal = 0,
                avgGap = 0,
                weightChangeKg = 0.0,
                waterDays = 0,
                exerciseDays = 0,
                conclusion = "再记 ${MIN_DAYS - n} 天就能生成本周小结",
                advice = ""
            )
        }

        val avgKcal = (recorded.sumOf { it.kcal } / n).roundToInt()
        val avgGap = (recorded.sumOf { dailyGoal + it.exerciseKcal - it.kcal } / n).roundToInt()
        val change = if (
            startWeight != null && endWeight != null && startWeight > 0 && endWeight > 0
        ) endWeight - startWeight else 0.0

        val weightText = when {
            abs(change) < 0.05 -> "体重基本持平"
            change < 0 -> "体重降了 ${oneDecimal(-change)}kg"
            else -> "体重涨了 ${oneDecimal(change)}kg"
        }
        val gapText = if (avgGap >= 0) "平均每天少 $avgGap 千卡" else "平均每天多 ${-avgGap} 千卡"
        val advice = when {
            avgGap >= 300 -> "节奏比较稳，保持现在的记录习惯就好。"
            avgGap >= 0 -> "缺口不大，可以按自己的节奏慢慢来。"
            else -> "这周摄入略高于预算，先把每餐记全更容易看出原因。"
        }
        return Summary(
            ready = true,
            recordedDays = n,
            avgKcal = avgKcal,
            avgGap = avgGap,
            weightChangeKg = change,
            waterDays = waterDays,
            exerciseDays = exerciseDays,
            conclusion = "这周记了 $n 天，$gapText，$weightText",
            advice = advice
        )
    }

    /** 自己保留一位小数，避开 String.format 在不同系统语言下把小数点写成逗号。 */
    private fun oneDecimal(v: Double): String = (round(v * 10.0) / 10.0).toString()
}
