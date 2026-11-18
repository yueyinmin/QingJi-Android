package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseLog
import java.time.LocalDate

/**
 * ============================================================
 * 有氧记录「日 / 周 / 月」范围汇总 —— 纯函数，不碰安卓、不碰数据库
 * ------------------------------------------------------------
 * 工单 4.4「增查改删 + 日周月统计」里的统计维度。
 * 只做三件事：按范围过滤、累加次数/时长/消耗、取范围文案。
 * 口径跟现有周卡一致：消耗直接用 ExerciseLog.kcal（确认页算好的值），
 * 不在这里重算 MET —— 改一次口径只改一处。
 * ============================================================
 */
object ExerciseRangeCalculator {

    /** 三个维度。字符串直接给界面当 key，不需要再映射。 */
    const val RANGE_DAY = "day"
    const val RANGE_WEEK = "week"
    const val RANGE_MONTH = "month"

    data class Summary(
        val count: Int,
        val minutes: Int,
        val kcal: Double,
        val label: String
    )

    /** 范围起点（含）。日=今天，周=近7天，月=近30天；未知范围退回近7天。 */
    fun rangeStart(range: String, today: LocalDate): LocalDate = when (range) {
        RANGE_DAY -> today
        RANGE_WEEK -> today.minusDays(6)   // 近 7 天，跟现有「本周」口径一致
        RANGE_MONTH -> today.minusDays(29) // 近 30 天
        else -> today.minusDays(6)
    }

    /** 按范围过滤，返回日期从旧到新的原始记录（调用方给近 30 天全量即可）。 */
    fun filter(
        logs: List<ExerciseLog>,
        range: String,
        today: LocalDate
    ): List<ExerciseLog> {
        val from = rangeStart(range, today)
        return logs.filter { it.date >= from && it.date <= today }
    }

    /**
     * @param logs 候选记录（调用方给足够大的范围，比如近 30 天）
     * @param range day / week / month
     * @param today 归属今天，跨天边界统一用它，不调系统时钟（方便单测）
     */
    fun summarize(
        logs: List<ExerciseLog>,
        range: String,
        today: LocalDate
    ): Summary {
        val picked = filter(logs, range, today)
        val label = when (range) {
            RANGE_DAY -> "今日"
            RANGE_WEEK -> "近 7 天"
            RANGE_MONTH -> "近 30 天"
            else -> "近 7 天"
        }
        return Summary(
            count = picked.size,
            minutes = picked.sumOf { it.minutes },
            kcal = picked.sumOf { it.kcal },
            label = label
        )
    }
}
